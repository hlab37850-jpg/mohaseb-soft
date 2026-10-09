package com.muhasib.soft.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.muhasib.soft.R
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.JournalLineEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object PdfUtil {

    fun printDoc(ctx: Context, doc: DocumentEntity, items: List<DocumentItemEntity>, lines: List<JournalLineEntity>) {
        val pdf = PdfDocument()
        val pageW = 595
        val pageH = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = pdf.startPage(pageInfo)
        val c: Canvas = page.canvas
        val title = Paint().apply { textSize = 22f; isFakeBoldText = true; typeface = Typeface.DEFAULT; isAntiAlias = true }
        val normal = Paint().apply { textSize = 12f; isAntiAlias = true }
        val small = Paint().apply { textSize = 10f; isAntiAlias = true }
        var y = 50f
        val lineH = 18f

        c.drawText(ctx.getString(R.string.app_name), (pageW / 2).toFloat(), y, title)
        y += lineH * 2
        val label = Paint(title).apply { textSize = 16f }
        c.drawText(when (doc.type) {
            "SALE" -> ctx.getString(if (doc.isCredit) R.string.sale_credit else R.string.sale_cash)
            "PURCHASE" -> ctx.getString(if (doc.isCredit) R.string.purchase_credit else R.string.purchase_cash)
            "RECEIPT" -> ctx.getString(R.string.receipt_voucher)
            "PAYMENT" -> ctx.getString(R.string.payment_voucher)
            "JOURNAL" -> ctx.getString(R.string.journal_voucher)
            "QUOTE" -> ctx.getString(R.string.quote_title)
            else -> ctx.getString(R.string.invoice)
        }, 40f, y, label)
        y += lineH
        c.drawText("${ctx.getString(R.string.number)} #${doc.number}", 40f, y, normal)
        c.drawText(doc.date, (pageW - 120).toFloat(), y, normal)
        y += lineH
        if (doc.partyName.isNotEmpty()) {
            c.drawText("${ctx.getString(R.string.account)}: ${doc.partyName}", 40f, y, normal)
            y += lineH
        }
        y += lineH
        val header = Paint(label).apply { textSize = 13f }
        c.drawText(ctx.getString(R.string.item), 40f, y, header)
        c.drawText(ctx.getString(R.string.qty), 240f, y, header)
        c.drawText(ctx.getString(R.string.price), 320f, y, header)
        c.drawText(ctx.getString(R.string.total), 440f, y, header)
        y += lineH
        items.forEach {
            if (y > pageH - 100) { pdf.finishPage(page); return }
            c.drawText(it.name.take(30), 40f, y, normal)
            c.drawText(fmt(it.qty), 240f, y, normal)
            c.drawText(fmt(it.price), 320f, y, normal)
            c.drawText(fmt(it.total), 440f, y, normal)
            y += lineH
        }
        y += lineH
        lines.forEach {
            if (y > pageH - 100) return@forEach
            c.drawText(it.accountName, 40f, y, normal)
            c.drawText(fmt(it.debit), 320f, y, normal)
            c.drawText(fmt(it.credit), 440f, y, normal)
            y += lineH
        }
        y += lineH
        c.drawText("${ctx.getString(R.string.net)}: ${fmt(doc.net)}", 40f, y, header)
        y += lineH
        c.drawText("${ctx.getString(R.string.remaining)}: ${fmt(doc.remaining)}", 40f, y, header)
        pdf.finishPage(page)

        val fileName = "${doc.type}_${doc.number}_${System.currentTimeMillis()}.pdf"
        val file = File(ctx.cacheDir, fileName)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.print)))
        } catch (e: Exception) {
            ctx.toast("تعذر فتح PDF: ${e.message}")
        }
    }

    fun printList(ctx: Context, title: String, cols: List<String>, rows: List<List<String>>) {
        val pdf = PdfDocument()
        val pageW = 842
        val pageH = 595
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas
        val header = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
        var y = 40f
        c.drawText(title, 40f, y, header)
        y += 30f
        val colW = (pageW - 80) / cols.size.coerceAtLeast(1)
        cols.forEachIndexed { i, col -> c.drawText(col, 40f + i * colW, y, header) }
        y += 16f
        rows.forEach { row ->
            if (y > pageH - 40) { pdf.finishPage(page); return }
            row.forEachIndexed { i, cell -> c.drawText(cell.take(30), 40f + i * colW, y, normal) }
            y += 14f
        }
        pdf.finishPage(page)
        val fileName = "${title}_${System.currentTimeMillis()}.pdf"
        val file = File(ctx.cacheDir, fileName)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.print)))
        } catch (e: Exception) {
            ctx.toast("تعذر فتح PDF: ${e.message}")
        }
    }
}
