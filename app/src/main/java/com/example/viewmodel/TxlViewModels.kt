package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceAudioEngine
import com.example.audio.VoiceNotePlayer
import com.example.audio.VoiceNoteRecorder
import com.example.data.TxlRepository
import com.example.data.local.PreferencesStore
import com.example.data.local.TxlDatabase
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
import com.example.model.PostComment
import com.example.model.RoomEventLog
import com.example.model.UserProfile
import com.example.model.VoiceParticipant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = TxlDatabase.getInstance(appContext)
    private val prefs = PreferencesStore(appContext)
    val repository = TxlRepository(appContext, database, prefs)
}

class AuthViewModel(private val repository: TxlRepository) : ViewModel() {
    val currentUser: StateFlow<UserProfile?> = repository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasAnyUser: StateFlow<Boolean?> = repository.observeHasAnyUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeSeedData()
        }
    }

    fun clearError() {
        _authError.value = null
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _authError.value = null
            repository.login(identifier, password)
                .onFailure { _authError.value = it.message }
            _isBusy.value = false
        }
    }

    fun register(username: String, email: String, password: String, confirmPassword: String, acceptedTerms: Boolean) {
        if (!acceptedTerms) {
            _authError.value = "Debes aceptar los términos y condiciones para registrarte."
            return
        }
        if (password != confirmPassword) {
            _authError.value = "Las contraseñas no coinciden."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _authError.value = null
            repository.register(username, email, password)
                .onFailure { _authError.value = it.message }
            _isBusy.value = false
        }
    }
}

