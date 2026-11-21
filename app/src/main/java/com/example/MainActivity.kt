package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.AppRank
import com.example.model.AppSettings
import com.example.model.CommunityRole
import com.example.ui.components.GlassCard
import com.example.ui.components.LocalCustomFrames
import com.example.ui.components.TxlBackdrop
import com.example.ui.screens.DriftingBottleScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.GroupChatScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceRoomScreen
import com.example.ui.theme.DangerRose
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TXLsocialTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppContainer
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RoomViewModel
import com.example.viewmodel.TxlViewModelFactory

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_MAIN = "main"
private const val ROUTE_VOICE_ROOM = "voice_room/{communityId}"
private const val ROUTE_GROUP_CHAT = "group_chat/{communityId}"

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    EXPLORE("En Vivo", Icons.Filled.Explore, Icons.Outlined.Explore),
    FEED("Clips", Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed),
    BOTTLE("Botella", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    NOTIFICATIONS("Alertas", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
    SETTINGS("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext)
        setContent {
            TXLsocialTheme {
                TXLsocialRoot(container)
            }
        }
    }
}

@Composable
private fun TXLsocialRoot(container: AppContainer) {
    val authViewModel: AuthViewModel = viewModel(
        factory = TxlViewModelFactory(container.repository)
    )
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val hasAnyUser by authViewModel.hasAnyUser.collectAsStateWithLifecycle()
    val authError by authViewModel.authError.collectAsStateWithLifecycle()
    val isBusy by authViewModel.isBusy.collectAsStateWithLifecycle()

    if (currentUser == null) {
        val navController = rememberNavController()
        val isFirstRegistration = hasAnyUser == false
        NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
            composable(ROUTE_LOGIN) {
                LoginScreen(
                    errorMessage = authError,
                    isBusy = isBusy,
                    isFirstAccountPending = isFirstRegistration,
                    onLoginClick = { id, pwd -> authViewModel.login(id, pwd) },
                    onNavigateToRegister = {
                        authViewModel.clearError()
                        navController.navigate(ROUTE_REGISTER)
                    }
                )
            }
            composable(ROUTE_REGISTER) {
                RegisterScreen(
                    errorMessage = authError,
                    isBusy = isBusy,
                    isFirstAccountPending = isFirstRegistration,
                    onRegisterClick = { u, e, p, c, terms -> authViewModel.register(u, e, p, c, terms) },
                    onBackToLogin = {
                        authViewModel.clearError()
                        navController.popBackStack()
                    }
                )
            }
        }
    } else {
        val uid = currentUser!!.id
        key(uid) {
            AuthenticatedShell(container = container, currentUserId = uid)
        }
    }
}

