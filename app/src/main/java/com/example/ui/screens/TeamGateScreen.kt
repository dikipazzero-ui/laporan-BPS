package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.JoinedTeam
import com.example.data.UserProfile
import com.example.ui.components.MemberAvatar
import com.example.util.DateTimeUtils

@Composable
fun TeamGateScreen(
    userProfile: UserProfile?,
    joinedTeams: List<JoinedTeam>,
    isBusy: Boolean,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onJoinTeam: (teamCode: String, displayName: String, photoBase64: String?) -> Unit,
    onCreateTeam: (teamName: String, teamCode: String, displayName: String, photoBase64: String?) -> Unit,
    onSelectJoinedTeam: (teamId: String) -> Unit,
    onSignOut: () -> Unit
) {
    if (canNavigateBack) {
        BackHandler { onBack() }
    }

    var selectedMode by remember { mutableIntStateOf(0) } // 0 = Gabung, 1 = Buat Baru
    var joinCode by remember { mutableStateOf("") }
    var newTeamName by remember { mutableStateOf("Tim Lapangan BPS") }
    var newTeamCode by remember { mutableStateOf(DateTimeUtils.generateReadableTeamCode()) }
    var displayName by remember(userProfile?.displayName) {
        mutableStateOf(userProfile?.displayName?.ifBlank { "" } ?: "")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 580.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top bar with User Info & Sign Out
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canNavigateBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
                    MemberAvatar(
                        displayName = userProfile?.displayName ?: "Anggota",
                        photoBase64 = userProfile?.photoBase64,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userProfile?.displayName?.ifBlank { "Anggota BPS" } ?: "Anggota BPS",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!userProfile?.email.isNullOrBlank()) {
                            Text(
                                text = userProfile?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Keluar")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Akses Tim Perumahan BPS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Masukkan Kode Tim yang dibagikan rekan Anda atau buat tim baru untuk Perumahan Bumi Papan Selaras.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Mode switcher
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == 0,
                    onClick = { selectedMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                ) {
                    Text("Gabung Kode Tim")
                }
                SegmentedButton(
                    selected = selectedMode == 1,
                    onClick = { selectedMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                ) {
                    Text("Buat Tim Baru")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 60) displayName = it },
                        label = { Text("Nama Anda di Tim") },
                        placeholder = { Text("Contoh: Pak Andi / Keamanan") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_gate_display_name")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedMode == 0) {
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it.uppercase().take(20) },
                            label = { Text("Kode Tim") },
                            placeholder = { Text("Contoh: BPS-7K4M") },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_team_code_input")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onJoinTeam(joinCode, displayName, userProfile?.photoBase64)
                            },
                            enabled = !isBusy && joinCode.trim().length >= 4 && displayName.trim().isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("join_team_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Gabung ke Tim")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newTeamName,
                            onValueChange = { if (it.length <= 80) newTeamName = it },
                            label = { Text("Nama Tim / Divisi") },
                            placeholder = { Text("Contoh: Keamanan & Lapangan BPS") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_team_name_input")
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newTeamCode,
                            onValueChange = { newTeamCode = it.uppercase().take(20) },
                            label = { Text("Kode Tim Unik (4-20 karakter)") },
                            supportingText = { Text("Bagikan kode ini ke anggota agar bisa masuk bersama.") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { newTeamCode = DateTimeUtils.generateReadableTeamCode() }
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Acak kode tim")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_team_code_input")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onCreateTeam(
                                    newTeamName,
                                    newTeamCode,
                                    displayName,
                                    userProfile?.photoBase64
                                )
                            },
                            enabled = !isBusy &&
                                newTeamName.trim().length >= 2 &&
                                newTeamCode.trim().length >= 4 &&
                                displayName.trim().isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_team_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Buat Tim & Mulai")
                            }
                        }
                    }
                }
            }

            if (joinedTeams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Tim Anda Sebelumnya",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                joinedTeams.forEach { jt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isBusy) { onSelectJoinedTeam(jt.teamId) }
                            .testTag("joined_team_${jt.teamId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Groups,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = jt.teamName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Kode: ${jt.teamCode} • ${if (jt.role == "ADMIN") "Admin" else "Anggota"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Masuk",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
