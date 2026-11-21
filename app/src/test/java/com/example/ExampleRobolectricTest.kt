package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.TxlRepository
import com.example.data.local.PreferencesStore
import com.example.data.local.TxlDatabase
import com.example.model.AppRank
import com.example.model.AvatarFrame
import com.example.model.CustomFrameStyle
import com.example.model.UserProfile
import com.example.ui.screens.ExploreScreen
import com.example.ui.theme.TXLsocialTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: TxlDatabase
    private lateinit var repo: TxlRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TxlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TxlRepository(context, db, PreferencesStore(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("NexoLive", appName)
    }

    @Test
    fun `no bots or fake rooms exist initially and only primary owner email becomes owner`() = runTest {
        repo.initializeSeedData()

        val initialSnapshot = repo.getWebSnapshot(null, null)
        assertTrue("No debe haber bots al iniciar", initialSnapshot.allUsers.isEmpty())
        assertTrue("No debe haber salas falsas al iniciar", initialSnapshot.communities.isEmpty())
        assertTrue("No debe haber botellas falsas al iniciar", initialSnapshot.bottles.isEmpty())

        // First account on another device with non-primary email must NOT be owner
        val firstNonOwnerRes = repo.registerWithoutSessionChange("OtroDispositivo", "otro@txl.test", "123456")
        assertTrue(firstNonOwnerRes.isSuccess)
        val member = firstNonOwnerRes.getOrThrow()
        assertEquals(AppRank.NONE, member.appRank)
        assertTrue(!member.isDeveloper)
        assertTrue(!member.canManageVerifications)

        // Primary registered email gets Owner + Developer even if registered afterward
        val ownerRes = repo.registerWithoutSessionChange("AdminReal", TxlRepository.PRIMARY_OWNER_EMAIL, "123456")
        assertTrue(ownerRes.isSuccess)
        val owner = ownerRes.getOrThrow()
        assertEquals(AppRank.OWNER, owner.appRank)
        assertTrue(owner.isDeveloper)

        val grantRes = repo.assignAppRank(owner.id, member.id, AppRank.MODERATOR)
        assertTrue(grantRes.isSuccess)

        val unauthorizedGrant = repo.assignAppRank(member.id, owner.id, AppRank.NONE)
        assertTrue(unauthorizedGrant.isFailure)

        val updatedMember = repo.getUserById(member.id)
        assertEquals(AppRank.MODERATOR, updatedMember?.appRank)

        val notifsForMember = repo.observeNotifications(member.id).first()
        assertTrue(notifsForMember.none { it.title.contains("Moderador", ignoreCase = true) })
    }

    @Test
    fun `rooms can be customized and deleted and studio frames plus posts work end to end`() = runTest {
        val owner = repo.registerWithoutSessionChange("CreadorUnico", "creador@txl.test", "secret123").getOrThrow()
        val guest = repo.registerWithoutSessionChange("InvitadoReal", "invitado@txl.test", "secret123").getOrThrow()

        // Studio frame creation & equip
        val frameRes = repo.publishStudioFrame(
            creatorId = guest.id,
            title = "Galaxia Libre",
            primaryColorHex = 0xFF9D4EDD,
            secondaryColorHex = 0xFFFF4EC2,
            stylePattern = CustomFrameStyle.DOUBLE_ORBIT,
            thicknessDp = 5f,
            hasSparkles = true
        )
        assertTrue(frameRes.isSuccess)
        val frame = frameRes.getOrThrow()
        assertEquals("STUDIO", frame.frameType)

        repo.updateProfileCustomization(
            userId = guest.id,
            username = guest.username,
            bio = "Hola TXL",
            customStatus = "En linea",
            frameId = frame.id,
            avatarUri = null,
            presetAvatarKey = "gaming"
        )
        assertEquals(frame.id, repo.getUserById(guest.id)?.avatarFrameId)

        // Community creation, background customization, post tagging, and room deletion
        val cid = repo.createCommunity(
            creatorId = guest.id,
            name = "Sala Cyber",
            category = "Tech",
            description = "Sala de prueba real",
            coverKey = "gaming",
            backgroundUri = "/tmp/custom_bg.jpg"
        ).getOrThrow()

        val createdComm = repo.getCommunityById(cid)
        assertEquals("/tmp/custom_bg.jpg", createdComm?.backgroundUri)

        // Another normal member shouldn't be able to delete guest's room, but guest (Admin) can
        val post = repo.createPost(guest.id, cid, "Hola comunidad TXLsocial!", null).getOrThrow()
        repo.likePost(post.id)
        assertTrue(repo.addPostComment(post.id, owner.id, "Excelente post").isSuccess)

        val feedPosts = repo.observePosts().first()
        val targetPost = feedPosts.firstOrNull { it.id == post.id }
        assertNotNull(targetPost)
        assertEquals(1, targetPost?.likesCount)
        assertEquals(1, targetPost?.commentsCount)

        // Delete community and verify cascading cleanup
        assertTrue(repo.deleteCommunity(cid, guest.id).isSuccess)
        assertNull(repo.getCommunityById(cid))

        // Post remains in general feed with cleared community link, and can be deleted
        val postAfterRoomDel = repo.observePosts().first().firstOrNull { it.id == post.id }
        assertNotNull(postAfterRoomDel)
        assertNull(postAfterRoomDel?.communityId)
        assertTrue(repo.deletePost(post.id, guest.id).isSuccess)
        assertTrue(repo.observePosts().first().none { it.id == post.id })
    }

    @Test
    fun `developer keeps verification permissions even after changing own badge and can unlock on secondary account`() = runTest {
        val dev = repo.registerWithoutSessionChange("DevOwner", TxlRepository.PRIMARY_OWNER_EMAIL, "123456").getOrThrow()
        val secondary = repo.registerWithoutSessionChange("CuentaDos", "dos@txl.test", "123456").getOrThrow()

        assertTrue(dev.isDeveloper)
        assertTrue(dev.canManageVerifications)
        assertTrue(!secondary.isDeveloper)
        assertTrue(!secondary.canManageVerifications)

        // Developer changes their own badge away from OWNER to MODERATOR, INFLUENCER, NONE, then back to OWNER
        assertTrue(repo.assignAppRank(dev.id, dev.id, AppRank.MODERATOR).isSuccess)
        assertEquals(AppRank.MODERATOR, repo.getUserById(dev.id)?.appRank)
        assertTrue(repo.getUserById(dev.id)?.canManageVerifications == true)

        assertTrue(repo.assignAppRank(dev.id, dev.id, AppRank.NONE).isSuccess)
        assertEquals(AppRank.NONE, repo.getUserById(dev.id)?.appRank)
        assertTrue(repo.getUserById(dev.id)?.canManageVerifications == true)

        // Developer with NONE badge can still grant Influencer to another user and Owner back to self
        assertTrue(repo.assignAppRank(dev.id, secondary.id, AppRank.INFLUENCER).isSuccess)
        assertEquals(AppRank.INFLUENCER, repo.getUserById(secondary.id)?.appRank)
        assertTrue(repo.assignAppRank(dev.id, dev.id, AppRank.OWNER).isSuccess)
        assertEquals(AppRank.OWNER, repo.getUserById(dev.id)?.appRank)

        // Non-developer cannot assign ranks
        assertTrue(repo.assignAppRank(secondary.id, dev.id, AppRank.NONE).isFailure)

        // Wrong code fails, TXL2026 unlocks developer mode on secondary account
        assertTrue(repo.unlockDeveloperMode(secondary.id, "WRONG").isFailure)
        assertTrue(repo.unlockDeveloperMode(secondary.id, "TXL2026").isSuccess)
        val unlockedSecondary = repo.getUserById(secondary.id)
        assertTrue(unlockedSecondary?.isDeveloper == true)
        assertTrue(unlockedSecondary?.canManageVerifications == true)
        assertTrue(repo.assignAppRank(secondary.id, secondary.id, AppRank.MODERATOR).isSuccess)
        assertEquals(AppRank.MODERATOR, repo.getUserById(secondary.id)?.appRank)
    }

    @Test
    fun `explore user search filters registered users and opens profile sheet`() {
        val alice = UserProfile(
            id = "u_alice",
            username = "LuciaNeon",
            email = "lucia@txl.test",
            bio = "Creadora de arte synthwave",
            customStatus = "Escuchando beats",
            avatarFrame = AvatarFrame.NEON_PULSE,
            avatarFrameId = AvatarFrame.NEON_PULSE.id,
            avatarUri = null,
            presetAvatarKey = "anime",
            communitiesCount = 2,
            followersCount = 14,
            postsCount = 3,
            appRank = AppRank.INFLUENCER,
            isDeveloper = false
        )
        val bob = UserProfile(
            id = "u_bob",
            username = "CarlosGamer",
            email = "carlos@txl.test",
            bio = "Torneos nocturnos",
            customStatus = "En partida",
            avatarFrame = AvatarFrame.CYBER_PRISM,
            avatarFrameId = AvatarFrame.CYBER_PRISM.id,
            avatarUri = null,
            presetAvatarKey = "gaming",
            communitiesCount = 1,
            followersCount = 5,
            postsCount = 1,
            appRank = AppRank.NONE,
            isDeveloper = false
        )

        composeRule.setContent {
            TXLsocialTheme {
                ExploreScreen(
                    communities = emptyList(),
                    allUsers = listOf(alice, bob),
                    currentUserId = bob.id,
                    currentUserRank = AppRank.NONE,
                    currentUserIsDeveloper = false,
                    onOpenVoiceRoom = {},
                    onOpenGroupChat = {},
                    onToggleJoin = {},
                    onCreateCommunity = { _, _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("explore_user_search_input").performTextInput("Lucia")
        composeRule.onNodeWithTag("explore_user_card_u_alice").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_user_card_u_bob").assertDoesNotExist()

        composeRule.onNodeWithTag("explore_user_card_u_alice").performClick()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Creadora de arte synthwave").assertExists()
    }

    @Test
    fun `group chat component renders stylized dark bubbles and sends messages`() {
        val streamer = UserProfile(
            id = "u_streamer",
            username = "KuroIRL",
            email = "kuro@nexolive.test",
            bio = "IRL Tokio",
            customStatus = "En vivo desde Shibuya",
            avatarFrame = AvatarFrame.NEON_PULSE,
            avatarFrameId = AvatarFrame.NEON_PULSE.id,
            avatarUri = null,
            presetAvatarKey = "irl",
            communitiesCount = 1,
            followersCount = 1200,
            postsCount = 4,
            appRank = AppRank.INFLUENCER
        )
        val viewer = UserProfile(
            id = "u_viewer",
            username = "MaxGamer",
            email = "max@nexolive.test",
            bio = "FPS player",
            customStatus = "Ranked",
            avatarFrame = AvatarFrame.CYBER_PRISM,
            avatarFrameId = AvatarFrame.CYBER_PRISM.id,
            avatarUri = null,
            presetAvatarKey = "fps",
            communitiesCount = 1,
            followersCount = 24,
            postsCount = 1,
            appRank = AppRank.NONE
        )
        val initialMessage = com.example.model.ChatMessage(
            id = 101L,
            communityId = "c_1",
            channelType = "GROUP",
            sender = streamer,
            senderRole = com.example.model.CommunityRole.ADMIN,
            content = "¡Bienvenidos al directo IRL!",
            reactionCount = 4,
            timestampMillis = System.currentTimeMillis()
        )
        var sentText: String? = null

        composeRule.setContent {
            TXLsocialTheme {
                val inputState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                com.example.ui.components.GroupChatPanel(
                    messages = listOf(initialMessage),
                    currentUserId = viewer.id,
                    myRole = com.example.model.CommunityRole.MEMBER,
                    showUserStatus = true,
                    inputText = inputState.value,
                    onInputTextChange = { inputState.value = it },
                    replyingTo = null,
                    onSetReplyingTo = {},
                    onSendMessage = { content, _ -> sentText = content },
                    onReactMessage = {},
                    onDeleteMessage = {},
                    onSelectUser = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("chat_bubble_101").assertIsDisplayed()
        composeRule.onNodeWithText("¡Bienvenidos al directo IRL!").assertIsDisplayed()
        composeRule.onNodeWithTag("group_chat_input").performTextInput("GG buen directo!")
        composeRule.onNodeWithTag("group_chat_send_button").performClick()
        assertEquals("GG buen directo!", sentText)
    }

    @Test
    fun `audio messages can be saved, sent, observed, and cleaned up on deletion`() = runTest {
        val dev = repo.registerWithoutSessionChange("AudioDev", TxlRepository.PRIMARY_OWNER_EMAIL, "123456").getOrThrow()
        val cid = repo.createCommunity(dev.id, "Sala Audio", "Música", "Notas de voz", "music", null).getOrThrow()

        val fakeAudioDataUrl = "data:audio/webm;base64," + Base64.encodeToString("TXL_FAKE_AUDIO_BYTES".toByteArray(), Base64.NO_WRAP)
        val audioPath = repo.saveBase64AudioToInternalStorage(fakeAudioDataUrl).getOrThrow()
        assertTrue(java.io.File(audioPath).exists())

        val sendRes = repo.sendAudioMessage(
            communityId = cid,
            channelType = "VOICE",
            senderId = dev.id,
            audioUri = audioPath,
            durationMs = 5400L
        )
        assertTrue(sendRes.isSuccess)

        val voiceMessages = repo.observeMessages(cid, "VOICE").first()
        assertEquals(1, voiceMessages.size)
        val msg = voiceMessages.first()
        assertTrue(msg.isAudioMessage)
        assertEquals(5400L, msg.audioDurationMs)
        assertEquals(audioPath, msg.audioUri)
        assertNotNull(repo.getMessageById(msg.id))

        assertTrue(repo.deleteMessageById(cid, dev.id, msg.id).isSuccess)
        assertNull(repo.getMessageById(msg.id))
        assertFalse(java.io.File(audioPath).exists())
    }

    @Test
    fun `only developer or owner can ban and unban users and banned accounts lose all access until unbanned`() = runTest {
        val owner = repo.registerWithoutSessionChange("OwnerPrincipal", TxlRepository.PRIMARY_OWNER_EMAIL, "123456").getOrThrow()
        val normalA = repo.registerWithoutSessionChange("Infractor", "infractor@txl.test", "123456").getOrThrow()
        val normalB = repo.registerWithoutSessionChange("OtroUsuario", "otro2@txl.test", "123456").getOrThrow()

        // Non-owner cannot ban anyone
        assertTrue(repo.banUser(normalB.id, normalA.id, "Spam").isFailure)

        // Owner cannot be banned even by self
        assertTrue(repo.banUser(owner.id, owner.id, "Auto ban").isFailure)

        // Put infractor inside a voice room first
        val cid = repo.createCommunity(owner.id, "Sala Principal", "Chat", "Bienvenidos", "anime", null).getOrThrow()
        repo.joinVoiceRoom(cid, normalA.id)
        assertTrue(repo.observeVoiceParticipants(cid).first().any { it.user.id == normalA.id })

        // Owner bans infractor for illegal activity
        val banRes = repo.banUser(owner.id, normalA.id, "Contenido ilegal prohibido")
        assertTrue(banRes.isSuccess)

        val bannedProfile = repo.getUserById(normalA.id)
        assertTrue(bannedProfile?.isBanned == true)
        assertEquals("Contenido ilegal prohibido", bannedProfile?.banReason)

        // Banned user was removed from voice rooms and blocked from login/registration/writes
        assertTrue(repo.observeVoiceParticipants(cid).first().none { it.user.id == normalA.id })
        assertTrue(repo.loginWithoutSessionChange("infractor@txl.test", "123456").isFailure)
        assertTrue(repo.registerWithoutSessionChange("NuevaCuenta", "infractor@txl.test", "123456").isFailure)
        assertTrue(repo.registerWithoutSessionChange("Infractor", "nuevo@txl.test", "123456").isFailure)
        assertTrue(repo.createPost(normalA.id, cid, "Intentando publicar", null).isFailure)
        assertTrue(repo.launchDriftingBottle(normalA.id, "Mood", "Intentando botella").isFailure)

        // Non-owner cannot unban
        assertTrue(repo.unbanUser(normalB.id, normalA.id).isFailure)

        // Owner unbans -> user can log in and use the app again
        assertTrue(repo.unbanUser(owner.id, normalA.id).isSuccess)
        val unbanned = repo.getUserById(normalA.id)
        assertFalse(unbanned?.isBanned == true)
        assertNull(unbanned?.banReason)
        assertTrue(repo.loginWithoutSessionChange("infractor@txl.test", "123456").isSuccess)
        assertTrue(repo.createPost(normalA.id, cid, "De regreso respetando las normas", null).isSuccess)
    }
}
