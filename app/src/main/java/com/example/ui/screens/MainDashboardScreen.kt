package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.data.DailyReport
import com.example.data.UiState
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.MemberAvatar
import com.example.ui.components.ProfileEditSheet
import com.example.util.ExportUtils
import com.example.viewmodel.BpsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: BpsViewModel,
    onSignOutComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val joinedTeamsState by viewModel.joinedTeamsState.collectAsStateWithLifecycle()
    val activeTeamState by viewModel.activeTeamState.collectAsStateWithLifecycle()
    val teamMembersState by viewModel.teamMembersState.collectAsStateWithLifecycle()
    val reportsState by viewModel.reportsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Laporan, 1=Rekap, 2=Chat
    var showTeamGate by remember { mutableStateOf(false) }
    var showReportEditor by remember { mutableStateOf(false) }
    var editingReport by remember { mutableStateOf<DailyReport?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showTeamCodeDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var previewPhotoBase64 by remember { mutableStateOf<String?>(null) }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val joinedTeams = (joinedTeamsState as? UiState.Success)?.data ?: emptyList()
    val activeTeam = (activeTeamState as? UiState.Success)?.data
    val teamMembers = (teamMembersState as? UiState.Success)?.data ?: emptyList()

    val myMemberRecord = remember(teamMembers, viewModel.currentUserId) {
        teamMembers.firstOrNull { it.userId == viewModel.currentUserId }
    }
    val myDisplayName = myMemberRecord?.displayName?.ifBlank { userProfile?.displayName }
        ?: userProfile?.displayName?.ifBlank { "Anggota BPS" }
        ?: "Anggota BPS"
    val myPhotoBase64 = myMemberRecord?.photoBase64 ?: userProfile?.photoBase64
    val isAdmin = activeTeam?.ownerId == viewModel.currentUserId || myMemberRecord?.role == "ADMIN"

    val performSignOut = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignOutComplete
        )
    }

    // Loading state while initial user profile loads
    if (userProfileState is UiState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Menghubungkan akun & tim...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    // Gate to TeamGateScreen if no active team or user explicitly requested switching/joining
    val hasActiveTeamId = !userProfile?.activeTeamId.isNullOrBlank()
    if (!hasActiveTeamId || showTeamGate || (activeTeamState is UiState.Success && activeTeam == null)) {
        Box(modifier = Modifier.fillMaxSize()) {
            TeamGateScreen(
                userProfile = userProfile,
                joinedTeams = joinedTeams,
                isBusy = isBusy,
                canNavigateBack = hasActiveTeamId && activeTeam != null,
                onBack = { showTeamGate = false },
                onJoinTeam = { code, name, photo ->
                    viewModel.joinTeam(code, name, photo) {
                        showTeamGate = false
                    }
                },
                onCreateTeam = { teamName, code, name, photo ->
                    viewModel.createTeam(teamName, code, name, photo) {
                        showTeamGate = false
                    }
                },
                onSelectJoinedTeam = { teamId ->
                    viewModel.switchActiveTeam(teamId) {
                        showTeamGate = false
                    }
                },
                onSignOut = performSignOut
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
        return
    }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    val team = activeTeam
    val teamId = team?.teamCode ?: userProfile?.activeTeamId.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LAPORAN HARIAN BPS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A))
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showTeamCodeDialog = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${team?.name ?: "Tim BPS"} • Kode: $teamId",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showTeamCodeDialog = true },
                            modifier = Modifier.testTag("team_code_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = "Info Kode Tim",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showProfileMenu = true },
                                modifier = Modifier.testTag("top_profile_menu_button")
                            ) {
                                MemberAvatar(
                                    displayName = myDisplayName,
                                    photoBase64 = myPhotoBase64,
                                    size = 34.dp
                                )
                            }
                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ubah Profil & Foto") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        showProfileMenu = false
                                        showProfileEditor = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ganti / Gabung Tim") },
                                    leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                    onClick = {
                                        showProfileMenu = false
                                        showTeamGate = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Keluar Akun", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        performSignOut()
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingReport = null
                            showReportEditor = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.btn_create_report)) },
                        modifier = Modifier.testTag("fab_create_report")
                    )
                }
            },
            bottomBar = {
                if (!isExpandedWidth) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    if (selectedTab == 0) Icons.Filled.Assignment else Icons.Outlined.Assignment,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_reports)) },
                            modifier = Modifier.testTag("nav_reports_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    if (selectedTab == 1) Icons.Filled.Summarize else Icons.Outlined.Summarize,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_summary)) },
                            modifier = Modifier.testTag("nav_summary_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    if (selectedTab == 2) Icons.Filled.Forum else Icons.Outlined.Forum,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_chat)) },
                            modifier = Modifier.testTag("nav_chat_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        NavigationRailItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    if (selectedTab == 0) Icons.Filled.Assignment else Icons.Outlined.Assignment,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_reports)) }
                        )
                        NavigationRailItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    if (selectedTab == 1) Icons.Filled.Summarize else Icons.Outlined.Summarize,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_summary)) }
                        )
                        NavigationRailItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    if (selectedTab == 2) Icons.Filled.Forum else Icons.Outlined.Forum,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.nav_chat)) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedTab) {
                        0 -> DailyReportsScreen(
                            reportsState = reportsState,
                            teamMembers = teamMembers,
                            currentUserId = viewModel.currentUserId,
                            isAdmin = isAdmin,
                            onCreateReportClick = {
                                editingReport = null
                                showReportEditor = true
                            },
                            onEditReportClick = { report ->
                                editingReport = report
                                showReportEditor = true
                            },
                            onDeleteReport = { reportId ->
                                viewModel.deleteDailyReport(teamId, reportId)
                            },
                            onPreviewPhoto = { b64 -> previewPhotoBase64 = b64 }
                        )

                        1 -> MonthlySummaryScreen(
                            team = team,
                            reportsState = reportsState,
                            teamMembers = teamMembers,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )

                        2 -> LiveChatScreen(
                            messagesState = messagesState,
                            teamMembers = teamMembers,
                            currentUserId = viewModel.currentUserId,
                            myDisplayName = myDisplayName,
                            myPhotoBase64 = myPhotoBase64,
                            isAdmin = isAdmin,
                            onEditProfileClick = { showProfileEditor = true },
                            onSendText = { txt ->
                                viewModel.sendTextMessage(teamId, myDisplayName, myPhotoBase64, txt)
                            },
                            onSendImage = { imgB64 ->
                                viewModel.sendImageMessage(teamId, myDisplayName, myPhotoBase64, imgB64)
                            },
                            onSendAudio = { audioB64, durSec ->
                                viewModel.sendAudioMessage(teamId, myDisplayName, myPhotoBase64, audioB64, durSec)
                            },
                            onDeleteMessage = { msgId ->
                                viewModel.deleteMessage(teamId, msgId)
                            },
                            onPreviewPhoto = { b64 -> previewPhotoBase64 = b64 },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showReportEditor) {
        ReportEditorSheet(
            defaultMemberName = myDisplayName,
            editingReport = editingReport,
            isBusy = isBusy,
            onDismiss = {
                showReportEditor = false
                editingReport = null
            },
            onSave = { memberName, dayName, dateLabel, timeLabel, yearMonth, dayOfMonth, millis, desc, photos ->
                viewModel.saveDailyReport(
                    teamId = teamId,
                    editingReportId = editingReport?.id,
                    memberName = memberName,
                    dayName = dayName,
                    dateLabel = dateLabel,
                    timeLabel = timeLabel,
                    yearMonth = yearMonth,
                    dayOfMonth = dayOfMonth,
                    reportTimestampMillis = millis,
                    description = desc,
                    photos = photos,
                    onSuccess = {
                        showReportEditor = false
                        editingReport = null
                    }
                )
            },
            onPreviewPhoto = { b64 -> previewPhotoBase64 = b64 },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showProfileEditor) {
        ProfileEditSheet(
            initialName = myDisplayName,
            initialPhotoBase64 = myPhotoBase64,
            isBusy = isBusy,
            onSave = { newName, newPhoto ->
                viewModel.updateProfile(teamId, newName, newPhoto) {
                    showProfileEditor = false
                }
            },
            onDismiss = { showProfileEditor = false },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showTeamCodeDialog) {
        AlertDialog(
            onDismissRequest = { showTeamCodeDialog = false },
            title = { Text(team?.name ?: "Tim BPS") },
            text = {
                Column {
                    Text(
                        text = "Perumahan Bumi Papan Selaras",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "KODE TIM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = teamId,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Anggota aktif: ${teamMembers.size} orang. Bagikan Kode Tim di atas agar rekan dapat bergabung dan melihat data secara real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val invite = "Ayo gabung ke Tim '${team?.name ?: "BPS"}' di aplikasi LAPORAN HARIAN BPS (Perumahan Bumi Papan Selaras).\nKode Tim: $teamId"
                        ExportUtils.shareToWhatsApp(context, invite)
                        showTeamCodeDialog = false
                    }
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bagikan WA")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ExportUtils.copyToClipboard(context, "Kode Tim BPS", teamId)
                        showTeamCodeDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Kode Tim '$teamId' disalin.") }
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Salin Kode")
                }
            }
        )
    }

    previewPhotoBase64?.let { photo ->
        FullScreenPhotoDialog(
            photoBase64 = photo,
            onDismiss = { previewPhotoBase64 = null }
        )
    }
}
