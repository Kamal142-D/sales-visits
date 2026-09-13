package com.sales.visits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val AR_DAYS = arrayOf(
    "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"
) // index by DayOfWeek.value-1 (Mon=1..Sun=7)

private val AR_MONTHS = arrayOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

private val EN_DAYS = arrayOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)
private val EN_MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** App-wide language flag, kept in sync with the Store so the pure date helpers can localize. */
object I18n { var en = false }

fun todayIso(): String = LocalDate.now().toString()

fun parseIso(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

fun dayName(d: LocalDate): String = (if (I18n.en) EN_DAYS else AR_DAYS)[d.dayOfWeek.value - 1]
fun monthName(d: LocalDate): String = (if (I18n.en) EN_MONTHS else AR_MONTHS)[d.monthValue - 1]

/** "الأحد 9 أغسطس" */
fun baseDate(iso: String): String {
    val d = parseIso(iso) ?: return iso
    return "${dayName(d)} ${d.dayOfMonth} ${monthName(d)}"
}

/** relative label اليوم/أمس/غداً or "" */
fun relDay(iso: String): String {
    val d = parseIso(iso) ?: return ""
    val diff = LocalDate.now().toEpochDay() - d.toEpochDay()
    return when (diff) {
        0L -> if (I18n.en) "Today" else "اليوم"
        1L -> if (I18n.en) "Yesterday" else "أمس"
        -1L -> if (I18n.en) "Tomorrow" else "غداً"
        else -> ""
    }
}

/** top-of-card day label: relative if within a couple days, else full date */
fun cardDay(iso: String): String {
    val r = relDay(iso)
    return if (r.isNotEmpty()) r else baseDate(iso)
}

/** card label: just the day — اليوم/أمس, else weekday name for this week, else "9 أغسطس" (no time) */
fun cardDayShort(iso: String): String {
    val d = parseIso(iso) ?: return iso
    val diff = LocalDate.now().toEpochDay() - d.toEpochDay()
    return when {
        diff == 0L -> if (I18n.en) "Today" else "اليوم"
        diff == 1L -> if (I18n.en) "Yesterday" else "أمس"
        diff in 2..6 -> dayName(d)
        else -> if (I18n.en) "${monthName(d)} ${d.dayOfMonth}" else "${d.dayOfMonth} ${monthName(d)}"
    }
}

/** full "اليوم · الأحد 9 أغسطس" */
fun fullDay(iso: String): String {
    val r = relDay(iso)
    val b = baseDate(iso)
    return if (r.isNotEmpty()) "$r · $b" else b
}

/** "10:30 ص" from HH:mm */
fun fmtTime(t: String): String {
    if (t.isBlank()) return ""
    val parts = t.split(":")
    if (parts.size < 2) return t
    var h = parts[0].toIntOrNull() ?: return t
    val m = parts[1].toIntOrNull() ?: 0
    val ap = if (I18n.en) (if (h < 12) "AM" else "PM") else (if (h < 12) "ص" else "م")
    h %= 12; if (h == 0) h = 12
    return "$h:${m.toString().padStart(2, '0')} $ap"
}

/** The configurable first day of the week (defaults to Saturday); set from settings on launch. */
object WeekConfig {
    var startDay: DayOfWeek = DayOfWeek.SATURDAY
}

/** Week (starting on [WeekConfig.startDay]) containing today, shifted by offset weeks back. */
fun weekStart(offset: Int): LocalDate {
    val d = LocalDate.now().with(TemporalAdjusters.previousOrSame(WeekConfig.startDay))
    return d.minusWeeks(offset.toLong())
}

/** The working week is the start day plus 4 more days (5 days total, e.g. Sunday–Thursday). */
const val WORK_WEEK_DAYS = 5

fun inWeek(iso: String, offset: Int): Boolean {
    val d = parseIso(iso) ?: return false
    val s = weekStart(offset)
    val e = s.plusDays(WORK_WEEK_DAYS.toLong())
    return !d.isBefore(s) && d.isBefore(e)
}

fun weekLabel(offset: Int): String {
    val s = weekStart(offset)
    val e = s.plusDays((WORK_WEEK_DAYS - 1).toLong())
    return "${s.dayOfMonth} ${monthName(s)} – ${e.dayOfMonth} ${monthName(e)}"
}

fun weekVisits(all: List<Visit>, offset: Int): List<Visit> =
    all.filter { inWeek(it.date, offset) }
        .sortedBy { it.date + it.time }

fun reportText(all: List<Visit>, offset: Int): String {
    val en = I18n.en
    val vs = weekVisits(all, offset)
    val s = weekStart(offset); val e = s.plusDays((WORK_WEEK_DAYS - 1).toLong())
    val sb = StringBuilder()
    val range = if (en) "${monthName(s)} ${s.dayOfMonth} – ${monthName(e)} ${e.dayOfMonth}"
    else "${s.dayOfMonth} ${monthName(s)} – ${e.dayOfMonth} ${monthName(e)}"
    sb.append(if (en) "Weekly Visits Report\n" else "تقرير الزيارات الأسبوعي\n")
    sb.append("$range\n")
    sb.append("——————————————\n")
    val clients = vs.map { it.client }.toSet()
    val succ = vs.count { it.outcomeEnum() == Outcome.SUCCESS }
    if (en) {
        sb.append("Total visits: ${vs.size}\n"); sb.append("Clients: ${clients.size}\n"); sb.append("Successful: $succ\n")
    } else {
        sb.append("إجمالي الزيارات: ${vs.size}\n"); sb.append("عدد العملاء: ${clients.size}\n"); sb.append("زيارات ناجحة: $succ\n")
    }
    sb.append("——————————————\n")
    var lastD: String? = null
    for (v in vs) {
        if (v.date != lastD) { sb.append("\n${fullDay(v.date)}\n"); lastD = v.date }
        sb.append("• ${v.client} (${v.typeEnum().label(en)} - ${v.outcomeEnum().label(en)})")
        if (v.contact.isNotBlank()) sb.append(" - ${v.contact}")
        if (v.phone.isNotBlank()) sb.append(" - ${v.phone}")
        sb.append("\n")
        val note = v.notesFor(en)
        if (note.isNotBlank()) sb.append("   $note\n")
        if (v.next.isNotBlank()) {
            sb.append(if (en) "   → Next step: ${v.next}" else "   ← الخطوة الجاية: ${v.next}")
            if (v.nextDate.isNotBlank()) sb.append(" (${fullDay(v.nextDate)})")
            sb.append("\n")
        }
    }
    if (vs.isEmpty()) sb.append(if (en) "\nNo visits recorded.\n" else "\nمفيش زيارات مسجّلة.\n")
    return sb.toString()
}
