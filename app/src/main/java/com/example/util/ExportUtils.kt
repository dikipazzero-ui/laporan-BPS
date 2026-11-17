package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.DailyReport
import com.example.data.MemberMonthlySummary
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ExportUtils {

    fun buildWhatsAppSummaryText(
        teamName: String,
        teamCode: String,
        periodLabel: String,
        filterLabel: String,
        reports: List<DailyReport>,
        grouped: List<MemberMonthlySummary>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("*LAPORAN HARIAN BPS*")
        sb.appendLine("*Perumahan Bumi Papan Selaras*")
        sb.appendLine("Tim: $teamName (Kode: $teamCode)")
        sb.appendLine("Periode: $periodLabel")
        if (filterLabel.isNotBlank()) {
            sb.appendLine("Filter: $filterLabel")
        }
        sb.appendLine("Total Laporan: ${reports.size} | Anggota Aktif: ${grouped.size}")
        sb.appendLine("────────────────────────")
        if (grouped.isEmpty()) {
            sb.appendLine("Belum ada laporan pada periode ini.")
        } else {
            grouped.forEach { member ->
                sb.appendLine()
                sb.appendLine("*${member.memberName}* (${member.reportCount} laporan, ${member.activeDaysCount} hari)")
                member.reports.forEachIndexed { idx, r ->
                    val photoNote = if (r.photos.isNotEmpty()) " [${r.photos.size} foto]" else ""
                    sb.appendLine("${idx + 1}. ${r.dayName}, ${r.dateLabel} • ${r.timeLabel}$photoNote")
                    sb.appendLine("   ${r.description}")
                }
            }
        }
        return sb.toString().trim()
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareToWhatsApp(context: Context, text: String) {
        val pm = context.packageManager
        val waPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in waPackages) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(pkg)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return
            }
        }
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, "Bagikan Rekap ke WhatsApp / Aplikasi"))
    }

    fun generateCsvContent(reports: List<DailyReport>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Microsoft Excel compatibility
        sb.appendLine("No,Nama Anggota,Hari,Tanggal,Jam,Keterangan Pelaporan,Jumlah Foto")
        reports.forEachIndexed { index, r ->
            sb.append(index + 1).append(',')
            sb.append(escapeCsv(r.memberName)).append(',')
            sb.append(escapeCsv(r.dayName)).append(',')
            sb.append(escapeCsv(r.dateLabel)).append(',')
            sb.append(escapeCsv(r.timeLabel)).append(',')
            sb.append(escapeCsv(r.description)).append(',')
            sb.append(r.photos.size)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\r\n", " ").replace('\n', ' ')
        return "\"${clean.replace("\"", "\"\"")}\""
    }

    fun writeExportUri(context: Context, fileName: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareFileUri(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun generateMonthlyPdfBytes(
        teamName: String,
        teamCode: String,
        periodLabel: String,
        filterLabel: String,
        reports: List<DailyReport>,
        grouped: List<MemberMonthlySummary>
    ): ByteArray {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        val headerBgPaint = Paint().apply { color = Color.rgb(15, 76, 92) }
        val accentPaint = Paint().apply { color = Color.rgb(217, 119, 6) }
        val cardBgPaint = Paint().apply { color = Color.rgb(241, 245, 244) }
        val dividerPaint = Paint().apply {
            color = Color.rgb(210, 220, 218)
            strokeWidth = 1f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 238, 242)
            textSize = 10.5f
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 76, 92)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 38, 41)
            textSize = 10f
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(88, 102, 105)
            textSize = 9.2f
        }

        fun finishCurrentPageFooter() {
            canvas.drawText(
                "Halaman $pageNumber • Laporan Harian BPS - Perumahan Bumi Papan Selaras",
                margin,
                pageHeight - 18f,
                mutedPaint
            )
            document.finishPage(page)
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > pageHeight - margin - 20f) {
                finishCurrentPageFooter()
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
        }

        // Top Banner
        canvas.drawRoundRect(
            RectF(margin, y, pageWidth - margin, y + 66f),
            10f, 10f, headerBgPaint
        )
        canvas.drawRect(margin, y + 58f, pageWidth - margin, y + 66f, accentPaint)
        canvas.drawText("REKAP LAPORAN HARIAN BPS", margin + 14f, y + 25f, titlePaint)
        canvas.drawText(
            "Perumahan Bumi Papan Selaras • $teamName (Kode: $teamCode)",
            margin + 14f, y + 43f, subtitlePaint
        )
        y += 80f

        // Summary Bar
        canvas.drawRoundRect(
            RectF(margin, y, pageWidth - margin, y + 42f),
            8f, 8f, cardBgPaint
        )
        val filterSummary = if (filterLabel.isBlank()) "Semua Anggota & Hari" else filterLabel
        canvas.drawText("Periode: $periodLabel   |   Filter: $filterSummary", margin + 12f, y + 17f, bodyPaint)
        canvas.drawText(
            "Total Laporan: ${reports.size}   •   Anggota Aktif: ${grouped.size}   •   Total Foto: ${reports.sumOf { it.photos.size }}",
            margin + 12f, y + 33f, sectionPaint
        )
        y += 56f

        if (grouped.isEmpty()) {
            canvas.drawText("Tidak ada data laporan untuk filter yang dipilih.", margin, y + 16f, bodyPaint)
        } else {
            grouped.forEach { member ->
                ensureSpace(46f)
                canvas.drawRoundRect(
                    RectF(margin, y, pageWidth - margin, y + 24f),
                    6f, 6f, cardBgPaint
                )
                canvas.drawText(
                    "${member.memberName} — ${member.reportCount} Laporan (${member.activeDaysCount} hari)",
                    margin + 10f, y + 16f, sectionPaint
                )
                y += 32f

                member.reports.forEachIndexed { idx, report ->
                    val descLayout = StaticLayout.Builder.obtain(
                        report.description, 0, report.description.length, bodyPaint, contentWidth - 20
                    ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                    val rowHeight = 20f + descLayout.height + 12f
                    ensureSpace(rowHeight)

                    val photoText = if (report.photos.isNotEmpty()) " • ${report.photos.size} foto" else ""
                    canvas.drawText(
                        "${idx + 1}. ${report.dayName}, ${report.dateLabel}  |  Jam ${report.timeLabel}$photoText",
                        margin + 6f, y + 12f, mutedPaint
                    )
                    y += 17f

                    canvas.save()
                    canvas.translate(margin + 14f, y)
                    descLayout.draw(canvas)
                    canvas.restore()
                    y += descLayout.height + 6f

                    canvas.drawLine(margin + 6f, y, pageWidth - margin - 6f, y, dividerPaint)
                    y += 8f
                }
                y += 8f
            }
        }

        finishCurrentPageFooter()
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    fun printPdfBytes(context: Context, jobName: String, pdfBytes: ByteArray) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileOutputStream(destination?.fileDescriptor).use { out ->
                        out.write(pdfBytes)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