@Composable
private fun AuthenticatedShell(
    container: AppContainer,
    currentUserId: String
) {
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = TxlViewModelFactory(
            repository = container.repository,
            currentUserId = currentUserId
        )
    )
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val user by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsers.collectAsStateWithLifecycle()
    val communities by mainViewModel.communities.collectAsStateWithLifecycle()
    val bottles by mainViewModel.driftingBottles.collectAsStateWithLifecycle()
    val notifications by mainViewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by mainViewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val customFrames by mainViewModel.customFrames.collectAsStateWithLifecycle()
    val posts by mainViewModel.posts.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBanner.collectAsStateWithLifecycle()
    val pickedBottle by mainViewModel.pickedBottle.collectAsStateWithLifecycle()

    val customFramesMap = remember(customFrames) { customFrames.associateBy { it.id } }

    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusBanner!!)
            mainViewModel.clearBanner()
        }
    }

    if (user?.isBanned == true) {
        BannedAccountLockoutScreen(
            username = user!!.username,
            banReason = user?.banReason,
            settings = settings,
            onLogout = { mainViewModel.logout() }
        )
        return
    }

    CompositionLocalProvider(LocalCustomFrames provides customFramesMap) {
        NavHost(navController = navController, startDestination = ROUTE_MAIN) {
            composable(ROUTE_MAIN) {
                var activeTab by rememberSaveable { mutableStateOf(MainTab.EXPLORE) }
                val myCommunities = remember(communities) { communities.filter { it.isJoined } }

                TxlBackdrop(
                    neonGlowEnabled = settings.neonGlowEnabled,
                    backgroundUri = settings.appBackgroundUri,
                    backgroundDim = settings.appBackgroundDim
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 600.dp
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar(
                                        containerColor = SurfaceDark.copy(alpha = 0.96f),
                                        modifier = Modifier.navigationBarsPadding()
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = activeTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { activeTab = tab },
                                                icon = {
                                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                                    if (tab == MainTab.NOTIFICATIONS && unreadCount > 0) {
                                                        BadgedBox(badge = {
                                                            Badge(containerColor = NeonPink) {
                                                                Text(unreadCount.toString(), color = TextPrimary)
                                                            }
                                                        }) {
                                                            Icon(icon, contentDescription = tab.label)
                                                        }
                                                    } else {
                                                        Icon(icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = tab.label,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Clip
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = NeonPink,
                                                    selectedTextColor = NeonPink,
                                                    unselectedIconColor = TextMuted,
                                                    unselectedTextColor = TextMuted,
                                                    indicatorColor = NeonPurple.copy(alpha = 0.25f)
                                                ),
                                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(innerPadding)
                            ) {
                                if (useRail) {
                                    NavigationRail(containerColor = SurfaceDark.copy(alpha = 0.95f)) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = activeTab == tab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { activeTab = tab },
                                                icon = {
                                                    Icon(
                                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.label
                                                    )
                                                },
                                                label = { Text(tab.label) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = NeonPink,
                                                    selectedTextColor = NeonPink,
                                                    unselectedIconColor = TextMuted,
                                                    indicatorColor = NeonPurple.copy(alpha = 0.25f)
                                                )
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    when (activeTab) {
                                        MainTab.EXPLORE -> ExploreScreen(
                                            communities = communities,
                                            allUsers = allUsers,
                                            currentUserId = currentUserId,
                                            currentUserRank = user?.appRank ?: AppRank.NONE,
                                            currentUserIsDeveloper = user?.isDeveloper == true,
                                            onPickBackgroundUri = { uri, onSaved ->
                                                mainViewModel.saveImageFromUri(uri, "backgrounds", "room_bg", onSaved)
                                            },
                                            onOpenVoiceRoom = { cid -> navController.navigate("voice_room/$cid") },
                                            onOpenGroupChat = { cid -> navController.navigate("group_chat/$cid") },
                                            onToggleJoin = { cid -> mainViewModel.toggleJoinCommunity(cid) },
                                            onCreateCommunity = { n, c, d, k, bgUri ->
                                                mainViewModel.createCommunity(n, c, d, k, bgUri) { cid ->
                                                    navController.navigate("voice_room/$cid")
                                                }
                                            },
                                            onDeleteCommunity = { cid -> mainViewModel.deleteCommunity(cid) },
                                            onAssignAppRank = { targetId, rank -> mainViewModel.assignAppRank(targetId, rank) },
                                            onBanUser = { targetId, reason -> mainViewModel.banUser(targetId, reason) },
                                            onUnbanUser = { targetId -> mainViewModel.unbanUser(targetId) }
                                        )
                                        MainTab.FEED -> FeedScreen(
                                            posts = posts,
                                            communities = communities,
                                            currentUserId = currentUserId,
                                            currentUserRank = user?.appRank ?: AppRank.NONE,
                                            currentUserIsDeveloper = user?.isDeveloper == true,
                                            observeComments = { postId -> mainViewModel.observeCommentsForPost(postId) },
                                            onPickPostImage = { uri, onSaved ->
                                                mainViewModel.saveImageFromUri(uri, "posts", "post_img", onSaved)
                                            },
                                            onCreatePost = { cid, content, imgPath, onDone ->
                                                mainViewModel.createPost(cid, content, imgPath, onDone)
                                            },
                                            onLikePost = { postId -> mainViewModel.likePost(postId) },
                                            onCommentOnPost = { postId, text, onSent ->
                                                mainViewModel.commentOnPost(postId, text, onSent)
                                            },
                                            onDeletePost = { postId -> mainViewModel.deletePost(postId) },
                                            onAssignAppRank = { targetId, rank -> mainViewModel.assignAppRank(targetId, rank) },
                                            onOpenCommunity = { cid -> navController.navigate("voice_room/$cid") },
                                            onBanUser = { targetId, reason -> mainViewModel.banUser(targetId, reason) },
                                            onUnbanUser = { targetId -> mainViewModel.unbanUser(targetId) }
                                        )
                                        MainTab.BOTTLE -> DriftingBottleScreen(
                                            activeBottles = bottles,
                                            pickedBottle = pickedBottle,
                                            onLaunchBottle = { mood, text -> mainViewModel.launchBottle(mood, text) {} },
                                            onPickBottle = { mainViewModel.pickRandomBottle() },
                                            onReplyToBottle = { id, reply -> mainViewModel.replyToBottle(id, reply) },
                                            onDismissPickedBottle = { mainViewModel.clearPickedBottle() }
                                        )
                                        MainTab.NOTIFICATIONS -> NotificationsScreen(
                                            notifications = notifications,
                                            onMarkRead = { id -> mainViewModel.markNotificationRead(id) },
                                            onMarkAllRead = { mainViewModel.markAllNotificationsRead() },
                                            onOpenCommunity = { cid -> navController.navigate("voice_room/$cid") }
                                        )
                                        MainTab.PROFILE -> ProfileScreen(
                                            user = user,
                                            allUsers = allUsers,
                                            myCommunities = myCommunities,
                                            customFrames = customFrames,
                                            onPickPhotoUri = { uri, onSaved -> mainViewModel.saveAvatarFromUri(uri, onSaved) },
                                            onSaveCustomization = { uname, bio, status, frameId, uri, preset, onDone ->
                                                mainViewModel.saveProfileCustomization(uname, bio, status, frameId, uri, preset, onDone)
                                            },
                                            onPublishPngFrame = { title, uri, onDone ->
                                                mainViewModel.publishPngFrame(title, uri, equipNow = true, onDone = onDone)
                                            },
                                            onPublishStudioFrame = { title, primaryHex, secondaryHex, style, thickness, sparkles, onDone ->
                                                mainViewModel.publishStudioFrame(title, primaryHex, secondaryHex, style, thickness, sparkles, equipNow = true, onDone = onDone)
                                            },
                                            onDeleteCustomFrame = { frameId -> mainViewModel.deleteCustomFrame(frameId) },
                                            onAssignAppRank = { targetId, rank -> mainViewModel.assignAppRank(targetId, rank) },
                                            onExploreCircles = { activeTab = MainTab.EXPLORE },
                                            onOpenCommunityVoice = { cid -> navController.navigate("voice_room/$cid") },
                                            onBanUser = { targetId, reason -> mainViewModel.banUser(targetId, reason) },
                                            onUnbanUser = { targetId -> mainViewModel.unbanUser(targetId) }
                                        )
                                        MainTab.SETTINGS -> SettingsScreen(
                                            user = user,
                                            allUsers = allUsers,
                                            settings = settings,
                                            onUpdateSettings = { mainViewModel.updateSettings(it) },
                                            onPickAppBackgroundUri = { uri ->
                                                mainViewModel.saveImageFromUri(uri, "backgrounds", "app_bg") { savedPath ->
                                                    mainViewModel.updateSettings { it.copy(appBackgroundUri = savedPath) }
                                                }
                                            },
                                            onResetAppBackground = {
                                                mainViewModel.updateSettings { it.copy(appBackgroundUri = null) }
                                            },
                                            onAssignAppRank = { targetId, rank -> mainViewModel.assignAppRank(targetId, rank) },
                                            onUnlockDeveloperMode = { code, ok, err -> mainViewModel.unlockDeveloperMode(code, ok, err) },
                                            onBanUser = { targetId, reason -> mainViewModel.banUser(targetId, reason) },
                                            onUnbanUser = { targetId -> mainViewModel.unbanUser(targetId) },
                                            onLogout = { mainViewModel.logout() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            composable(
                route = ROUTE_VOICE_ROOM,
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { entry ->
                val communityId = entry.arguments?.getString("communityId").orEmpty()
                val roomVm: RoomViewModel = viewModel(
                    key = "${currentUserId}_$communityId",
                    factory = TxlViewModelFactory(container.repository, currentUserId, communityId)
                )
                val roomUser by roomVm.currentUser.collectAsStateWithLifecycle()
                val community by roomVm.community.collectAsStateWithLifecycle()
                val participants by roomVm.voiceParticipants.collectAsStateWithLifecycle()
                val events by roomVm.roomEvents.collectAsStateWithLifecycle()
                val messages by roomVm.voiceChatMessages.collectAsStateWithLifecycle()
                val micAmp by roomVm.micAmplitude.collectAsStateWithLifecycle()
                val isRecordingVoiceNote by roomVm.isRecordingVoiceNote.collectAsStateWithLifecycle()
                val recordingElapsedMs by roomVm.recordingElapsedMs.collectAsStateWithLifecycle()
                val playingAudioMessageId by roomVm.playingAudioMessageId.collectAsStateWithLifecycle()
                val playingAudioProgress by roomVm.playingAudioProgress.collectAsStateWithLifecycle()
                val roomNotice by roomVm.roomNotice.collectAsStateWithLifecycle()

                LaunchedEffect(roomUser?.isBanned) {
                    if (roomUser?.isBanned == true) {
                        roomVm.leaveVoiceRoom()
                        navController.popBackStack(ROUTE_MAIN, inclusive = false)
                    }
                }

                VoiceRoomScreen(
                    community = community,
                    participants = participants,
                    events = events,
                    messages = messages,
                    currentUserId = currentUserId,
                    currentUserRank = roomUser?.appRank ?: AppRank.NONE,
                    currentUserIsDeveloper = roomUser?.isDeveloper == true,
                    micAmplitude = micAmp,
                    settings = settings,
                    roomNotice = roomNotice,
                    onClearNotice = { roomVm.clearNotice() },
                    onEnterRoom = { roomVm.enterVoiceRoom() },
                    onLeaveRoom = { roomVm.leaveVoiceRoom() },
                    onToggleMute = { muted, perm -> roomVm.toggleSelfMute(muted, perm) },
                    onToggleHandRaised = { roomVm.toggleHandRaised() },
                    onSendMessage = { roomVm.sendVoiceMessage(it) },
                    onDeleteMessage = { roomVm.deleteMessage(it) },
                    onModerateMute = { target, mute -> roomVm.moderateMute(target, mute) },
                    onKickParticipant = { target -> roomVm.kickParticipant(target) },
                    onChangeRole = { target, role -> roomVm.setRole(target, role) },
                    onAssignAppRank = { target, rank -> roomVm.assignAppRank(target, rank) },
                    onToggleRoomLock = { lock -> roomVm.toggleRoomLock(lock) },
                    onClearEvents = { roomVm.clearEvents() },
                    onUpdateRoomBackground = { uri -> roomVm.updateRoomBackgroundFromUri(uri) },
                    onResetRoomBackground = { roomVm.resetRoomBackground() },
                    onDeleteRoom = {
                        roomVm.deleteCommunity {
                            navController.popBackStack(ROUTE_MAIN, inclusive = false)
                        }
                    },
                    onOpenGroupChat = { navController.navigate("group_chat/$communityId") },
                    onBack = { navController.popBackStack() },
                    isRecordingVoiceNote = isRecordingVoiceNote,
                    recordingElapsedMs = recordingElapsedMs,
                    playingAudioMessageId = playingAudioMessageId,
                    playingAudioProgress = playingAudioProgress,
                    onStartVoiceNote = { hasPerm -> roomVm.startVoiceNoteRecording(hasPerm) },
                    onStopAndSendVoiceNote = { roomVm.stopAndSendVoiceNote("VOICE") },
                    onCancelVoiceNote = { roomVm.cancelVoiceNoteRecording() },
                    onTogglePlayAudio = { msg -> roomVm.togglePlayAudioMessage(msg) },
                    onBanUser = { targetId, reason -> roomVm.banUser(targetId, reason) },
                    onUnbanUser = { targetId -> roomVm.unbanUser(targetId) }
                )
            }

            composable(
                route = ROUTE_GROUP_CHAT,
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { entry ->
                val communityId = entry.arguments?.getString("communityId").orEmpty()
                val roomVm: RoomViewModel = viewModel(
                    key = "${currentUserId}_chat_$communityId",
                    factory = TxlViewModelFactory(container.repository, currentUserId, communityId)
                )
                val roomUser by roomVm.currentUser.collectAsStateWithLifecycle()
                val community by roomVm.community.collectAsStateWithLifecycle()
                val messages by roomVm.groupChatMessages.collectAsStateWithLifecycle()
                val isRecordingVoiceNote by roomVm.isRecordingVoiceNote.collectAsStateWithLifecycle()
                val recordingElapsedMs by roomVm.recordingElapsedMs.collectAsStateWithLifecycle()
                val playingAudioMessageId by roomVm.playingAudioMessageId.collectAsStateWithLifecycle()
                val playingAudioProgress by roomVm.playingAudioProgress.collectAsStateWithLifecycle()
                val roomNotice by roomVm.roomNotice.collectAsStateWithLifecycle()

                LaunchedEffect(roomUser?.isBanned) {
                    if (roomUser?.isBanned == true) {
                        navController.popBackStack(ROUTE_MAIN, inclusive = false)
                    }
                }

                GroupChatScreen(
                    community = community,
                    messages = messages,
                    currentUserId = currentUserId,
                    currentUserRank = roomUser?.appRank ?: AppRank.NONE,
                    currentUserIsDeveloper = roomUser?.isDeveloper == true,
                    settings = settings,
                    roomNotice = roomNotice,
                    onClearNotice = { roomVm.clearNotice() },
                    onSendMessage = { text, reply -> roomVm.sendGroupMessage(text, reply) },
                    onReactMessage = { id -> roomVm.reactToMessage(id) },
                    onDeleteMessage = { msg -> roomVm.deleteMessage(msg) },
                    onPromoteCoAdmin = { uid -> roomVm.setRole(uid, CommunityRole.CO_ADMIN) },
                    onDemoteMember = { uid -> roomVm.setRole(uid, CommunityRole.MEMBER) },
                    onAssignAppRank = { target, rank -> roomVm.assignAppRank(target, rank) },
                    onUpdateRoomBackground = { uri -> roomVm.updateRoomBackgroundFromUri(uri) },
                    onResetRoomBackground = { roomVm.resetRoomBackground() },
                    onDeleteRoom = {
                        roomVm.deleteCommunity {
                            navController.popBackStack(ROUTE_MAIN, inclusive = false)
                        }
                    },
                    onOpenVoiceRoom = { navController.navigate("voice_room/$communityId") },
                    onBack = { navController.popBackStack() },
                    isRecordingVoiceNote = isRecordingVoiceNote,
                    recordingElapsedMs = recordingElapsedMs,
                    playingAudioMessageId = playingAudioMessageId,
                    playingAudioProgress = playingAudioProgress,
                    onStartVoiceNote = { hasPerm -> roomVm.startVoiceNoteRecording(hasPerm) },
                    onStopAndSendVoiceNote = { reply -> roomVm.stopAndSendVoiceNote("GROUP", reply) },
                    onCancelVoiceNote = { roomVm.cancelVoiceNoteRecording() },
                    onTogglePlayAudio = { msg -> roomVm.togglePlayAudioMessage(msg) },
                    onBanUser = { targetId, reason -> roomVm.banUser(targetId, reason) },
                    onUnbanUser = { targetId -> roomVm.unbanUser(targetId) }
                )
            }
        }
    }
}

@Composable
private fun BannedAccountLockoutScreen(
    username: String,
    banReason: String?,
    settings: AppSettings,
    onLogout: () -> Unit
) {
    BackHandler(enabled = true) {}
    TxlBackdrop(
        neonGlowEnabled = settings.neonGlowEnabled,
        backgroundUri = settings.appBackgroundUri,
        backgroundDim = settings.appBackgroundDim
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DangerRose.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, DangerRose),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Gavel,
                                contentDescription = "Cuenta suspendida",
                                tint = DangerRose,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cuenta suspendida permanentemente",
                        style = MaterialTheme.typography.titleLarge,
                        color = DangerRose,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "@$username fue bloqueado por el desarrollador y no puede volver a usar NexoLive hasta que se retire el baneo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DangerRose.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DangerRose.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = DangerRose, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Motivo: ${banReason?.takeIf { it.isNotBlank() } ?: "Actividad ilegal o incumplimiento grave"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRose),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("banned_logout_button")
                    ) {
                        Text("Cerrar sesión", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
