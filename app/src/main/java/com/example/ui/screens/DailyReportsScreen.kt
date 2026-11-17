package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DailyReport
import com.example.data.TeamMember
import com.example.data.UiState
import com.example.ui.components.MemberAvatar
import com.example.util.DateTimeUtils
import com.example.util.MediaUtils

@Composable
fun DailyReportsScreen(
    reportsState: UiState<List<DailyReport>>,
    teamMembers: List<TeamMember>,
    currentUserId: String,
    isAdmin: Boolean,
    onCreateReportClick: () -> Unit,
    onEditReportClick: (DailyReport) -> Unit,
    onDeleteReport: (String) -> Unit,
    onPreviewPhoto: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=Semua, 1=Hari Ini, 2=Saya
    var reportToDelete by remember { mutableStateOf<DailyReport?>(null) }

    val memberPhotoMap = remember(teamMembers) {
        teamMembers.associate { it.userId to it.photoBase64 }
    }
    val todayParts = remember { DateTimeUtils.extractReportDateParts() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama anggota atau keterangan...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_reports_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = filterTab == 0,
                onClick = { filterTab = 0 },
                label = { Text("Semua") }
            )
            FilterChip(
                selected = filterTab == 1,
                onClick = { filterTab = 1 },
                label = { Text("Hari Ini") }
            )
            FilterChip(
                selected = filterTab == 2,
                onClick = { filterTab = 2 },
                label = { Text("Laporan Saya") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (reportsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = reportsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is UiState.Success -> {
                val allReports = reportsState.data
                val filtered = remember(allReports, searchQuery, filterTab, currentUserId, todayParts) {
                    allReports.filter { r ->
                        val matchesTab = when (filterTab) {
                            1 -> r.yearMonth == todayParts.yearMonth && r.dayOfMonth.toInt() == todayParts.dayOfMonth
                            2 -> r.authorId == currentUserId
                            else -> true
                        }
                        val q = searchQuery.trim().lowercase()
                        val matchesSearch = q.isEmpty() ||
                            r.memberName.lowercase().contains(q) ||
                            r.description.lowercase().contains(q) ||
                            r.dateLabel.lowercase().contains(q)
                        matchesTab && matchesSearch
                    }
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_reports),
                            contentDescription = stringResource(R.string.cd_empty_reports),
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (allReports.isEmpty()) "Belum Ada Laporan Harian"
                            else "Tidak Ada Laporan yang Cocok",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (allReports.isEmpty())
                                "Catat laporan kegiatan harian beserta dokumentasi foto agar tim selalu terupdate."
                            else "Coba ubah filter atau kata kunci pencarian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (allReports.isEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onCreateReportClick) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Buat Laporan Pertama")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { report ->
                            DailyReportCard(
                                report = report,
                                authorPhotoBase64 = memberPhotoMap[report.authorId],
                                canEdit = report.authorId == currentUserId,
                                canDelete = report.authorId == currentUserId || isAdmin,
                                onEdit = { onEditReportClick(report) },
                                onDelete = { reportToDelete = report },
                                onPreviewPhoto = onPreviewPhoto
                            )
                        }
                    }
                }
            }
        }
    }

    reportToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text("Hapus Laporan?") },
            text = {
                Text("Laporan dari ${target.memberName} pada ${target.dayName}, ${target.dateLabel} akan dihapus permanen untuk seluruh tim.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteReport(target.id)
                        reportToDelete = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun DailyReportCard(
    report: DailyReport,
    authorPhotoBase64: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreviewPhoto: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_card_${report.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MemberAvatar(
                        displayName = report.memberName,
                        photoBase64 = authorPhotoBase64,
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = report.memberName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${report.dayName}, ${report.dateLabel} • ${report.timeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row {
                    if (canEdit) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit laporan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Hapus laporan",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (report.photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${report.photos.size} Dokumentasi Foto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    report.photos.take(4).forEachIndexed { idx, photoB64 ->
                        val bmp = remember(photoB64) { MediaUtils.decodeBase64ToImageBitmap(photoB64) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPreviewPhoto(photoB64) }
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Foto laporan ${idx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    repeat((4 - report.photos.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
