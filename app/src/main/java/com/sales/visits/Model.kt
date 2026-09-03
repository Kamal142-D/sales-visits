package com.sales.visits

import kotlinx.serialization.Serializable

enum class VisitType(val label: String, val labelEn: String) {
    FIRST("زيارة أولى", "First visit"),
    FOLLOW("متابعة", "Follow-up"),
    DELIVER("تسليم", "Delivery"),
    COLLECT("تحصيل", "Collection"),
    COMPLAINT("شكوى", "Complaint"),
    OTHER("أخرى", "Other"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

enum class Outcome(val label: String, val labelEn: String) {
    SUCCESS("ناجحة", "Successful"),
    LEAD("محتملة", "Lead"),
    PENDING("مؤجلة", "Pending"),
    LOST("مرفوضة", "Lost"),
    NONE("بدون", "None"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

@Serializable
data class Visit(
    val id: String,
    val client: String,
    val contact: String = "",
    val phone: String = "",
    val address: String = "",
    val date: String,          // yyyy-MM-dd
    val time: String = "",     // HH:mm
    val type: String = "FOLLOW",
    val outcome: String = "NONE",
    val notes: String = "",
    val notesAr: String = "",   // AI-formatted professional Arabic version
    val notesEn: String = "",   // AI-formatted professional English version
    val next: String = "",
    val nextDate: String = "",
)

/** A reusable customer record. Visits remain linked by the normalized customer name. */
@Serializable
data class Customer(
    val id: String,
    val name: String,
    val contact: String = "",
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: String = "",
)

/** A client planned to be visited on a given day (the "Today" plan). */
@Serializable
data class PlanItem(
    val id: String,
    val client: String,
    val date: String,       // yyyy-MM-dd
    val done: Boolean = false,
)

/** Versioned portable payload used by the settings backup/import flow. */
@Serializable
data class AppBackup(
    val version: Int = 1,
    val exportedAt: String,
    val visits: List<Visit>,
    val customers: List<Customer>,
    val plan: List<PlanItem>,
)

fun Visit.typeEnum(): VisitType = runCatching { VisitType.valueOf(type) }.getOrDefault(VisitType.OTHER)
fun Visit.outcomeEnum(): Outcome = runCatching { Outcome.valueOf(outcome) }.getOrDefault(Outcome.NONE)

/** The note to display: the AI-formatted version for the active language, falling back to the raw note. */
fun Visit.notesFor(en: Boolean): String = (if (en) notesEn else notesAr).ifBlank { notes }
