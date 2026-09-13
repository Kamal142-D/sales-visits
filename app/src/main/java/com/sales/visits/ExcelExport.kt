package com.sales.visits

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a real .xlsx (an OOXML zip) from the week's visits, matching the columns of the user's
 * "Visits Log" template exactly: Date, Company Name, Sector, Person Met, Contact (Phone/Email),
 * Purpose of Visit, Notes. No third-party library — the parts are written by hand with inline strings.
 */
object ExcelExport {

    private val HEADERS_EN = listOf(
        "Date", "Company Name", "Sector", "Person Met",
        "Contact (Phone/Email)", "Purpose of Visit", "Notes",
    )
    private val HEADERS_AR = listOf(
        "التاريخ", "اسم الشركة", "المجال", "الشخص",
        "التواصل (هاتف/إيميل)", "الغرض من الزيارة", "الملاحظات",
    )

    /**
     * One output row (7 cells) mapped from a visit + its linked customer. Notes and the purpose label
     * follow the chosen report language: the matching clean version if present, otherwise the note is
     * split by script so only the requested language is exported.
     */
    private fun rowFor(store: Store, v: Visit, en: Boolean): List<String> {
        val cust = store.customerFor(v.client)
        val sector = cust?.industry.orEmpty()
        val person = v.contact.ifBlank { cust?.contacts?.firstOrNull()?.name ?: cust?.contact.orEmpty() }
        val contact = buildString {
            val phone = v.phone.ifBlank { cust?.contacts?.firstOrNull()?.phone.orEmpty() }
            val email = cust?.contacts?.firstOrNull { it.email.isNotBlank() }?.email.orEmpty()
            append(phone)
            if (phone.isNotBlank() && email.isNotBlank()) append(" / ")
            append(email)
        }
        val purpose = v.typeEnum().label(en)
        val clean = (if (en) v.notesEn else v.notesAr)
        val notes = clean.ifBlank { noteInLanguage(v.notes, en) }
        return listOf(v.date, v.client, sector, person, contact, purpose, notes)
    }

    /** Creates the .xlsx for the given week and returns the file, or null on failure. */
    fun writeWeekFile(ctx: Context, store: Store, offset: Int, en: Boolean): File? = runCatching {
        val visits = weekVisits(store.visits, offset)
        val rows = visits.map { rowFor(store, it, en) }
        val bytes = buildXlsx("Visits Log", if (en) HEADERS_EN else HEADERS_AR, rows)
        val dir = File(ctx.filesDir, "exports").apply { mkdirs() }
        val stamp = weekStart(offset).toString()
        File(dir, "visits-log-$stamp.xlsx").apply { writeBytes(bytes) }
    }.getOrNull()

    fun shareWeek(ctx: Context, store: Store, offset: Int, en: Boolean, chooserTitle: String) {
        val file = writeWeekFile(ctx, store, offset, en) ?: return
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }

    // ---- OOXML plumbing ----

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
        // strip control chars Excel rejects, but keep tab/newline
        .filter { it >= ' ' || it == '\n' || it == '\t' }

    private fun colLetter(i: Int): String {
        var n = i; val sb = StringBuilder()
        do { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    private fun cell(ref: String, text: String, style: Int?): String {
        val s = if (style != null) " s=\"$style\"" else ""
        return "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(text)}</t></is></c>"
    }

    private fun buildXlsx(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val cols = headers.size
        val lastCol = colLetter(cols - 1)
        val lastRow = 3 + rows.size   // title row 1, blank row 2, header row 3, data from 4
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<dimension ref="A1:$lastCol$lastRow"/>""")
        sb.append("""<cols><col min="1" max="1" width="14" customWidth="1"/><col min="2" max="$cols" width="24" customWidth="1"/></cols>""")
        sb.append("<sheetData>")
        // Row 1: merged title
        sb.append("""<row r="1" ht="26" customHeight="1">""").append(cell("A1", title, 2)).append("</row>")
        // Row 3: headers
        sb.append("""<row r="3">""")
        headers.forEachIndexed { i, h -> sb.append(cell("${colLetter(i)}3", h, 1)) }
        sb.append("</row>")
        // Data
        rows.forEachIndexed { r, row ->
            val rn = r + 4
            sb.append("""<row r="$rn">""")
            row.forEachIndexed { i, v -> sb.append(cell("${colLetter(i)}$rn", v, 0)) }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("""<mergeCells count="1"><mergeCell ref="A1:$lastCol""" + "1\"/></mergeCells>")
        sb.append("</worksheet>")
        val sheetXml = sb.toString()

        val files = linkedMapOf(
            "[Content_Types].xml" to CONTENT_TYPES,
            "_rels/.rels" to ROOT_RELS,
            "xl/workbook.xml" to WORKBOOK,
            "xl/_rels/workbook.xml.rels" to WORKBOOK_RELS,
            "xl/styles.xml" to STYLES,
            "xl/worksheets/sheet1.xml" to sheetXml,
        )
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Visits Log" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="16"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
