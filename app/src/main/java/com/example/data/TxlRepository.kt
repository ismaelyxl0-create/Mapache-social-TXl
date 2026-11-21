package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CommunityEntity
import com.example.data.local.CustomFrameEntity
import com.example.data.local.DriftingBottleEntity
import com.example.data.local.MembershipEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PostCommentEntity
import com.example.data.local.PostEntity
import com.example.data.local.PreferencesStore
import com.example.data.local.RoomEventEntity
import com.example.data.local.SeedData
import com.example.data.local.TxlDatabase
import com.example.data.local.UserEntity
import com.example.data.local.VoiceParticipantEntity
import com.example.model.AppNotification
import com.example.model.AppRank
import com.example.model.AppSettings
import com.example.model.AvatarFrame
import com.example.model.ChatMessage
import com.example.model.Community
import com.example.model.CommunityPost
import com.example.model.CommunityRole
import com.example.model.CustomFrame
import com.example.model.CustomFrameStyle
import com.example.model.DriftingBottle
import com.example.model.NotificationCategory
import com.example.model.PostComment
import com.example.model.RoomEventLog
import com.example.model.UserProfile
import com.example.model.VoiceParticipant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class TxlRepository(
    private val context: Context,
    private val db: TxlDatabase,
    private val prefs: PreferencesStore
) {
    companion object {
        const val PRIMARY_OWNER_EMAIL = "abukeje26@gmail.com"
    }

    val appContext: Context get() = context
    val currentUserId: Flow<String?> = prefs.currentUserId
    val settings: Flow<AppSettings> = prefs.settings

    suspend fun initializeSeedData() {
        SeedData.ensureSeeded(db)
        db.driftingBottleDao().purgeExpired(System.currentTimeMillis())
        db.userDao().findByEmailOrUsername(PRIMARY_OWNER_EMAIL)?.let { owner ->
            if (!owner.isDeveloper) {
                db.userDao().updateIsDeveloper(owner.id, true)
            }
        }
    }

    fun observeAllUsersMap(): Flow<Map<String, UserProfile>> =
        db.userDao().observeAllUsers().map { list ->
            list.associate { it.id to it.toDomain() }
        }

    fun observeAllUsersList(): Flow<List<UserProfile>> =
        db.userDao().observeAllUsers().map { list ->
            list.map { it.toDomain() }
        }

    fun observeHasAnyUser(): Flow<Boolean> =
        db.userDao().observeAllUsers().map { it.isNotEmpty() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentUser(): Flow<UserProfile?> =
        currentUserId.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null)
            else db.userDao().observeUserById(uid).map { it?.toDomain() }
        }

    fun observeCommunities(userId: String): Flow<List<Community>> =
        combine(
            db.communityDao().observeCommunities(),
            db.membershipDao().observeMembershipsForUser(userId)
        ) { communities, memberships ->
            val roleByCommunity = memberships.associateBy { it.communityId }
            communities.map { entity ->
                val membership = roleByCommunity[entity.id]
                entity.toDomain(
                    myRole = membership?.let { CommunityRole.fromRaw(it.role) },
                    isJoined = membership != null
                )
            }
        }

    fun observeCommunity(communityId: String, userId: String): Flow<Community?> =
        combine(
            db.communityDao().observeCommunity(communityId),
            db.membershipDao().observeMembershipsForUser(userId)
        ) { entity, memberships ->
            val membership = memberships.firstOrNull { it.communityId == communityId }
            entity?.toDomain(
                myRole = membership?.let { CommunityRole.fromRaw(it.role) },
                isJoined = membership != null
            )
        }

    fun observeVoiceParticipants(communityId: String): Flow<List<VoiceParticipant>> =
        combine(
            db.voiceRoomDao().observeParticipants(communityId),
            observeAllUsersMap(),
            db.membershipDao().observeMembershipsForCommunity(communityId)
        ) { participants, users, memberships ->
            val roleMap = memberships.associate { it.userId to CommunityRole.fromRaw(it.role) }
            participants.mapNotNull { vp ->
                val user = users[vp.userId] ?: return@mapNotNull null
                VoiceParticipant(
                    id = vp.id,
                    communityId = vp.communityId,
                    user = user,
                    role = roleMap[vp.userId] ?: CommunityRole.MEMBER,
                    isMuted = vp.isMuted,
                    isSpeaking = vp.isSpeaking,
                    isHandRaised = vp.isHandRaised
                )
            }.sortedByDescending { it.role.priority }
        }

    fun observeRoomEvents(communityId: String): Flow<List<RoomEventLog>> =
        db.voiceRoomDao().observeRoomEvents(communityId).map { list ->
            list.map {
                RoomEventLog(
                    id = it.id,
                    communityId = it.communityId,
                    actorName = it.actorName,
                    description = it.description,
                    timestampMillis = it.timestampMillis
                )
            }
        }

    fun observeMessages(communityId: String, channelType: String): Flow<List<ChatMessage>> =
        combine(
            db.chatDao().observeMessages(communityId, channelType),
            observeAllUsersMap(),
            db.membershipDao().observeMembershipsForCommunity(communityId)
        ) { messages, users, memberships ->
            val roleMap = memberships.associate { it.userId to CommunityRole.fromRaw(it.role) }
            messages.map { msg ->
                val sender = users[msg.senderId] ?: UserProfile(
                    id = msg.senderId,
                    username = "Usuario",
                    email = "",
                    bio = "",
                    customStatus = "",
                    avatarFrame = AvatarFrame.NONE,
                    avatarUri = null,
                    presetAvatarKey = "anime",
                    communitiesCount = 0,
                    followersCount = 0,
                    postsCount = 0
                )
                ChatMessage(
                    id = msg.id,
                    communityId = msg.communityId,
                    channelType = msg.channelType,
                    sender = sender,
                    senderRole = roleMap[msg.senderId] ?: CommunityRole.MEMBER,
                    content = msg.content,
                    replyToSnippet = msg.replyToSnippet,
                    reactionCount = msg.reactionCount,
                    timestampMillis = msg.timestampMillis,
                    audioUri = msg.audioUri,
                    audioDurationMs = msg.audioDurationMs
                )
            }
        }

    fun observeDriftingBottles(): Flow<List<DriftingBottle>> =
        db.driftingBottleDao().observeActiveBottles(System.currentTimeMillis()).map { list ->
            list.map { it.toDomain() }
        }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        db.notificationDao().observeNotifications(userId).map { list ->
            list.map {
                AppNotification(
                    id = it.id,
                    userId = it.userId,
                    actorName = it.actorName,
                    actorAvatarKey = it.actorAvatarKey,
                    actorAvatarUri = it.actorAvatarUri,
                    actorFrame = AvatarFrame.fromId(it.actorFrameId),
                    title = it.title,
                    subtitle = it.subtitle,
                    category = NotificationCategory.fromRaw(it.category),
                    targetCommunityId = it.targetCommunityId,
                    isRead = it.isRead,
                    timestampMillis = it.timestampMillis
                )
            }
        }

    fun observeUnreadNotificationsCount(userId: String): Flow<Int> =
        db.notificationDao().observeUnreadCount(userId)

    fun observeCustomFrames(): Flow<List<CustomFrame>> =
        db.customFrameDao().observeAllFrames().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getCustomFrameById(frameId: String): CustomFrame? =
        db.customFrameDao().getFrameById(frameId)?.toDomain()

    fun observePosts(): Flow<List<CommunityPost>> =
        combine(
            db.postDao().observeAllPosts(),
            observeAllUsersMap(),
            db.communityDao().observeCommunities(),
            db.postDao().observeAllComments()
        ) { posts, users, communities, comments ->
            val commMap = communities.associateBy { it.id }
            val commentCountByPost = comments.groupingBy { it.postId }.eachCount()
            posts.mapNotNull { post ->
                val author = users[post.authorId] ?: return@mapNotNull null
                CommunityPost(
                    id = post.id,
                    author = author,
                    communityId = post.communityId,
                    communityName = post.communityId?.let { commMap[it]?.name },
                    content = post.content,
                    imageUri = post.imageUri,
                    likesCount = post.likesCount,
                    commentsCount = commentCountByPost[post.id] ?: 0,
                    createdAtMillis = post.createdAtMillis
                )
            }
        }

    fun observePostComments(postId: String): Flow<List<PostComment>> =
        combine(
            db.postDao().observeCommentsForPost(postId),
            observeAllUsersMap()
        ) { comments, users ->
            comments.mapNotNull { c ->
                val author = users[c.authorId] ?: return@mapNotNull null
                PostComment(
                    id = c.id,
                    postId = c.postId,
                    author = author,
                    content = c.content,
                    createdAtMillis = c.createdAtMillis
                )
            }
        }

    suspend fun getPostById(postId: String): PostEntity? =
        db.postDao().getPostById(postId)

    // --- AUTH ---

    suspend fun login(identifier: String, password: String): Result<UserProfile> {
        return loginWithoutSessionChange(identifier, password).onSuccess {
            prefs.setCurrentUserId(it.id)
        }
    }

    suspend fun loginWithoutSessionChange(identifier: String, password: String): Result<UserProfile> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Ingresa tu correo/usuario y contraseña."))
        }
        val user = db.userDao().findByEmailOrUsername(cleanId)
            ?: return Result.failure(IllegalArgumentException("No encontramos una cuenta con ese correo o usuario."))
        if (user.passwordHash != SeedData.hashPassword(password)) {
            return Result.failure(IllegalArgumentException("Contraseña incorrecta. Intenta de nuevo."))
        }
        if (user.isBanned) {
            val reasonText = user.banReason?.takeIf { it.isNotBlank() } ?: "actividad ilegal o incumplimiento grave"
            return Result.failure(IllegalStateException("Tu cuenta está baneada permanentemente: $reasonText"))
        }
        val resolvedUser = if (user.email.equals(PRIMARY_OWNER_EMAIL, ignoreCase = true) && !user.isDeveloper) {
            db.userDao().updateIsDeveloper(user.id, true)
            user.copy(isDeveloper = true)
        } else {
            user
        }
        return Result.success(resolvedUser.toDomain())
    }

    suspend fun register(username: String, email: String, password: String): Result<UserProfile> {
        return registerWithoutSessionChange(username, email, password).onSuccess {
            prefs.setCurrentUserId(it.id)
        }
    }

    suspend fun registerWithoutSessionChange(username: String, email: String, password: String): Result<UserProfile> {
        val cleanUser = username.trim()
        val cleanEmail = email.trim()
        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("El nombre de usuario debe tener al menos 3 caracteres."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Ingresa un correo electrónico válido."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener mínimo 6 caracteres."))
        }
        val existingByUser = db.userDao().findByEmailOrUsername(cleanUser)
        if (existingByUser != null) {
            val msg = if (existingByUser.isBanned) {
                "Esta cuenta está baneada permanentemente y no puede volver a usarse."
            } else {
                "Ese nombre de usuario ya está en uso."
            }
            return Result.failure(IllegalArgumentException(msg))
        }
        val existingByEmail = db.userDao().findByEmailOrUsername(cleanEmail)
        if (existingByEmail != null) {
            val msg = if (existingByEmail.isBanned) {
                "Este correo está baneado permanentemente y no puede volver a usarse."
            } else {
                "Ese correo ya está registrado."
            }
            return Result.failure(IllegalArgumentException(msg))
        }

        val isPrimaryOwner = cleanEmail.equals(PRIMARY_OWNER_EMAIL, ignoreCase = true)
        val uid = "user_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val newUser = UserEntity(
            id = uid,
            username = cleanUser,
            email = cleanEmail,
            passwordHash = SeedData.hashPassword(password),
            bio = "",
            customStatus = "",
            avatarFrameId = AvatarFrame.NEON_PULSE.id,
            avatarUri = null,
            presetAvatarKey = "anime",
            communitiesCount = 0,
            followersCount = 0,
            postsCount = 0,
            appRankId = if (isPrimaryOwner) AppRank.OWNER.id else AppRank.NONE.id,
            isDeveloper = isPrimaryOwner
        )
        db.userDao().upsertUser(newUser)
        if (isPrimaryOwner) {
            db.notificationDao().insertNotification(
                NotificationEntity(
                    userId = uid,
                    actorName = "NexoLive",
                    actorAvatarKey = "anime",
                    actorAvatarUri = null,
                    actorFrameId = AvatarFrame.ROYAL_CROWN.id,
                    title = "Cuenta Dueño activada (Verificado Negro)",
                    subtitle = "Crea tu primera comunidad y otorga verificados desde Perfil o salas",
                    category = NotificationCategory.COMMUNITY.name,
                    targetCommunityId = null,
                    isRead = false,
                    timestampMillis = now
                )
            )
        }
        return Result.success(newUser.toDomain())
    }

    suspend fun logout() {
        prefs.setCurrentUserId(null)
    }

    // --- PROFILE CUSTOMIZATION ---

    suspend fun copyPickedImageToInternalStorage(
        uri: Uri,
        folder: String,
        prefix: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.filesDir, folder).apply { mkdirs() }
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val ext = if (mime.contains("png")) "png" else "jpg"
            val destFile = File(targetDir, "${prefix}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("No se pudo leer la imagen"))
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyPickedAvatarToInternalStorage(uri: Uri): Result<String> =
        copyPickedImageToInternalStorage(uri, "avatars", "avatar")

    suspend fun saveBase64ImageToInternalStorage(
        base64OrDataUrl: String,
        folder: String,
        prefix: String,
        forcePng: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isPng = forcePng || base64OrDataUrl.startsWith("data:image/png", ignoreCase = true)
            val rawBase64 = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl).trim()
            val decoded = android.util.Base64.decode(rawBase64, android.util.Base64.DEFAULT)
            if (decoded.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Imagen vacía o inválida."))
            }
            val targetDir = File(context.filesDir, folder).apply { mkdirs() }
            val ext = if (isPng) "png" else "jpg"
            val destFile = File(targetDir, "${prefix}_${System.currentTimeMillis()}.$ext")
            destFile.writeBytes(decoded)
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBase64AudioToInternalStorage(
        base64OrDataUrl: String,
        folder: String = "audio_notes",
        prefix: String = "voice"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val header = base64OrDataUrl.substringBefore("base64,", "").lowercase()
            val ext = when {
                header.contains("webm") -> "webm"
                header.contains("ogg") -> "ogg"
                header.contains("wav") -> "wav"
                header.contains("mpeg") || header.contains("mp3") -> "mp3"
                else -> "m4a"
            }
            val rawBase64 = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl).trim()
            val decoded = android.util.Base64.decode(rawBase64, android.util.Base64.DEFAULT)
            if (decoded.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Audio vacío o inválido."))
            }
            val targetDir = File(context.filesDir, folder).apply { mkdirs() }
            val destFile = File(targetDir, "${prefix}_${UUID.randomUUID().toString().take(8)}.$ext")
            destFile.writeBytes(decoded)
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfileCustomization(
        userId: String,
        username: String,
        bio: String,
        customStatus: String,
        frameId: String,
        avatarUri: String?,
        presetAvatarKey: String
    ): Result<Unit> {
        val cleanName = username.trim()
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("El nombre debe tener al menos 3 caracteres."))
        }
        db.userDao().updateProfileCustomization(
            userId = userId,
            username = cleanName.take(28),
            bio = bio.trim().take(160),
            status = customStatus.trim().take(60),
            frameId = frameId.ifBlank { AvatarFrame.NEON_PULSE.id },
            avatarUri = avatarUri,
            presetAvatarKey = presetAvatarKey
        )
        return Result.success(Unit)
    }

    // --- CUSTOM AVATAR FRAMES (FREE & PNG) ---

    suspend fun publishPngFrame(
        creatorId: String,
        title: String,
        pngPath: String
    ): Result<CustomFrame> {
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Escribe un nombre de al menos 2 caracteres para el marco."))
        }
        val creator = db.userDao().getUserById(creatorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        val entity = CustomFrameEntity(
            id = "custom_frame_${UUID.randomUUID().toString().take(8)}",
            creatorId = creatorId,
            creatorName = creator.username,
            title = cleanTitle.take(26),
            frameType = "PNG",
            pngPath = pngPath,
            primaryColorHex = 0xFF9D4EDD,
            secondaryColorHex = 0xFFFF4EC2,
            stylePatternId = CustomFrameStyle.RING_GLOW.id,
            thicknessDp = 4f,
            hasSparkles = false,
            createdAtMillis = System.currentTimeMillis()
        )
        db.customFrameDao().insertFrame(entity)
        return Result.success(entity.toDomain())
    }

    suspend fun publishStudioFrame(
        creatorId: String,
        title: String,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        stylePattern: CustomFrameStyle,
        thicknessDp: Float,
        hasSparkles: Boolean
    ): Result<CustomFrame> {
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Escribe un nombre de al menos 2 caracteres para el marco."))
        }
        val creator = db.userDao().getUserById(creatorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        val entity = CustomFrameEntity(
            id = "custom_frame_${UUID.randomUUID().toString().take(8)}",
            creatorId = creatorId,
            creatorName = creator.username,
            title = cleanTitle.take(26),
            frameType = "STUDIO",
            pngPath = null,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            stylePatternId = stylePattern.id,
            thicknessDp = thicknessDp.coerceIn(2f, 8f),
            hasSparkles = hasSparkles,
            createdAtMillis = System.currentTimeMillis()
        )
        db.customFrameDao().insertFrame(entity)
        return Result.success(entity.toDomain())
    }

    suspend fun deleteCustomFrame(frameId: String, actorId: String): Result<Unit> {
        val frame = db.customFrameDao().getFrameById(frameId)
            ?: return Result.failure(IllegalStateException("Marco no encontrado."))
        val actor = db.userDao().getUserById(actorId)
        val isOwner = actor != null && (actor.isDeveloper || AppRank.fromId(actor.appRankId) == AppRank.OWNER)
        if (frame.creatorId != actorId && !isOwner) {
            return Result.failure(IllegalStateException("Solo el creador del marco o el Dueño pueden eliminarlo."))
        }
        db.customFrameDao().deleteFrameById(frameId)
        return Result.success(Unit)
    }

    suspend fun unlockDeveloperMode(userId: String, code: String): Result<Unit> {
        if (!code.trim().equals("TXL2026", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Código de desarrollador incorrecto."))
        }
        db.userDao().getUserById(userId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        db.userDao().updateIsDeveloper(userId, true)
        return Result.success(Unit)
    }

    suspend fun assignAppRank(
        actorId: String,
        targetUserId: String,
        newRank: AppRank
    ): Result<Unit> {
        val actor = db.userDao().getUserById(actorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        if (!actor.isDeveloper && AppRank.fromId(actor.appRankId) != AppRank.OWNER) {
            return Result.failure(IllegalStateException("Solo el desarrollador o Dueño puede cambiar verificados."))
        }
        db.userDao().getUserById(targetUserId)
            ?: return Result.failure(IllegalStateException("Usuario destino no encontrado."))
        db.userDao().updateAppRank(targetUserId, newRank.id)
        if (targetUserId != actorId) {
            db.notificationDao().insertNotification(
                NotificationEntity(
                    userId = targetUserId,
                    actorName = actor.username,
                    actorAvatarKey = actor.presetAvatarKey,
                    actorAvatarUri = actor.avatarUri,
                    actorFrameId = actor.avatarFrameId,
                    title = if (newRank == AppRank.NONE) "Insignia actualizada" else "¡Recibiste una insignia de verificación!",
                    subtitle = if (newRank == AppRank.NONE) "Tu insignia de perfil fue actualizada" else "Ya aparece junto a tu nombre en perfiles y salas",
                    category = NotificationCategory.COMMUNITY.name,
                    targetCommunityId = null,
                    isRead = false,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun banUser(
        actorId: String,
        targetUserId: String,
        reason: String
    ): Result<Unit> {
        val actor = db.userDao().getUserById(actorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        if (actor.isBanned || (!actor.isDeveloper && AppRank.fromId(actor.appRankId) != AppRank.OWNER)) {
            return Result.failure(IllegalStateException("Solo el desarrollador o Dueño principal puede banear usuarios."))
        }
        if (actorId == targetUserId) {
            return Result.failure(IllegalArgumentException("No puedes banearte a ti mismo."))
        }
        val target = db.userDao().getUserById(targetUserId)
            ?: return Result.failure(IllegalStateException("Usuario objetivo no encontrado."))
        if (target.email.equals(PRIMARY_OWNER_EMAIL, ignoreCase = true)) {
            return Result.failure(IllegalStateException("No se puede banear la cuenta principal."))
        }
        val cleanReason = reason.trim().ifBlank { "Actividad ilegal o incumplimiento grave de normas" }.take(180)
        db.userDao().updateBanStatus(targetUserId, true, cleanReason)

        val roomIds = db.voiceRoomDao().getCommunityIdsForParticipant(targetUserId)
        db.voiceRoomDao().removeParticipantFromAllRooms(targetUserId)
        val now = System.currentTimeMillis()
        roomIds.forEach { cid ->
            syncActiveVoiceCount(cid)
            db.voiceRoomDao().insertEvent(
                RoomEventEntity(
                    communityId = cid,
                    actorName = target.username,
                    description = "fue baneado permanentemente de NexoLive",
                    timestampMillis = now
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun unbanUser(
        actorId: String,
        targetUserId: String
    ): Result<Unit> {
        val actor = db.userDao().getUserById(actorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        if (actor.isBanned || (!actor.isDeveloper && AppRank.fromId(actor.appRankId) != AppRank.OWNER)) {
            return Result.failure(IllegalStateException("Solo el desarrollador o Dueño principal puede quitar baneos."))
        }
        db.userDao().getUserById(targetUserId)
            ?: return Result.failure(IllegalStateException("Usuario objetivo no encontrado."))
        db.userDao().updateBanStatus(targetUserId, false, null)
        return Result.success(Unit)
    }

    // --- COMMUNITIES ---

    suspend fun createCommunity(
        creatorId: String,
        name: String,
        category: String,
        description: String,
        coverKey: String,
        backgroundUri: String? = null
    ): Result<String> {
        if (isUserBannedOrMissing(creatorId)) {
            return Result.failure(IllegalStateException("Tu cuenta está suspendida."))
        }
        val cleanName = name.trim()
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("El nombre debe tener al menos 3 caracteres."))
        }
        val cid = "comm_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        db.communityDao().upsertCommunity(
            CommunityEntity(
                id = cid,
                name = cleanName.take(36),
                category = category.ifBlank { "Comunidad" },
                description = description.trim().ifBlank { "Nuevo círculo abierto para charlar en tiempo real." },
                coverKey = coverKey,
                badgeLabel = "nueva",
                memberCount = 1,
                activeVoiceCount = 0,
                isVoiceLocked = false,
                backgroundUri = backgroundUri
            )
        )
        // Creator is Admin with total room control
        db.membershipDao().upsertMembership(
            MembershipEntity(cid, creatorId, CommunityRole.ADMIN.name, now)
        )
        val count = db.membershipDao().countMemberships(creatorId)
        db.userDao().updateCommunitiesCount(creatorId, count)
        val creator = db.userDao().getUserById(creatorId)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = cid,
                actorName = creator?.username ?: "Admin",
                description = "creó la comunidad como Admin",
                timestampMillis = now
            )
        )
        return Result.success(cid)
    }

    suspend fun updateCommunityBackground(
        communityId: String,
        actorId: String,
        backgroundUri: String?
    ): Result<Unit> {
        val actor = db.userDao().getUserById(actorId)
        val isOwner = actor != null && (actor.isDeveloper || AppRank.fromId(actor.appRankId) == AppRank.OWNER)
        val actorRole = getRole(communityId, actorId)
        if (actorRole != CommunityRole.ADMIN && actorRole != CommunityRole.CO_ADMIN && !isOwner) {
            return Result.failure(IllegalStateException("Solo Admin o Co-Admin pueden cambiar el fondo de la sala."))
        }
        db.communityDao().updateBackground(communityId, backgroundUri)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = actor?.username ?: "Admin",
                description = if (backgroundUri != null) "personalizó el fondo de la sala" else "restauró el fondo predeterminado de la sala",
                timestampMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteCommunity(communityId: String, actorId: String): Result<Unit> {
        val actor = db.userDao().getUserById(actorId)
        val isOwner = actor != null && (actor.isDeveloper || AppRank.fromId(actor.appRankId) == AppRank.OWNER)
        val actorRole = getRole(communityId, actorId)
        if (actorRole != CommunityRole.ADMIN && !isOwner) {
            return Result.failure(IllegalStateException("Solo el Admin de la sala o el Dueño pueden eliminarla."))
        }
        val affectedUserIds = db.membershipDao().getUserIdsByCommunity(communityId)
        db.voiceRoomDao().deleteAllParticipantsForCommunity(communityId)
        db.voiceRoomDao().clearRoomEvents(communityId)
        db.chatDao().deleteAllMessagesForCommunity(communityId)
        db.postDao().clearCommunityFromPosts(communityId)
        db.membershipDao().deleteAllForCommunity(communityId)
        db.communityDao().deleteCommunityById(communityId)
        affectedUserIds.distinct().forEach { uid ->
            val newCount = db.membershipDao().countMemberships(uid)
            db.userDao().updateCommunitiesCount(uid, newCount)
        }
        return Result.success(Unit)
    }

    suspend fun toggleJoinCommunity(communityId: String, userId: String) {
        val existing = db.membershipDao().getMembership(communityId, userId)
        val now = System.currentTimeMillis()
        if (existing == null) {
            db.membershipDao().upsertMembership(
                MembershipEntity(communityId, userId, CommunityRole.MEMBER.name, now)
            )
            db.communityDao().adjustMemberCount(communityId, 1)
        } else if (existing.role != CommunityRole.ADMIN.name) {
            db.membershipDao().deleteMembership(communityId, userId)
            db.voiceRoomDao().removeParticipant(communityId, userId)
            syncActiveVoiceCount(communityId)
            db.communityDao().adjustMemberCount(communityId, -1)
        }
        val count = db.membershipDao().countMemberships(userId)
        db.userDao().updateCommunitiesCount(userId, count)
    }

    // --- VOICE ROOM & ROLES ---

    suspend fun joinVoiceRoom(communityId: String, userId: String) {
        if (isUserBannedOrMissing(userId)) return
        val now = System.currentTimeMillis()
        if (db.membershipDao().getMembership(communityId, userId) == null) {
            db.membershipDao().upsertMembership(
                MembershipEntity(communityId, userId, CommunityRole.MEMBER.name, now)
            )
            db.communityDao().adjustMemberCount(communityId, 1)
            val count = db.membershipDao().countMemberships(userId)
            db.userDao().updateCommunitiesCount(userId, count)
        }
        if (db.voiceRoomDao().getParticipant(communityId, userId) == null) {
            db.voiceRoomDao().upsertParticipant(
                VoiceParticipantEntity(
                    id = "${communityId}_$userId",
                    communityId = communityId,
                    userId = userId,
                    isMuted = true,
                    isSpeaking = false,
                    isHandRaised = false
                )
            )
            syncActiveVoiceCount(communityId)
            val user = db.userDao().getUserById(userId)
            db.voiceRoomDao().insertEvent(
                RoomEventEntity(
                    communityId = communityId,
                    actorName = user?.username ?: "Miembro",
                    description = "entró a la sala de voz",
                    timestampMillis = now
                )
            )
        }
    }

    suspend fun leaveVoiceRoom(communityId: String, userId: String) {
        db.voiceRoomDao().removeParticipant(communityId, userId)
        syncActiveVoiceCount(communityId)
        val user = db.userDao().getUserById(userId)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = user?.username ?: "Miembro",
                description = "salió de la sala de voz",
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun setSelfMuted(communityId: String, userId: String, muted: Boolean) {
        db.voiceRoomDao().setMuted(communityId, userId, muted)
    }

    suspend fun setSelfSpeaking(communityId: String, userId: String, speaking: Boolean) {
        db.voiceRoomDao().setSpeaking(communityId, userId, speaking)
    }

    suspend fun toggleHandRaised(communityId: String, userId: String) {
        val current = db.voiceRoomDao().getParticipant(communityId, userId) ?: return
        val next = !current.isHandRaised
        db.voiceRoomDao().setHandRaised(communityId, userId, next)
        val user = db.userDao().getUserById(userId)
        if (next) {
            db.voiceRoomDao().insertEvent(
                RoomEventEntity(
                    communityId = communityId,
                    actorName = user?.username ?: "Miembro",
                    description = "levantó la mano para pedir turno",
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun moderateMuteParticipant(
        communityId: String,
        actorId: String,
        targetUserId: String,
        mute: Boolean
    ): Result<Unit> {
        val actorRole = getRole(communityId, actorId)
        val targetRole = getRole(communityId, targetUserId)
        if (actorRole.priority <= CommunityRole.MEMBER.priority || actorRole.priority <= targetRole.priority) {
            return Result.failure(IllegalStateException("Sin permisos suficientes para silenciar a este usuario."))
        }
        db.voiceRoomDao().setMuted(communityId, targetUserId, mute)
        val actor = db.userDao().getUserById(actorId)
        val target = db.userDao().getUserById(targetUserId)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = actor?.username ?: "Moderador",
                description = if (mute) "silenció a ${target?.username}" else "habilitó el micro de ${target?.username}",
                timestampMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun kickParticipant(
        communityId: String,
        actorId: String,
        targetUserId: String
    ): Result<Unit> {
        val actorRole = getRole(communityId, actorId)
        if (actorRole != CommunityRole.ADMIN) {
            return Result.failure(IllegalStateException("Solo el Admin puede expulsar participantes de la sala."))
        }
        db.voiceRoomDao().removeParticipant(communityId, targetUserId)
        syncActiveVoiceCount(communityId)
        val target = db.userDao().getUserById(targetUserId)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = target?.username ?: "Usuario",
                description = "fue expulsado de la sala por el Admin",
                timestampMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun changeParticipantRole(
        communityId: String,
        actorId: String,
        targetUserId: String,
        newRole: CommunityRole
    ): Result<Unit> {
        val actorRole = getRole(communityId, actorId)
        if (actorRole != CommunityRole.ADMIN) {
            return Result.failure(IllegalStateException("Solo el Admin tiene control total para asignar Co-Admins."))
        }
        if (newRole == CommunityRole.ADMIN) {
            return Result.failure(IllegalStateException("Solo puede haber un Admin principal por sala."))
        }
        db.membershipDao().updateRole(communityId, targetUserId, newRole.name)
        val actor = db.userDao().getUserById(actorId)
        val target = db.userDao().getUserById(targetUserId)
        val actionText = if (newRole == CommunityRole.CO_ADMIN) {
            "promovió a ${target?.username} a Co-Admin"
        } else {
            "cambió el rol de ${target?.username} a Miembro"
        }
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = actor?.username ?: "Admin",
                description = actionText,
                timestampMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun toggleRoomLock(communityId: String, actorId: String, lock: Boolean): Result<Unit> {
        if (getRole(communityId, actorId) != CommunityRole.ADMIN) {
            return Result.failure(IllegalStateException("Solo el Admin puede bloquear o desbloquear la sala."))
        }
        db.communityDao().setVoiceLocked(communityId, lock)
        val actor = db.userDao().getUserById(actorId)
        db.voiceRoomDao().insertEvent(
            RoomEventEntity(
                communityId = communityId,
                actorName = actor?.username ?: "Admin",
                description = if (lock) "bloqueó la sala de voz" else "desbloqueó la sala de voz",
                timestampMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun clearRoomEvents(communityId: String, actorId: String): Result<Unit> {
        if (getRole(communityId, actorId) != CommunityRole.ADMIN) {
            return Result.failure(IllegalStateException("Solo el Admin puede limpiar el registro de eventos."))
        }
        db.voiceRoomDao().clearRoomEvents(communityId)
        return Result.success(Unit)
    }

    // --- CHAT MESSAGES ---

    suspend fun sendMessage(
        communityId: String,
        channelType: String,
        senderId: String,
        content: String,
        replyToSnippet: String? = null
    ) {
        if (isUserBannedOrMissing(senderId)) return
        val clean = content.trim()
        if (clean.isEmpty()) return
        db.chatDao().insertMessage(
            ChatMessageEntity(
                communityId = communityId,
                channelType = channelType,
                senderId = senderId,
                content = clean,
                replyToSnippet = replyToSnippet,
                reactionCount = 0,
                timestampMillis = System.currentTimeMillis()
            )
        )
        db.userDao().incrementPostsCount(senderId)
    }

    suspend fun sendAudioMessage(
        communityId: String,
        channelType: String,
        senderId: String,
        audioUri: String,
        durationMs: Long,
        replyToSnippet: String? = null
    ): Result<Unit> {
        if (isUserBannedOrMissing(senderId)) {
            return Result.failure(IllegalStateException("Tu cuenta está suspendida."))
        }
        if (audioUri.isBlank()) {
            return Result.failure(IllegalArgumentException("El mensaje de audio está vacío."))
        }
        db.chatDao().insertMessage(
            ChatMessageEntity(
                communityId = communityId,
                channelType = channelType,
                senderId = senderId,
                content = "Nota de voz",
                replyToSnippet = replyToSnippet,
                reactionCount = 0,
                timestampMillis = System.currentTimeMillis(),
                audioUri = audioUri,
                audioDurationMs = durationMs.coerceAtLeast(1000L)
            )
        )
        db.userDao().incrementPostsCount(senderId)
        return Result.success(Unit)
    }

    suspend fun getMessageById(messageId: Long): ChatMessageEntity? =
        db.chatDao().getMessageById(messageId)

    suspend fun deleteMessage(communityId: String, actorId: String, message: ChatMessage): Result<Unit> {
        val actorRole = getRole(communityId, actorId)
        val canModerate = actorRole == CommunityRole.ADMIN ||
            (actorRole == CommunityRole.CO_ADMIN && message.senderRole != CommunityRole.ADMIN) ||
            message.sender.id == actorId
        if (!canModerate) {
            return Result.failure(IllegalStateException("Solo Admin o Co-Admin pueden eliminar este mensaje."))
        }
        db.chatDao().deleteMessage(message.id)
        message.audioUri?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                val root = File(context.filesDir, "audio_notes").canonicalPath
                val f = File(path)
                if (f.exists() && f.canonicalPath.startsWith(root)) f.delete()
            }
        }
        if (message.sender.id != actorId) {
            val actor = db.userDao().getUserById(actorId)
            db.voiceRoomDao().insertEvent(
                RoomEventEntity(
                    communityId = communityId,
                    actorName = actor?.username ?: "Moderador",
                    description = "eliminó un mensaje por moderación",
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun deleteMessageById(communityId: String, actorId: String, messageId: Long): Result<Unit> {
        val msgEntity = db.chatDao().getMessageById(messageId)
            ?: return Result.failure(IllegalStateException("Mensaje no encontrado."))
        val senderRole = getRole(communityId, msgEntity.senderId)
        val sender = db.userDao().getUserById(msgEntity.senderId)?.toDomain()
            ?: UserProfile(
                id = msgEntity.senderId,
                username = "Usuario",
                email = "",
                bio = "",
                customStatus = "",
                avatarFrame = AvatarFrame.NONE,
                avatarUri = null,
                presetAvatarKey = "anime",
                communitiesCount = 0,
                followersCount = 0,
                postsCount = 0
            )
        val msg = ChatMessage(
            id = msgEntity.id,
            communityId = msgEntity.communityId,
            channelType = msgEntity.channelType,
            sender = sender,
            senderRole = senderRole,
            content = msgEntity.content,
            replyToSnippet = msgEntity.replyToSnippet,
            reactionCount = msgEntity.reactionCount,
            timestampMillis = msgEntity.timestampMillis,
            audioUri = msgEntity.audioUri,
            audioDurationMs = msgEntity.audioDurationMs
        )
        return deleteMessage(communityId, actorId, msg)
    }

    suspend fun reactToMessage(messageId: Long) {
        db.chatDao().incrementReaction(messageId)
    }

    // --- DRIFTING BOTTLE ---

    suspend fun launchDriftingBottle(
        authorId: String,
        moodTag: String,
        content: String
    ): Result<Unit> {
        if (isUserBannedOrMissing(authorId)) {
            return Result.failure(IllegalStateException("Tu cuenta está suspendida."))
        }
        val clean = content.trim()
        if (clean.length < 4) {
            return Result.failure(IllegalArgumentException("Escribe un mensaje de al menos 4 caracteres."))
        }
        val now = System.currentTimeMillis()
        val aliases = listOf(
            "Cometa Neón",
            "Sombra Violeta",
            "Pulso Estelar",
            "Eco Lunar",
            "Brisa Cyber",
            "Nébula Rosa"
        )
        val alias = "${aliases.random()} #${(100..999).random()}"
        db.driftingBottleDao().purgeExpired(now)
        db.driftingBottleDao().insertBottle(
            DriftingBottleEntity(
                id = "bottle_${UUID.randomUUID().toString().take(8)}",
                authorId = authorId,
                anonymousAlias = alias,
                moodTag = moodTag.ifBlank { "Anónimo" },
                content = clean.take(280),
                replyContent = null,
                isPickedByMe = false,
                createdAtMillis = now,
                expiresAtMillis = now + 24 * 3_600_000L
            )
        )
        db.userDao().incrementPostsCount(authorId)
        return Result.success(Unit)
    }

    suspend fun pickRandomDriftingBottle(userId: String): Result<DriftingBottle> {
        val now = System.currentTimeMillis()
        db.driftingBottleDao().purgeExpired(now)
        val picked = db.driftingBottleDao().pickRandomBottle(now, userId)
            ?: db.driftingBottleDao().pickAnyActiveBottle(now)
            ?: return Result.failure(IllegalStateException("El océano neón está tranquilo. ¡Lanza la primera botella!"))

        db.driftingBottleDao().markPickedWithReply(picked.id, picked.replyContent)
        // Notify current user / log interaction
        db.notificationDao().insertNotification(
            NotificationEntity(
                userId = userId,
                actorName = picked.anonymousAlias,
                actorAvatarKey = "music",
                actorAvatarUri = null,
                actorFrameId = AvatarFrame.STARLIGHT_HALO.id,
                title = "Recogiste una Drifting Bottle",
                subtitle = "\"${picked.content.take(55)}...\"",
                category = NotificationCategory.DRIFTING_BOTTLE.name,
                targetCommunityId = null,
                isRead = false,
                timestampMillis = now
            )
        )
        return Result.success(picked.copy(isPickedByMe = true).toDomain())
    }

    suspend fun replyToBottle(bottleId: String, reply: String) {
        val clean = reply.trim()
        if (clean.isEmpty()) return
        db.driftingBottleDao().markPickedWithReply(bottleId, clean.take(200))
    }

    // --- COMMUNITY POSTS ---

    suspend fun createPost(
        authorId: String,
        communityId: String?,
        content: String,
        imageUri: String?
    ): Result<CommunityPost> {
        if (isUserBannedOrMissing(authorId)) {
            return Result.failure(IllegalStateException("Tu cuenta está suspendida."))
        }
        val clean = content.trim()
        if (clean.isEmpty() && imageUri.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Escribe un mensaje o agrega una foto para publicar."))
        }
        val author = db.userDao().getUserById(authorId)?.toDomain()
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        val communityName = if (!communityId.isNullOrBlank()) {
            db.communityDao().getCommunityById(communityId)?.name
        } else null
        val now = System.currentTimeMillis()
        val entity = PostEntity(
            id = "post_${UUID.randomUUID().toString().take(8)}",
            authorId = authorId,
            communityId = communityId?.takeIf { it.isNotBlank() },
            content = clean.take(500),
            imageUri = imageUri,
            likesCount = 0,
            createdAtMillis = now
        )
        db.postDao().insertPost(entity)
        db.userDao().incrementPostsCount(authorId)
        return Result.success(
            CommunityPost(
                id = entity.id,
                author = author,
                communityId = entity.communityId,
                communityName = communityName,
                content = entity.content,
                imageUri = entity.imageUri,
                likesCount = 0,
                commentsCount = 0,
                createdAtMillis = now
            )
        )
    }

    suspend fun likePost(postId: String) {
        db.postDao().incrementLikes(postId)
    }

    suspend fun addPostComment(postId: String, authorId: String, content: String): Result<Unit> {
        val clean = content.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Escribe un comentario."))
        }
        val post = db.postDao().getPostById(postId)
            ?: return Result.failure(IllegalStateException("Publicación no encontrada."))
        val actor = db.userDao().getUserById(authorId)
            ?: return Result.failure(IllegalStateException("Usuario no encontrado."))
        val now = System.currentTimeMillis()
        db.postDao().insertComment(
            PostCommentEntity(
                postId = postId,
                authorId = authorId,
                content = clean.take(280),
                createdAtMillis = now
            )
        )
        if (post.authorId != authorId) {
            db.notificationDao().insertNotification(
                NotificationEntity(
                    userId = post.authorId,
                    actorName = actor.username,
                    actorAvatarKey = actor.presetAvatarKey,
                    actorAvatarUri = actor.avatarUri,
                    actorFrameId = actor.avatarFrameId,
                    title = "${actor.username} comentó tu publicación",
                    subtitle = clean.take(60),
                    category = NotificationCategory.MENTION.name,
                    targetCommunityId = post.communityId,
                    isRead = false,
                    timestampMillis = now
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun deletePost(postId: String, actorId: String): Result<Unit> {
        val post = db.postDao().getPostById(postId)
            ?: return Result.failure(IllegalStateException("Publicación no encontrada."))
        val actor = db.userDao().getUserById(actorId)
        val isOwner = actor != null && (actor.isDeveloper || AppRank.fromId(actor.appRankId) == AppRank.OWNER)
        if (post.authorId != actorId && !isOwner) {
            return Result.failure(IllegalStateException("Solo el autor o el Dueño pueden eliminar esta publicación."))
        }
        db.postDao().deleteCommentsForPost(postId)
        db.postDao().deletePost(postId)
        return Result.success(Unit)
    }

    // --- NOTIFICATIONS & SETTINGS ---

    suspend fun markNotificationRead(id: Long) {
        db.notificationDao().markAsRead(id)
    }

    suspend fun markAllNotificationsRead(userId: String) {
        db.notificationDao().markAllAsRead(userId)
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        prefs.updateSettings(transform)
    }

    private suspend fun syncActiveVoiceCount(communityId: String) {
        val count = db.voiceRoomDao().countParticipants(communityId)
        db.communityDao().updateActiveVoiceCount(communityId, count)
    }

    private suspend fun getRole(communityId: String, userId: String): CommunityRole {
        val m = db.membershipDao().getMembership(communityId, userId)
        return m?.let { CommunityRole.fromRaw(it.role) } ?: CommunityRole.MEMBER
    }

    private suspend fun isUserBannedOrMissing(userId: String): Boolean {
        val user = db.userDao().getUserById(userId) ?: return true
        return user.isBanned
    }

    private fun UserEntity.toDomain() = UserProfile(
        id = id,
        username = username,
        email = email,
        bio = bio,
        customStatus = customStatus,
        avatarFrame = if (avatarFrameId.startsWith("custom_frame_")) AvatarFrame.NEON_PULSE else AvatarFrame.fromId(avatarFrameId),
        avatarUri = avatarUri,
        presetAvatarKey = presetAvatarKey,
        communitiesCount = communitiesCount,
        followersCount = followersCount,
        postsCount = postsCount,
        appRank = AppRank.fromId(appRankId),
        avatarFrameId = avatarFrameId,
        isDeveloper = isDeveloper,
        isBanned = isBanned,
        banReason = banReason
    )

    private fun CommunityEntity.toDomain(myRole: CommunityRole?, isJoined: Boolean) = Community(
        id = id,
        name = name,
        category = category,
        description = description,
        coverKey = coverKey,
        badgeLabel = badgeLabel,
        memberCount = memberCount,
        activeVoiceCount = activeVoiceCount,
        isVoiceLocked = isVoiceLocked,
        myRole = myRole,
        isJoined = isJoined,
        backgroundUri = backgroundUri
    )

    private fun CustomFrameEntity.toDomain() = CustomFrame(
        id = id,
        creatorId = creatorId,
        creatorName = creatorName,
        title = title,
        frameType = frameType,
        pngPath = pngPath,
        primaryColorHex = primaryColorHex,
        secondaryColorHex = secondaryColorHex,
        stylePattern = CustomFrameStyle.fromId(stylePatternId),
        thicknessDp = thicknessDp,
        hasSparkles = hasSparkles,
        createdAtMillis = createdAtMillis
    )

    private fun DriftingBottleEntity.toDomain() = DriftingBottle(
        id = id,
        authorId = authorId,
        anonymousAlias = anonymousAlias,
        moodTag = moodTag,
        content = content,
        replyContent = replyContent,
        isPickedByMe = isPickedByMe,
        createdAtMillis = createdAtMillis,
        expiresAtMillis = expiresAtMillis
    )

    suspend fun getUserById(userId: String): UserProfile? =
        db.userDao().getUserById(userId)?.toDomain()

    suspend fun getCommunityById(communityId: String): CommunityEntity? =
        db.communityDao().getCommunityById(communityId)

    suspend fun getWebSnapshot(userId: String?, communityId: String?): WebSnapshot {
        val allUsers = observeAllUsersList().first()
        val activeUser = if (!userId.isNullOrBlank()) allUsers.firstOrNull { it.id == userId } else null
        val effectiveUid = activeUser?.id ?: ""
        val communities = observeCommunities(effectiveUid).first()
        val bottles = observeDriftingBottles().first()
        val notifications = if (effectiveUid.isNotEmpty()) observeNotifications(effectiveUid).first() else emptyList()
        val customFrames = observeCustomFrames().first()
        val posts = observePosts().first()
        val usersMap = allUsers.associateBy { it.id }
        val postComments = db.postDao().observeAllComments().first().mapNotNull { c ->
            val author = usersMap[c.authorId] ?: return@mapNotNull null
            PostComment(c.id, c.postId, author, c.content, c.createdAtMillis)
        }
        val selectedCommunity = if (!communityId.isNullOrBlank()) communities.firstOrNull { it.id == communityId } else null
        val participants = if (selectedCommunity != null) observeVoiceParticipants(selectedCommunity.id).first() else emptyList()
        val events = if (selectedCommunity != null) observeRoomEvents(selectedCommunity.id).first() else emptyList()
        val voiceMessages = if (selectedCommunity != null) observeMessages(selectedCommunity.id, "VOICE").first() else emptyList()
        val groupMessages = if (selectedCommunity != null) observeMessages(selectedCommunity.id, "GROUP").first() else emptyList()
        return WebSnapshot(
            hasAnyUser = allUsers.isNotEmpty(),
            user = activeUser,
            allUsers = allUsers,
            communities = communities,
            bottles = bottles,
            notifications = notifications,
            customFrames = customFrames,
            posts = posts,
            postComments = postComments,
            selectedCommunity = selectedCommunity,
            participants = participants,
            events = events,
            voiceMessages = voiceMessages,
            groupMessages = groupMessages
        )
    }
}

data class WebSnapshot(
    val hasAnyUser: Boolean,
    val user: UserProfile?,
    val allUsers: List<UserProfile>,
    val communities: List<Community>,
    val bottles: List<DriftingBottle>,
    val notifications: List<AppNotification>,
    val customFrames: List<CustomFrame>,
    val posts: List<CommunityPost>,
    val postComments: List<PostComment>,
    val selectedCommunity: Community?,
    val participants: List<VoiceParticipant>,
    val events: List<RoomEventLog>,
    val voiceMessages: List<ChatMessage>,
    val groupMessages: List<ChatMessage>
)
