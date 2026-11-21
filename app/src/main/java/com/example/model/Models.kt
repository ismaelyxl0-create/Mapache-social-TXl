package com.example.model

enum class CommunityRole(val label: String, val priority: Int) {
    ADMIN("Admin", 3),
    CO_ADMIN("Co-Admin", 2),
    MEMBER("Miembro", 1);

    companion object {
        fun fromRaw(raw: String): CommunityRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEMBER
    }
}

enum class AvatarFrame(
    val id: String,
    val displayName: String,
    val description: String
) {
    NONE("none", "Sin marco", "Estilo minimalista limpio"),
    NEON_PULSE("neon_pulse", "Pulso Neón", "Aro degradado #9D4EDD y #FF4EC2"),
    CYBER_PRISM("cyber_prism", "Prisma Cyber", "Segmentos futuristas cian y violeta"),
    STARLIGHT_HALO("starlight_halo", "Halo Estelar", "Órbita luminosa con destellos"),
    VOID_FLAME("void_flame", "Llama Astral", "Aura eléctrica giratoria"),
    ROYAL_CROWN("royal_crown", "Cresta Real", "Resplandor dorado y magenta");

    companion object {
        fun fromId(id: String): AvatarFrame =
            entries.firstOrNull { it.id == id } ?: NEON_PULSE
    }
}

enum class CustomFrameStyle(val id: String, val displayName: String) {
    RING_GLOW("ring_glow", "Aro Neón"),
    DOUBLE_ORBIT("double_orbit", "Doble Órbita"),
    NEON_DASH("neon_dash", "Segmentos"),
    STAR_BURST("star_burst", "Destello"),
    CROWN_SPIKES("crown_spikes", "Corona"),
    CYBER_HEX("cyber_hex", "Hexágono");

    companion object {
        fun fromId(id: String): CustomFrameStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RING_GLOW
    }
}

data class CustomFrame(
    val id: String,
    val creatorId: String,
    val creatorName: String,
    val title: String,
    val frameType: String, // "PNG" or "STUDIO"
    val pngPath: String? = null,
    val primaryColorHex: Long = 0xFF9D4EDD,
    val secondaryColorHex: Long = 0xFFFF4EC2,
    val stylePattern: CustomFrameStyle = CustomFrameStyle.RING_GLOW,
    val thicknessDp: Float = 4f,
    val hasSparkles: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class AppRank(
    val id: String,
    val label: String,
    val badgeTitle: String
) {
    NONE("none", "Miembro", "Sin verificado"),
    OWNER("owner", "Dueño", "Verificado Negro • Dueño"),
    MODERATOR("moderator", "Moderador", "Verificado Rojo • Moderador"),
    INFLUENCER("influencer", "Influencer", "Verificado Azul • Influencer");

    companion object {
        fun fromId(id: String): AppRank =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class NotificationCategory(val label: String) {
    FOLLOW("Seguimiento"),
    MENTION("Mención"),
    COMMUNITY("Comunidad"),
    DRIFTING_BOTTLE("Drifting Bottle");

    companion object {
        fun fromRaw(raw: String): NotificationCategory =
            entries.firstOrNull { it.name == raw } ?: COMMUNITY
    }
}

data class UserProfile(
    val id: String,
    val username: String,
    val email: String,
    val bio: String,
    val customStatus: String,
    val avatarFrame: AvatarFrame,
    val avatarUri: String?,
    val presetAvatarKey: String,
    val communitiesCount: Int,
    val followersCount: Int,
    val postsCount: Int,
    val appRank: AppRank = AppRank.NONE,
    val avatarFrameId: String = avatarFrame.id,
    val isDeveloper: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String? = null
) {
    val isVerified: Boolean get() = appRank != AppRank.NONE
    val canManageVerifications: Boolean get() = isDeveloper || appRank == AppRank.OWNER
    val canBanUsers: Boolean get() = isDeveloper || appRank == AppRank.OWNER
}

data class Community(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val coverKey: String,
    val badgeLabel: String, // "popular" or "nueva"
    val memberCount: Int,
    val activeVoiceCount: Int,
    val isVoiceLocked: Boolean = false,
    val myRole: CommunityRole? = null,
    val isJoined: Boolean = false,
    val backgroundUri: String? = null
)

data class VoiceParticipant(
    val id: String,
    val communityId: String,
    val user: UserProfile,
    val role: CommunityRole,
    val isMuted: Boolean,
    val isSpeaking: Boolean,
    val isHandRaised: Boolean
)

data class RoomEventLog(
    val id: Long,
    val communityId: String,
    val actorName: String,
    val description: String,
    val timestampMillis: Long
)

data class ChatMessage(
    val id: Long,
    val communityId: String,
    val channelType: String, // "VOICE" or "GROUP"
    val sender: UserProfile,
    val senderRole: CommunityRole,
    val content: String,
    val replyToSnippet: String? = null,
    val reactionCount: Int = 0,
    val timestampMillis: Long,
    val audioUri: String? = null,
    val audioDurationMs: Long = 0L
) {
    val isAudioMessage: Boolean get() = !audioUri.isNullOrBlank()
}

data class DriftingBottle(
    val id: String,
    val authorId: String,
    val anonymousAlias: String,
    val moodTag: String,
    val content: String,
    val replyContent: String?,
    val isPickedByMe: Boolean,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)

data class AppNotification(
    val id: Long,
    val userId: String,
    val actorName: String,
    val actorAvatarKey: String,
    val actorAvatarUri: String?,
    val actorFrame: AvatarFrame,
    val title: String,
    val subtitle: String,
    val category: NotificationCategory,
    val targetCommunityId: String?,
    val isRead: Boolean,
    val timestampMillis: Long
)

data class AppSettings(
    val neonGlowEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val showStatusInChat: Boolean = true,
    val compactVoiceGrid: Boolean = false,
    val privateProfileMode: Boolean = false,
    val languageLabel: String = "Español (ES)",
    val appBackgroundUri: String? = null,
    val appBackgroundDim: Float = 0.62f
)

data class CommunityPost(
    val id: String,
    val author: UserProfile,
    val communityId: String? = null,
    val communityName: String? = null,
    val content: String,
    val imageUri: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class PostComment(
    val id: Long,
    val postId: String,
    val author: UserProfile,
    val content: String,
    val createdAtMillis: Long
)