class MainViewModel(
    private val repository: TxlRepository,
    val currentUserId: String
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = repository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allUsers: StateFlow<List<UserProfile>> = repository.observeAllUsersList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<Community>> = repository.observeCommunities(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val driftingBottles: StateFlow<List<DriftingBottle>> = repository.observeDriftingBottles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AppNotification>> = repository.observeNotifications(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.observeUnreadNotificationsCount(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val customFrames: StateFlow<List<CustomFrame>> = repository.observeCustomFrames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posts: StateFlow<List<CommunityPost>> = repository.observePosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _pickedBottle = MutableStateFlow<DriftingBottle?>(null)
    val pickedBottle: StateFlow<DriftingBottle?> = _pickedBottle.asStateFlow()

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearPickedBottle() {
        _pickedBottle.value = null
    }

    fun toggleJoinCommunity(communityId: String) {
        viewModelScope.launch {
            repository.toggleJoinCommunity(communityId, currentUserId)
        }
    }

    fun createCommunity(
        name: String,
        category: String,
        description: String,
        coverKey: String,
        backgroundUri: String? = null,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.createCommunity(currentUserId, name, category, description, coverKey, backgroundUri)
                .onSuccess { cid ->
                    _statusBanner.value = "Comunidad creada. Eres Admin de la sala."
                    onCreated(cid)
                }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun deleteCommunity(communityId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCommunity(communityId, currentUserId)
                .onSuccess {
                    _statusBanner.value = "Sala eliminada correctamente."
                    onDeleted()
                }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun saveImageFromUri(uri: Uri, folder: String, prefix: String, onSavedPath: (String) -> Unit) {
        viewModelScope.launch {
            repository.copyPickedImageToInternalStorage(uri, folder, prefix)
                .onSuccess { path -> onSavedPath(path) }
                .onFailure { _statusBanner.value = "No se pudo cargar la imagen seleccionada." }
        }
    }

    fun saveAvatarFromUri(uri: Uri, onSavedPath: (String) -> Unit) {
        saveImageFromUri(uri, "avatars", "avatar", onSavedPath)
    }

    fun saveProfileCustomization(
        username: String,
        bio: String,
        customStatus: String,
        frameId: String,
        avatarUri: String?,
        presetAvatarKey: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateProfileCustomization(
                userId = currentUserId,
                username = username,
                bio = bio,
                customStatus = customStatus,
                frameId = frameId,
                avatarUri = avatarUri,
                presetAvatarKey = presetAvatarKey
            ).onSuccess {
                _statusBanner.value = "Perfil actualizado con éxito"
                onComplete()
            }.onFailure {
                _statusBanner.value = it.message
            }
        }
    }

    fun publishPngFrame(title: String, uri: Uri, equipNow: Boolean = true, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.copyPickedImageToInternalStorage(uri, "frames", "frame")
                .onSuccess { path ->
                    repository.publishPngFrame(currentUserId, title, path)
                        .onSuccess { created ->
                            if (equipNow) {
                                val u = currentUser.value
                                if (u != null) {
                                    repository.updateProfileCustomization(
                                        userId = u.id,
                                        username = u.username,
                                        bio = u.bio,
                                        customStatus = u.customStatus,
                                        frameId = created.id,
                                        avatarUri = u.avatarUri,
                                        presetAvatarKey = u.presetAvatarKey
                                    )
                                }
                            }
                            _statusBanner.value = "Marco PNG \"${created.title}\" publicado gratis"
                            onDone()
                        }
                        .onFailure { _statusBanner.value = it.message }
                }
                .onFailure { _statusBanner.value = "No se pudo importar el archivo PNG." }
        }
    }

    fun publishStudioFrame(
        title: String,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        stylePattern: CustomFrameStyle,
        thicknessDp: Float,
        hasSparkles: Boolean,
        equipNow: Boolean = true,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.publishStudioFrame(
                creatorId = currentUserId,
                title = title,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                stylePattern = stylePattern,
                thicknessDp = thicknessDp,
                hasSparkles = hasSparkles
            ).onSuccess { created ->
                if (equipNow) {
                    val u = currentUser.value
                    if (u != null) {
                        repository.updateProfileCustomization(
                            userId = u.id,
                            username = u.username,
                            bio = u.bio,
                            customStatus = u.customStatus,
                            frameId = created.id,
                            avatarUri = u.avatarUri,
                            presetAvatarKey = u.presetAvatarKey
                        )
                    }
                }
                _statusBanner.value = "Marco \"${created.title}\" creado y publicado gratis"
                onDone()
            }.onFailure {
                _statusBanner.value = it.message
            }
        }
    }

    fun deleteCustomFrame(frameId: String) {
        viewModelScope.launch {
            repository.deleteCustomFrame(frameId, currentUserId)
                .onSuccess { _statusBanner.value = "Marco eliminado." }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun observeCommentsForPost(postId: String): Flow<List<PostComment>> =
        repository.observePostComments(postId)

    fun createPost(
        communityId: String?,
        content: String,
        imagePath: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createPost(currentUserId, communityId, content, imagePath)
                .onSuccess {
                    _statusBanner.value = "Publicación compartida"
                    onDone()
                }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun likePost(postId: String) {
        viewModelScope.launch {
            repository.likePost(postId)
        }
    }

    fun commentOnPost(postId: String, content: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addPostComment(postId, currentUserId, content)
                .onSuccess { onDone() }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId, currentUserId)
                .onSuccess { _statusBanner.value = "Publicación eliminada." }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun launchBottle(moodTag: String, content: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.launchDriftingBottle(currentUserId, moodTag, content)
                .onSuccess {
                    _statusBanner.value = "Botella lanzada al océano neón (expira en 24h)"
                    onSuccess()
                }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun pickRandomBottle() {
        viewModelScope.launch {
            repository.pickRandomDriftingBottle(currentUserId)
                .onSuccess { _pickedBottle.value = it }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun replyToBottle(bottleId: String, reply: String) {
        viewModelScope.launch {
            repository.replyToBottle(bottleId, reply)
            _pickedBottle.value = _pickedBottle.value?.copy(replyContent = reply.trim())
            _statusBanner.value = "Respuesta anónima enviada a la botella"
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun assignAppRank(targetUserId: String, newRank: AppRank) {
        viewModelScope.launch {
            repository.assignAppRank(currentUserId, targetUserId, newRank)
                .onSuccess {
                    _statusBanner.value = if (newRank == AppRank.NONE) {
                        "Verificado retirado."
                    } else {
                        "Verificado actualizado: ${newRank.badgeTitle}"
                    }
                }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun unlockDeveloperMode(code: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.unlockDeveloperMode(currentUserId, code)
                .onSuccess {
                    _statusBanner.value = "Modo Desarrollador activado: ya puedes cambiar verificaciones."
                    onSuccess()
                }
                .onFailure {
                    onError(it.message ?: "Código incorrecto.")
                }
        }
    }

    fun banUser(targetUserId: String, reason: String) {
        viewModelScope.launch {
            repository.banUser(currentUserId, targetUserId, reason)
                .onSuccess { _statusBanner.value = "Usuario baneado permanentemente." }
                .onFailure { _statusBanner.value = it.message }
        }
    }

    fun unbanUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unbanUser(currentUserId, targetUserId)
                .onSuccess { _statusBanner.value = "Baneo retirado. El usuario puede volver a acceder." }
                .onFailure { _statusBanner.value = it.message }
        }
    }
}

class RoomViewModel(
    private val repository: TxlRepository,
    val communityId: String,
    val currentUserId: String
) : ViewModel() {

    private val audioEngine = VoiceAudioEngine()
    private val voiceNoteRecorder = VoiceNoteRecorder(repository.appContext)
    private val voiceNotePlayer = VoiceNotePlayer()
    private var pausedLiveMicForVoiceNote = false
    private var liveMicHasPermission = false

    val micAmplitude: StateFlow<Float> = audioEngine.amplitudeLevel
    val isRecordingVoiceNote: StateFlow<Boolean> = voiceNoteRecorder.isRecording
    val recordingElapsedMs: StateFlow<Long> = voiceNoteRecorder.elapsedMs
    val playingAudioMessageId: StateFlow<Long?> = voiceNotePlayer.playingMessageId
    val playingAudioProgress: StateFlow<Float> = voiceNotePlayer.progress

    val currentUser: StateFlow<UserProfile?> = repository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val community: StateFlow<Community?> = repository.observeCommunity(communityId, currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val voiceParticipants: StateFlow<List<VoiceParticipant>> = repository.observeVoiceParticipants(communityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomEvents: StateFlow<List<RoomEventLog>> = repository.observeRoomEvents(communityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceChatMessages: StateFlow<List<ChatMessage>> = repository.observeMessages(communityId, "VOICE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupChatMessages: StateFlow<List<ChatMessage>> = repository.observeMessages(communityId, "GROUP")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _roomNotice = MutableStateFlow<String?>(null)
    val roomNotice: StateFlow<String?> = _roomNotice.asStateFlow()

    fun clearNotice() {
        _roomNotice.value = null
    }

    fun enterVoiceRoom() {
        viewModelScope.launch {
            repository.joinVoiceRoom(communityId, currentUserId)
        }
    }

    fun leaveVoiceRoom() {
        pausedLiveMicForVoiceNote = false
        voiceNoteRecorder.release()
        voiceNotePlayer.stop()
        audioEngine.stopCapture()
        viewModelScope.launch {
            repository.leaveVoiceRoom(communityId, currentUserId)
        }
    }

    fun toggleSelfMute(currentlyMuted: Boolean, hasAudioPermission: Boolean) {
        liveMicHasPermission = hasAudioPermission
        val nextMuted = !currentlyMuted
        viewModelScope.launch {
            repository.setSelfMuted(communityId, currentUserId, nextMuted)
            repository.setSelfSpeaking(communityId, currentUserId, !nextMuted)
        }
        if (nextMuted) {
            pausedLiveMicForVoiceNote = false
            audioEngine.stopCapture()
        } else if (!isRecordingVoiceNote.value) {
            audioEngine.startCapture(viewModelScope, hasAudioPermission)
        }
    }

    fun toggleHandRaised() {
        viewModelScope.launch {
            repository.toggleHandRaised(communityId, currentUserId)
        }
    }

    fun sendVoiceMessage(content: String) {
        viewModelScope.launch {
            repository.sendMessage(communityId, "VOICE", currentUserId, content)
        }
    }

    fun sendGroupMessage(content: String, replyToSnippet: String? = null) {
        viewModelScope.launch {
            repository.sendMessage(communityId, "GROUP", currentUserId, content, replyToSnippet)
        }
    }

    fun startVoiceNoteRecording(hasPermission: Boolean) {
        if (!hasPermission) {
            _roomNotice.value = "Concede el permiso de micrófono para enviar mensajes de audio."
            return
        }
        voiceNotePlayer.stop()
        if (audioEngine.isMicActive.value) {
            pausedLiveMicForVoiceNote = true
            liveMicHasPermission = hasPermission
            audioEngine.stopCapture()
        } else {
            pausedLiveMicForVoiceNote = false
        }
        voiceNoteRecorder.startRecording(viewModelScope).onFailure { err ->
            restoreLiveMicIfNeeded()
            _roomNotice.value = err.message ?: "No se pudo grabar la nota de voz."
        }
    }

    fun stopAndSendVoiceNote(channelType: String, replyToSnippet: String? = null) {
        val recorded = voiceNoteRecorder.stopAndFinalize()
        restoreLiveMicIfNeeded()
        recorded
            .onSuccess { audio ->
                viewModelScope.launch {
                    repository.sendAudioMessage(
                        communityId = communityId,
                        channelType = channelType,
                        senderId = currentUserId,
                        audioUri = audio.filePath,
                        durationMs = audio.durationMs,
                        replyToSnippet = replyToSnippet
                    ).onFailure { _roomNotice.value = it.message }
                }
            }
            .onFailure {
                _roomNotice.value = it.message
            }
    }

    fun cancelVoiceNoteRecording() {
        voiceNoteRecorder.cancelRecording()
        restoreLiveMicIfNeeded()
    }

    fun togglePlayAudioMessage(message: ChatMessage) {
        val path = message.audioUri ?: return
        voiceNotePlayer.togglePlayback(
            messageId = message.id,
            audioPath = path,
            scope = viewModelScope,
            onError = { _roomNotice.value = it }
        )
    }

    private fun restoreLiveMicIfNeeded() {
        if (pausedLiveMicForVoiceNote) {
            pausedLiveMicForVoiceNote = false
            audioEngine.startCapture(viewModelScope, liveMicHasPermission)
        }
    }

    fun reactToMessage(messageId: Long) {
        viewModelScope.launch {
            repository.reactToMessage(messageId)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.deleteMessage(communityId, currentUserId, message)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun moderateMute(targetUserId: String, mute: Boolean) {
        viewModelScope.launch {
            repository.moderateMuteParticipant(communityId, currentUserId, targetUserId, mute)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun kickParticipant(targetUserId: String) {
        viewModelScope.launch {
            repository.kickParticipant(communityId, currentUserId, targetUserId)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun setRole(targetUserId: String, newRole: CommunityRole) {
        viewModelScope.launch {
            repository.changeParticipantRole(communityId, currentUserId, targetUserId, newRole)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun toggleRoomLock(lock: Boolean) {
        viewModelScope.launch {
            repository.toggleRoomLock(communityId, currentUserId, lock)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun clearEvents() {
        viewModelScope.launch {
            repository.clearRoomEvents(communityId, currentUserId)
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun assignAppRank(targetUserId: String, newRank: AppRank) {
        viewModelScope.launch {
            repository.assignAppRank(currentUserId, targetUserId, newRank)
                .onSuccess {
                    _roomNotice.value = if (newRank == AppRank.NONE) {
                        "Verificado retirado."
                    } else {
                        "Entregado: ${newRank.badgeTitle}"
                    }
                }
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun banUser(targetUserId: String, reason: String) {
        viewModelScope.launch {
            repository.banUser(currentUserId, targetUserId, reason)
                .onSuccess { _roomNotice.value = "Usuario baneado permanentemente." }
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun unbanUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unbanUser(currentUserId, targetUserId)
                .onSuccess { _roomNotice.value = "Baneo retirado." }
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun updateRoomBackgroundFromUri(uri: Uri) {
        viewModelScope.launch {
            repository.copyPickedImageToInternalStorage(uri, "backgrounds", "room_bg")
                .onSuccess { path ->
                    repository.updateCommunityBackground(communityId, currentUserId, path)
                        .onSuccess { _roomNotice.value = "Fondo de la sala actualizado" }
                        .onFailure { _roomNotice.value = it.message }
                }
                .onFailure { _roomNotice.value = "No se pudo cargar la foto de fondo." }
        }
    }

    fun resetRoomBackground() {
        viewModelScope.launch {
            repository.updateCommunityBackground(communityId, currentUserId, null)
                .onSuccess { _roomNotice.value = "Fondo predeterminado restaurado" }
                .onFailure { _roomNotice.value = it.message }
        }
    }

    fun deleteCommunity(onDeleted: () -> Unit) {
        pausedLiveMicForVoiceNote = false
        voiceNoteRecorder.release()
        voiceNotePlayer.stop()
        audioEngine.stopCapture()
        viewModelScope.launch {
            repository.deleteCommunity(communityId, currentUserId)
                .onSuccess { onDeleted() }
                .onFailure { _roomNotice.value = it.message }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pausedLiveMicForVoiceNote = false
        voiceNoteRecorder.release()
        voiceNotePlayer.stop()
        audioEngine.stopCapture()
    }
}

class TxlViewModelFactory(
    private val repository: TxlRepository,
    private val currentUserId: String = "",
    private val communityId: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
        modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(repository, currentUserId) as T
        modelClass.isAssignableFrom(RoomViewModel::class.java) -> RoomViewModel(repository, communityId, currentUserId) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
