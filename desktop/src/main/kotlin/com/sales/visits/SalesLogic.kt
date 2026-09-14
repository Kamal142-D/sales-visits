package com.sales.visits

/**
 * Enums + helpers ported from the Android app so the desktop can compute the same analytics/queries
 * (plan 6.5). Pure — no UI. Definitions must stay identical to the phone so numbers match.
 */

enum class OppStage(val label: String, val labelEn: String) {
    NEW("جديدة", "New"),
    QUALIFYING("تأهيل", "Qualifying"),
    NEED("فهم الاحتياج", "Needs"),
    SOLUTION("عرض الحل / تجربة", "Solution / Demo"),
    QUOTE("عرض سعر", "Quote"),
    NEGOTIATION("تفاوض / اعتماد", "Negotiation"),
    WON("تم البيع", "Won"),
    LOST("لم تتم", "Lost"),
    POSTPONED("مؤجلة", "Postponed"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
    val isActive: Boolean get() = this != WON && this != LOST && this != POSTPONED
}

fun mapLegacyStage(raw: String?): OppStage {
    val key = raw?.trim()?.uppercase().orEmpty()
    OppStage.values().firstOrNull { it.name == key }?.let { return it }
    return when (key) { "FOLLOW", "FOLLOWUP", "FOLLOW_UP" -> OppStage.QUALIFYING; else -> OppStage.NEW }
}

fun Opportunity.stageEnum(): OppStage = mapLegacyStage(stage)

enum class QuoteStatus(val label: String, val labelEn: String) {
    DRAFT("مسودة", "Draft"), SENT("أُرسل", "Sent"), ACCEPTED("مقبول", "Accepted"),
    REJECTED("مرفوض", "Rejected"), EXPIRED("منتهي", "Expired"), CANCELLED("ملغى", "Cancelled"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun quoteStatusEnum(raw: String?): QuoteStatus =
    runCatching { QuoteStatus.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(QuoteStatus.DRAFT)

fun Quote.statusEnum(): QuoteStatus = quoteStatusEnum(status)

object QuoteMath {
    fun round2(v: Double): Double =
        if (!v.isFinite()) 0.0 else java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    fun lineNet(line: QuoteLine): Double =
        round2(line.quantity * line.unitPrice * (1.0 - (line.discountPct.coerceIn(0.0, 100.0) / 100.0)))
    fun subtotal(q: Quote): Double = round2(q.lines.sumOf { lineNet(it) })
    fun tax(q: Quote): Double = round2(subtotal(q) * (q.taxPct.coerceAtLeast(0.0) / 100.0))
    fun total(q: Quote): Double = round2(subtotal(q) + tax(q))
    fun cost(q: Quote): Double = round2(q.lines.sumOf { round2(it.quantity * it.unitCost) })
    fun profit(q: Quote): Double = round2(subtotal(q) - cost(q))
    fun hasCost(q: Quote): Boolean = q.lines.any { it.unitCost > 0.0 }
    fun marginPct(q: Quote): Double { val s = subtotal(q); return if (s <= 0.0) 0.0 else round2(profit(q) / s * 100.0) }
}

enum class TaskStatus(val label: String, val labelEn: String) {
    OPEN("مفتوحة", "Open"), DONE("تمّت", "Done"), CANCELLED("أُلغيت", "Cancelled"), POSTPONED("مؤجلة", "Postponed"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun PlanItem.statusEnum(): TaskStatus = when {
    status.isNotBlank() -> runCatching { TaskStatus.valueOf(status.trim().uppercase()) }.getOrDefault(if (done) TaskStatus.DONE else TaskStatus.OPEN)
    done -> TaskStatus.DONE
    else -> TaskStatus.OPEN
}

enum class RelationshipStatus(val label: String, val labelEn: String) {
    PROSPECT("عميل محتمل", "Prospect"), CUSTOMER("عميل", "Customer"), ARCHIVED("مؤرشف", "Archived"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun relationshipEnum(raw: String?): RelationshipStatus =
    runCatching { RelationshipStatus.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(RelationshipStatus.CUSTOMER)

fun Customer.relationshipStatus(): RelationshipStatus = relationshipEnum(relationship)

enum class DecisionRole { DECISION_MAKER, INFLUENCER, TECHNICAL, PROCUREMENT, GATEKEEPER, UNKNOWN }

enum class ActivityResult { NONE, CONNECTED, NO_ANSWER, CALLBACK, DONE }

fun activityResultEnum(raw: String?): ActivityResult =
    runCatching { ActivityResult.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(ActivityResult.NONE)

/** In-app command intent (plan 4.4/6.5) — the AI fills it; the app validates and executes. */
enum class CommandAction { ADD_TASK, RESCHEDULE_TASK, SEARCH, UNKNOWN }

data class CommandIntent(
    val action: CommandAction = CommandAction.UNKNOWN,
    val customer: String = "",
    val text: String = "",
    val date: String = "",
    val time: String = "",
    val query: String = "",
)

fun taskExists(tasks: List<PlanItem>, client: String, action: String, date: String): Boolean =
    tasks.any {
        it.statusEnum() != TaskStatus.CANCELLED &&
            it.client.trim().equals(client.trim(), true) &&
            it.action.trim().equals(action.trim(), true) && it.date == date
    }
