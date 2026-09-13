package com.sales.visits

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the legacy binary .xls format (OLE2 compound file + BIFF8 records) into a grid of cell
 * strings — enough to import a simple price list (text via SST/LABEL, numbers via NUMBER/RK/MULRK).
 * No third-party library.
 */
object XlsBiff {

    fun parse(buf: ByteArray): List<List<String>> = runCatching { parseInner(buf) }.getOrDefault(emptyList())

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun dbl(b: ByteArray, o: Int) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getDouble(o)

    private fun parseInner(buf: ByteArray): List<List<String>> {
        if (buf.size < 512 || u32(buf, 0) != 0xE011CFD0.toInt()) return emptyList()
        val secSize = 1 shl u16(buf, 0x1E)
        val miniSize = 1 shl u16(buf, 0x20)
        val dirStart = u32(buf, 0x30)
        val miniCutoff = u32(buf, 0x38)
        val miniFatStart = u32(buf, 0x3C)
        val numMiniFat = u32(buf, 0x40)
        val difatStart = u32(buf, 0x44)
        val numDifat = u32(buf, 0x48)
        fun secOff(sec: Int) = 512 + sec * secSize

        // DIFAT → FAT sector list
        val fatSecs = ArrayList<Int>()
        for (i in 0 until 109) { val v = u32(buf, 0x4C + i * 4); if (v != -1 && v != -2) fatSecs.add(v) }
        var ds = difatStart
        var n = 0
        while (n < numDifat && ds != -2 && ds != -1) {
            val base = secOff(ds); val perSec = secSize / 4 - 1
            for (i in 0 until perSec) { val v = u32(buf, base + i * 4); if (v != -1 && v != -2) fatSecs.add(v) }
            ds = u32(buf, base + perSec * 4); n++
        }
        val fat = IntArray(fatSecs.size * (secSize / 4))
        var fi = 0
        for (fsSec in fatSecs) { val base = secOff(fsSec); for (i in 0 until secSize / 4) fat[fi++] = u32(buf, base + i * 4) }

        fun readChain(start: Int): ByteArray {
            val out = ByteArrayOutputStream(); var s = start; var guard = 0
            while (s != -2 && s != -1 && s >= 0 && guard++ < 200000) {
                val off = secOff(s); if (off + secSize > buf.size) break
                out.write(buf, off, secSize); s = if (s < fat.size) fat[s] else -2
            }
            return out.toByteArray()
        }

        val dirBytes = readChain(dirStart)
        data class Entry(val name: String, val type: Int, val start: Int, val size: Int)
        val entries = ArrayList<Entry>()
        var o = 0
        while (o + 128 <= dirBytes.size) {
            val nameLen = u16(dirBytes, o + 64)
            if (nameLen in 2..64) {
                val name = String(dirBytes, o, nameLen - 2, Charsets.UTF_16LE)
                entries.add(Entry(name, dirBytes[o + 66].toInt() and 0xFF, u32(dirBytes, o + 116), u32(dirBytes, o + 120)))
            }
            o += 128
        }
        val root = entries.firstOrNull { it.type == 5 }
        val miniStream = if (root != null) readChain(root.start) else ByteArray(0)
        val miniFatArr: IntArray = if (numMiniFat > 0) {
            val mb = readChain(miniFatStart); IntArray(mb.size / 4) { u32(mb, it * 4) }
        } else IntArray(0)
        fun readMini(start: Int, size: Int): ByteArray {
            val out = ByteArrayOutputStream(); var s = start; var guard = 0
            while (s != -2 && s != -1 && s >= 0 && guard++ < 200000) {
                val off = s * miniSize; if (off + miniSize > miniStream.size) break
                out.write(miniStream, off, miniSize); s = if (s < miniFatArr.size) miniFatArr[s] else -2
            }
            return out.toByteArray().copyOf(size)
        }
        fun readStream(e: Entry) = if (e.size < miniCutoff) readMini(e.start, e.size) else readChain(e.start).copyOf(e.size)

        val wb = entries.firstOrNull { it.name == "Workbook" } ?: entries.firstOrNull { it.name == "Book" } ?: return emptyList()
        val stream = readStream(wb)

        // Records
        data class Rec(val type: Int, val data: ByteArray)
        val recs = ArrayList<Rec>()
        var p = 0
        while (p + 4 <= stream.size) {
            val type = u16(stream, p); val len = u16(stream, p + 2)
            if (p + 4 + len > stream.size) break
            recs.add(Rec(type, stream.copyOfRange(p + 4, p + 4 + len))); p += 4 + len
        }

        // SST (with CONTINUE)
        var sst: List<String> = emptyList()
        val sstIdx = recs.indexOfFirst { it.type == 0x00FC }
        if (sstIdx >= 0) {
            val parts = ArrayList<ByteArray>(); parts.add(recs[sstIdx].data)
            val bounds = ArrayList<Int>(); bounds.add(recs[sstIdx].data.size)
            var i = sstIdx + 1
            while (i < recs.size && recs[i].type == 0x003C) { parts.add(recs[i].data); bounds.add(bounds.last() + recs[i].data.size); i++ }
            val d = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
            val boundarySet = bounds.dropLast(1).toHashSet()
            val unique = u32(d, 4)
            val strings = ArrayList<String>(unique.coerceAtMost(100000))
            var q = 8; var s = 0
            while (s < unique && q + 3 <= d.size) {
                val cch = u16(d, q); q += 2
                var flags = d[q].toInt() and 0xFF; q += 1
                var high = flags and 0x01
                val hasRich = flags and 0x08; val hasExt = flags and 0x04
                var rich = 0; var ext = 0
                if (hasRich != 0) { rich = u16(d, q); q += 2 }
                if (hasExt != 0) { ext = u32(d, q); q += 4 }
                val sb = StringBuilder(); var need = cch
                while (need > 0 && q < d.size) {
                    if (boundarySet.contains(q)) { flags = d[q].toInt() and 0xFF; q += 1; high = flags and 0x01 }
                    if (high != 0) { sb.append(String(d, q, 2, Charsets.UTF_16LE)); q += 2 }
                    else { sb.append((d[q].toInt() and 0xFF).toChar()); q += 1 }
                    need--
                }
                if (hasRich != 0) q += 4 * rich
                if (hasExt != 0) q += ext
                strings.add(sb.toString()); s++
            }
            sst = strings
        }

        fun rkVal(rk: Int): Double {
            val fInt = rk and 0x02 != 0; val fMul = rk and 0x01 != 0
            val v = if (fInt) (rk shr 2).toDouble()
            else Double.fromBits((rk.toLong() and 0xFFFFFFFCL) shl 32)
            return if (fMul) v / 100.0 else v
        }
        fun numStr(v: Double): String = if (v.isFinite() && v == Math.floor(v)) v.toLong().toString() else v.toString()

        val cells = HashMap<Int, HashMap<Int, String>>()
        fun put(r: Int, col: Int, v: String) { cells.getOrPut(r) { HashMap() }[col] = v }
        for (rec in recs) {
            val d = rec.data
            when (rec.type) {
                0x00FD -> { if (d.size >= 10) { val isst = u32(d, 6); put(u16(d, 0), u16(d, 2), sst.getOrElse(isst) { "" }) } }   // LABELSST
                0x0204 -> { if (d.size >= 9) { val cch = u16(d, 6); val high = d[8].toInt() and 1
                    val s = if (high != 0) String(d, 9, (cch * 2).coerceAtMost(d.size - 9), Charsets.UTF_16LE)
                    else String(d, 9, cch.coerceAtMost(d.size - 9), Charsets.ISO_8859_1)
                    put(u16(d, 0), u16(d, 2), s) } }                                                                             // LABEL
                0x0203 -> { if (d.size >= 14) put(u16(d, 0), u16(d, 2), numStr(dbl(d, 6))) }                                     // NUMBER
                0x027E -> { if (d.size >= 10) put(u16(d, 0), u16(d, 2), numStr(rkVal(u32(d, 6)))) }                              // RK
                0x00BD -> { if (d.size >= 6) { val r = u16(d, 0); val first = u16(d, 2); val last = u16(d, d.size - 2)
                    var q2 = 4; var col = first
                    while (col <= last && q2 + 6 <= d.size) { put(r, col, numStr(rkVal(u32(d, q2 + 2)))); q2 += 6; col++ } } }   // MULRK
            }
        }

        val rowNums = cells.keys.sorted()
        return rowNums.map { r ->
            val row = cells[r]!!
            val maxCol = row.keys.maxOrNull() ?: -1
            (0..maxCol).map { row[it] ?: "" }
        }
    }
}
