package com.sales.visits

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

/**
 * Renders a quote to a simple, clean A4 PDF (plan 3.2 follow-up) so it can be shared with the customer.
 * Money math comes from [QuoteMath] (never re-computed here); cost/profit are internal and never printed.
 */
object QuotePdf {
    private const val W = 595   // A4 @ 72dpi (points)
    private const val H = 842
    private const val M = 40f   // margin

    fun build(ctx: Context, q: Quote, customer: Customer?, company: String, en: Boolean): File? = runCatching {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val cv = page.canvas
        val cur = q.currency.trim()

        val title = Paint().apply { color = 0xFF111111.toInt(); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val h = Paint().apply { color = 0xFF333333.toInt(); textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val p = Paint().apply { color = 0xFF222222.toInt(); textSize = 11f; isAntiAlias = true }
        val muted = Paint().apply { color = 0xFF777777.toInt(); textSize = 10f; isAntiAlias = true }
        val rightP = Paint(p).apply { textAlign = Paint.Align.RIGHT }
        val rightH = Paint(h).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 1f }

        var y = M + 10f
        cv.drawText(company.ifBlank { "VisitFlow" }, M, y, title)
        y += 22f
        cv.drawText(if (en) "QUOTATION" else "عرض سعر", M, y, h)
        cv.drawText((if (en) "No: " else "رقم: ") + q.number.ifBlank { q.id.take(6) }, W - M, y, rightP)
        y += 16f
        if (q.createdAt.isNotBlank()) { cv.drawText((if (en) "Date: " else "التاريخ: ") + q.createdAt, W - M, y, rightP); }
        cv.drawText((if (en) "To: " else "إلى: ") + (customer?.name ?: q.customerName), M, y, p)
        y += 20f
        cv.drawLine(M, y, W - M, y, line)
        y += 22f

        // Table header
        val xDesc = M; val xNet = W - M
        cv.drawText(if (en) "Description" else "الوصف", xDesc, y, h)
        cv.drawText(if (en) "Qty" else "كمية", 320f, y, h)
        cv.drawText(if (en) "Price" else "سعر", 400f, y, h)
        cv.drawText(if (en) "Net" else "الصافي", xNet, y, rightH)
        y += 8f
        cv.drawLine(M, y, W - M, y, line)
        y += 18f

        q.lines.forEach { ln ->
            val desc = ln.description.ifBlank { "-" }.let { if (it.length > 45) it.take(44) + "…" else it }
            cv.drawText(desc, xDesc, y, p)
            cv.drawText(quoteNum(ln.quantity), 320f, y, p)
            cv.drawText(quoteNum(ln.unitPrice), 400f, y, p)
            cv.drawText(fmtMoney(QuoteMath.lineNet(ln)), xNet, y, rightP)
            if (ln.discountPct > 0) { y += 13f; cv.drawText((if (en) "  disc " else "  خصم ") + quoteNum(ln.discountPct) + "%", xDesc, y, muted) }
            y += 20f
            if (y > H - 160) return@forEach   // keep it to one page for now
        }

        y += 6f
        cv.drawLine(M, y, W - M, y, line)
        y += 22f
        fun totalRow(label: String, value: String, bold: Boolean = false) {
            cv.drawText(label, 360f, y, if (bold) h else muted)
            cv.drawText("$value $cur", xNet, y, if (bold) rightH else rightP)
            y += 18f
        }
        totalRow(if (en) "Subtotal" else "الإجمالي الفرعي", fmtMoney(QuoteMath.subtotal(q)))
        totalRow((if (en) "Tax " else "الضريبة ") + "(${quoteNum(q.taxPct)}%)", fmtMoney(QuoteMath.tax(q)))
        totalRow(if (en) "TOTAL" else "الإجمالي", fmtMoney(QuoteMath.total(q)), bold = true)

        if (q.notes.isNotBlank()) {
            y += 16f
            cv.drawText(if (en) "Notes:" else "ملاحظات:", M, y, h); y += 16f
            q.notes.chunkedLines(90).take(6).forEach { cv.drawText(it, M, y, p); y += 14f }
        }

        doc.finishPage(page)
        val dir = File(ctx.filesDir, "quotes").apply { mkdirs() }
        val file = File(dir, "quote-${q.number.ifBlank { q.id.take(6) }}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }.getOrNull()

    fun share(ctx: Context, file: File, chooserTitle: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }

    private fun quoteNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    private fun String.chunkedLines(n: Int): List<String> = split("\n").flatMap { it.chunked(n) }
}
