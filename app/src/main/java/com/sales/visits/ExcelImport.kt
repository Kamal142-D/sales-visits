package com.sales.visits

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads inventory rows from a spreadsheet the user picks — an .xlsx (OOXML zip) or a .csv.
 * No third-party library: xlsx parts are unzipped and parsed by hand.
 *
 * Columns are matched by header when a header row is present (name / quantity / price / currency /
 * notes, in Arabic or English); otherwise they're read positionally in that order.
 */
object ExcelImport {

    data class Row(val name: String, val quantity: Int, val price: Double, val currency: String, val notes: String)

    fun parse(bytes: ByteArray): List<Row> {
        val table = when {
            bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> parseXlsx(bytes)
            bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() -> XlsBiff.parse(bytes)  // legacy .xls
            else -> parseCsv(bytes)
        }
        return mapRows(table)
    }

    // ---- column mapping ----
    private fun mapRows(rows: List<List<String>>): List<Row> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        fun find(vararg keys: String) = header.indexOfFirst { h -> keys.any { h.contains(it) } }
        // Prefer a real item name/description column over a grouping "product" column.
        val nameCol = find("description", "الوصف", "item name", "name", "اسم", "صنف")
            .let { if (it >= 0) it else find("item", "product", "منتج") }
        val hasHeader = nameCol >= 0
        val qtyCol = if (hasHeader) find("quantity", "qty", "stock", "كمية", "عدد", "مخزون") else 1
        val priceCol = if (hasHeader) find("price", "سعر") else 2
        val curCol = if (hasHeader) find("currency", "عملة") else 3
        val notesCol = if (hasHeader) find("note", "ملاحظ") else 4
        val nCol = if (hasHeader) nameCol else 0

        val data = if (hasHeader) rows.drop(1) else rows
        return data.mapNotNull { r ->
            val name = r.getOrNull(nCol)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Row(
                name = name,
                quantity = r.getOrNull(qtyCol)?.let { num(it)?.toInt() } ?: 0,
                price = r.getOrNull(priceCol)?.let { num(it) } ?: 0.0,
                currency = r.getOrNull(curCol)?.trim().orEmpty(),
                notes = r.getOrNull(notesCol)?.trim().orEmpty(),
            )
        }
    }

    private fun num(s: String): Double? =
        s.trim().replace(",", "").replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()

    // ---- CSV ----
    private fun parseCsv(bytes: ByteArray): List<List<String>> {
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return text.split(Regex("\r\n|\n|\r")).filter { it.isNotBlank() }.map { splitCsvLine(it) }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                (ch == ',' || ch == ';' || ch == '\t') && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    // ---- XLSX ----
    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        var sharedXml = ""
        var sheetXml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when (e.name) {
                    "xl/sharedStrings.xml" -> sharedXml = zip.readBytes().toString(Charsets.UTF_8)
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        if (sheetXml.isBlank()) return emptyList()
        val shared = parseSharedStrings(sharedXml)
        return parseSheet(sheetXml, shared)
    }

    private val siRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("<c ([^>]*?)(/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val vRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val istRegex = Regex("<is>.*?<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

    private fun parseSharedStrings(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        return siRegex.findAll(xml).map { si ->
            tRegex.findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> {
        return rowRegex.findAll(xml).map { row ->
            val cells = LinkedHashMap<Int, String>()
            var maxCol = -1
            for (cm in cellRegex.findAll(row.groupValues[1])) {
                val attrs = cm.groupValues[1]
                val inner = cm.groupValues[3]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: continue
                val col = colIndex(ref)
                val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> vRegex.find(inner)?.groupValues?.get(1)?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> istRegex.find(inner)?.groupValues?.get(1)?.let { unescape(it) }.orEmpty()
                    else -> vRegex.find(inner)?.groupValues?.get(1)?.let { unescape(it) }.orEmpty()
                }
                cells[col] = value
                if (col > maxCol) maxCol = col
            }
            (0..maxCol).map { cells[it].orEmpty() }
        }.filter { r -> r.any { it.isNotBlank() } }.toList()
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
}
