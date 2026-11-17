package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DailyReport
import com.example.data.MemberMonthlySummary
import com.example.data.Team
import com.example.data.TeamMember
import com.example.data.UiState
import com.example.ui.components.MemberAvatar
import com.example.util.DateTimeUtils
import com.example.util.ExportUtils
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlySummaryScreen(
    team: Team?,
    reportsState: UiState<List<DailyReport>>,
    teamMembers: List<TeamMember>,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH) + 1) } // 1..12

    var selectedMemberFilter by remember { mutableStateOf<String?>(null) } // null = Semua
    var selectedDayNameFilter by remember { mutableStateOf<String?>(null) } // null = Semua
    var selectedDayOfMonthFilter by remember { mutableStateOf<Int?>(null) } // null = Semua

    var showMonthYearPicker by remember { mutableStateOf(false) }
    var showMemberDropdown by remember { mutableStateOf(false) }
    var showDayDropdown by remember { mutableStateOf(false) }
    var showCsvOptionsDialog by remember { mutableStateOf(false) }
    var showPdfOptionsDialog by remember { mutableStateOf(false) }

    val expandedMembers = remember { mutableStateMapOf<String, Boolean>() }

    val yearMonthKey = remember(selectedYear, selectedMonth) {
        DateTimeUtils.formatYearMonthKey(selectedYear, selectedMonth)
    }
    val periodLabel = remember(selectedYear, selectedMonth) {
        DateTimeUtils.formatMonthYearLabel(selectedYear, selectedMonth)
    }

    val allReports = (reportsState as? UiState.Success)?.data ?: emptyList()

    val monthReports = remember(allReports, yearMonthKey) {
        allReports.filter { it.yearMonth == yearMonthKey }
            .sortedBy { it.reportTimestampMillis }
    }

    val memberOptions = remember(monthReports, teamMembers) {
        (teamMembers.map { it.displayName } + monthReports.map { it.memberName })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val filteredReports = remember(
        monthReports,
        selectedMemberFilter,
        selectedDayNameFilter,
        selectedDayOfMonthFilter
    ) {
        monthReports.filter { r ->
            val matchMember = selectedMemberFilter == null ||
                r.memberName.equals(selectedMemberFilter, ignoreCase = true)
            val matchDayName = selectedDayNameFilter == null ||
                r.dayName.equals(selectedDayNameFilter, ignoreCase = true)
            val matchDayNum = selectedDayOfMonthFilter == null ||
                r.dayOfMonth.toInt() == selectedDayOfMonthFilter
            matchMember && matchDayName && matchDayNum
        }
    }

    val groupedByMember = remember(filteredReports) {
        filteredReports.groupBy { it.memberName.trim().ifEmpty { "Anggota" } }
            .entries
            .map { (name, list) ->
                MemberMonthlySummary(
                    memberName = name,
                    reportCount = list.size,
                    activeDaysCount = list.map { it.dayOfMonth }.distinct().size,
                    photoCount = list.sumOf { it.photos.size },
                    reports = list.sortedBy { it.reportTimestampMillis }
                )
            }
            .sortedByDescending { it.reportCount }
    }

    val filterDescription = remember(selectedMemberFilter, selectedDayNameFilter, selectedDayOfMonthFilter) {
        buildList {
            selectedMemberFilter?.let { add("Anggota: $it") }
            selectedDayNameFilter?.let { add("Hari: $it") }
            selectedDayOfMonthFilter?.let { add("Tanggal: $it") }
        }.joinToString(" • ")
    }

    val teamName = team?.name ?: "Tim BPS"
    val teamCode = team?.teamCode ?: "-"

    // SAF CSV Save Launcher
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = ExportUtils.generateCsvContent(filteredReports)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("File CSV berhasil disimpan.")
            } catch (e: Exception) {
                onShowMessage("Gagal menyimpan CSV: ${e.localizedMessage}")
            }
        }
    }

    // SAF PDF Save Launcher
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                val pdfBytes = ExportUtils.generateMonthlyPdfBytes(
                    teamName = teamName,
                    teamCode = teamCode,
                    periodLabel = periodLabel,
                    filterLabel = filterDescription,
                    reports = filteredReports,
                    grouped = groupedByMember
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pdfBytes)
                }
                onShowMessage("File PDF berhasil disimpan.")
            } catch (e: Exception) {
                onShowMessage("Gagal menyimpan PDF: ${e.localizedMessage}")
            }
        }
    }

    val memberPhotoMap = remember(teamMembers) {
        teamMembers.associate { it.displayName.lowercase() to it.photoBase64 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month & Year Picker Header
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (selectedMonth == 1) {
                                selectedMonth = 12
                                selectedYear -= 1
                            } else {
                                selectedMonth -= 1
                            }
                        },
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya")
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showMonthYearPicker = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("month_year_selector"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedMonth == 12) {
                                selectedMonth = 1
                                selectedYear += 1
                            } else {
                                selectedMonth += 1
                            }
                        },
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
                    }
                }
            }
        }

        // Filter Chips Row (Anggota + Hari/Tanggal)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    FilterChip(
                        selected = selectedMemberFilter != null,
                        onClick = { showMemberDropdown = true },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(selectedMemberFilter ?: "Semua Anggota", maxLines = 1)
                        },
                        modifier = Modifier.testTag("filter_member_chip")
                    )
                    DropdownMenu(
                        expanded = showMemberDropdown,
                        onDismissRequest = { showMemberDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Semua Anggota") },
                            onClick = {
                                selectedMemberFilter = null
                                showMemberDropdown = false
                            }
                        )
                        memberOptions.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedMemberFilter = name
                                    showMemberDropdown = false
                                }
                            )
                        }
                    }
                }

                Box {
                    val dayText = when {
                        selectedDayOfMonthFilter != null -> "Tgl $selectedDayOfMonthFilter"
                        selectedDayNameFilter != null -> selectedDayNameFilter!!
                        else -> "Semua Hari"
                    }
                    FilterChip(
                        selected = selectedDayNameFilter != null || selectedDayOfMonthFilter != null,
                        onClick = { showDayDropdown = true },
                        leadingIcon = {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(dayText, maxLines = 1) },
                        modifier = Modifier.testTag("filter_day_chip")
                    )
                    DropdownMenu(
                        expanded = showDayDropdown,
                        onDismissRequest = { showDayDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Semua Hari & Tanggal") },
                            onClick = {
                                selectedDayNameFilter = null
                                selectedDayOfMonthFilter = null
                                showDayDropdown = false
                            }
                        )
                        HorizontalDivider()
                        DateTimeUtils.INDONESIAN_DAYS.forEach { day ->
                            DropdownMenuItem(
                                text = { Text("Hari $day") },
                                onClick = {
                                    selectedDayNameFilter = day
                                    selectedDayOfMonthFilter = null
                                    showDayDropdown = false
                                }
                            )
                        }
                        val activeDates = monthReports.map { it.dayOfMonth.toInt() }.distinct().sorted()
                        if (activeDates.isNotEmpty()) {
                            HorizontalDivider()
                            activeDates.forEach { dateNum ->
                                DropdownMenuItem(
                                    text = { Text("Tanggal $dateNum $periodLabel") },
                                    onClick = {
                                        selectedDayOfMonthFilter = dateNum
                                        selectedDayNameFilter = null
                                        showDayDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // KPI Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryKpiCard(
                    label = "Total Laporan",
                    value = filteredReports.size.toString(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    label = "Anggota Aktif",
                    value = groupedByMember.size.toString(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    label = "Total Foto",
                    value = filteredReports.sumOf { it.photos.size }.toString(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Action Buttons: Salin, WhatsApp, Export CSV, Cetak PDF
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Bagikan & Ekspor Rekap",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = ExportUtils.buildWhatsAppSummaryText(
                                    teamName, teamCode, periodLabel, filterDescription,
                                    filteredReports, groupedByMember
                                )
                                ExportUtils.copyToClipboard(context, "Rekap $periodLabel", text)
                                onShowMessage("Rekap bulanan disalin ke clipboard.")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_summary_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salin")
                        }

                        Button(
                            onClick = {
                                val text = ExportUtils.buildWhatsAppSummaryText(
                                    teamName, teamCode, periodLabel, filterDescription,
                                    filteredReports, groupedByMember
                                )
                                ExportUtils.shareToWhatsApp(context, text)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B8A4B),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("whatsapp_summary_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCsvOptionsDialog = true },
                            enabled = filteredReports.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_csv_button")
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV")
                        }

                        Button(
                            onClick = { showPdfOptionsDialog = true },
                            enabled = filteredReports.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("print_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cetak PDF")
                        }
                    }
                }
            }
        }

        when (reportsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = reportsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Success -> {
                if (groupedByMember.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Belum ada laporan pada $periodLabel",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Pilih bulan lain atau ubah filter anggota/hari.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(groupedByMember, key = { it.memberName }) { summary ->
                        val isExpanded = expandedMembers[summary.memberName] ?: true
                        MemberSummaryCard(
                            summary = summary,
                            photoBase64 = memberPhotoMap[summary.memberName.lowercase()],
                            expanded = isExpanded,
                            onToggleExpand = {
                                expandedMembers[summary.memberName] = !isExpanded
                            }
                        )
                    }
                }
            }
        }
    }

    if (showMonthYearPicker) {
        var tempYear by remember(selectedYear) { mutableIntStateOf(selectedYear) }
        var tempMonth by remember(selectedMonth) { mutableIntStateOf(selectedMonth) }
        AlertDialog(
            onDismissRequest = { showMonthYearPicker = false },
            title = { Text("Pilih Bulan & Tahun") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { tempYear -= 1 }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Tahun sebelumnya")
                        }
                        Text(
                            text = tempYear.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { tempYear += 1 }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Tahun berikutnya")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DateTimeUtils.INDONESIAN_MONTHS.forEachIndexed { idx, monthName ->
                            val monthNum = idx + 1
                            FilterChip(
                                selected = tempMonth == monthNum,
                                onClick = { tempMonth = monthNum },
                                label = { Text(monthName.take(3)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedYear = tempYear
                        selectedMonth = tempMonth
                        showMonthYearPicker = false
                    }
                ) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { showMonthYearPicker = false }) { Text("Batal") }
            }
        )
    }

    if (showCsvOptionsDialog) {
        val fileName = "Rekap_BPS_${yearMonthKey}.csv"
        AlertDialog(
            onDismissRequest = { showCsvOptionsDialog = false },
            title = { Text("Export CSV ($periodLabel)") },
            text = { Text("Pilih simpan ke perangkat (Downloads/Drive) atau langsung bagikan file CSV.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCsvOptionsDialog = false
                        saveCsvLauncher.launch(fileName)
                    }
                ) { Text("Simpan CSV") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCsvOptionsDialog = false
                        val csv = ExportUtils.generateCsvContent(filteredReports)
                        val uri = ExportUtils.writeExportUri(
                            context, fileName, csv.toByteArray(Charsets.UTF_8)
                        )
                        ExportUtils.shareFileUri(context, uri, "text/csv", "Bagikan Rekap CSV")
                    }
                ) { Text("Bagikan CSV") }
            }
        )
    }

    if (showPdfOptionsDialog) {
        val fileName = "Rekap_BPS_${yearMonthKey}.pdf"
        AlertDialog(
            onDismissRequest = { showPdfOptionsDialog = false },
            title = { Text("Cetak & Simpan PDF ($periodLabel)") },
            text = { Text("Cetak langsung (termasuk Simpan sebagai PDF melalui pratinjau cetak Android), simpan file, atau bagikan PDF.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPdfOptionsDialog = false
                        val pdfBytes = ExportUtils.generateMonthlyPdfBytes(
                            teamName, teamCode, periodLabel, filterDescription,
                            filteredReports, groupedByMember
                        )
                        ExportUtils.printPdfBytes(context, "Rekap_BPS_$yearMonthKey", pdfBytes)
                    }
                ) { Text("Cetak / Preview") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showPdfOptionsDialog = false
                            savePdfLauncher.launch(fileName)
                        }
                    ) { Text("Simpan") }
                    TextButton(
                        onClick = {
                            showPdfOptionsDialog = false
                            val pdfBytes = ExportUtils.generateMonthlyPdfBytes(
                                teamName, teamCode, periodLabel, filterDescription,
                                filteredReports, groupedByMember
                            )
                            val uri = ExportUtils.writeExportUri(context, fileName, pdfBytes)
                            ExportUtils.shareFileUri(context, uri, "application/pdf", "Bagikan Rekap PDF")
                        }
                    ) { Text("Bagikan") }
                }
            }
        )
    }
}

@Composable
private fun SummaryKpiCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun MemberSummaryCard(
    summary: MemberMonthlySummary,
    photoBase64: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MemberAvatar(
                        displayName = summary.memberName,
                        photoBase64 = photoBase64,
                        size = 40.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.memberName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${summary.reportCount} laporan • ${summary.activeDaysCount} hari aktif • ${summary.photoCount} foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Ciutkan" else "Tampilkan detail"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    summary.reports.forEachIndexed { idx, r ->
                        if (idx > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${r.dayName}, ${r.dateLabel} • ${r.timeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (r.photos.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${r.photos.size} foto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = r.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
