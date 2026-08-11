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
    val next: String = "",
    val nextDate: String = "",
)

/** A client planned to be visited on a given day (the "Today" plan). */
@Serializable
data class PlanItem(
    val id: String,
    val client: String,
    val date: String,       // yyyy-MM-dd
    val done: Boolean = false,
)

fun Visit.typeEnum(): VisitType = runCatching { VisitType.valueOf(type) }.getOrDefault(VisitType.OTHER)
fun Visit.outcomeEnum(): Outcome = runCatching { Outcome.valueOf(outcome) }.getOrDefault(Outcome.NONE)
