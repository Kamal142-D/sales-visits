package com.sales.visits

import kotlinx.serialization.Serializable

/** The purpose of a visit — the mandatory field, chosen from a fixed list. */
enum class VisitType(val label: String, val labelEn: String) {
    FIRST_VISIT("زيارة أولى / تعريف", "First Visit / Introduction"),
    QUOTE_SUBMIT("تقديم عرض سعر", "Quotation Submission"),
    QUOTE_FOLLOW("متابعة عرض سعر", "Quotation Follow-Up"),
    CLIENT_FOLLOW("متابعة العميل", "Client Follow-Up"),
    NEGOTIATION("تفاوض", "Negotiation"),
    CONTRACT("توقيع عقد", "Contract Signing"),
    DEMO("عرض المنتج", "Product Demo"),
    DELIVERY("تسليم / تركيب", "Delivery / Installation"),
    TECH_SUPPORT("دعم فني", "Technical Support"),
    AFTER_SALES("خدمة ما بعد البيع", "After-Sales Service"),
    PAYMENT("تحصيل", "Payment Collection"),
    COURTESY("زيارة علاقات / مجاملة", "Relationship / Courtesy Visit"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

/**
 * Maps any stored visit-type string — including the legacy desktop enum
 * (FIRST/FOLLOW/DELIVER/COLLECT/COMPLAINT/OTHER) — onto the current 12-purpose [VisitType].
 * A legacy "FOLLOW" becomes [VisitType.CLIENT_FOLLOW], NOT a first visit. Pure and total.
 */
fun mapLegacyVisitType(raw: String?): VisitType {
    val key = raw?.trim()?.uppercase().orEmpty()
    // Exact match against a current value wins first.
    VisitType.values().firstOrNull { it.name == key }?.let { return it }
    return when (key) {
        "FIRST", "FIRST_VISIT", "INTRO", "INTRODUCTION" -> VisitType.FIRST_VISIT
        "FOLLOW", "FOLLOWUP", "FOLLOW_UP" -> VisitType.CLIENT_FOLLOW
        "QUOTE", "QUOTATION" -> VisitType.QUOTE_SUBMIT
        "DELIVER", "DELIVERY", "INSTALL", "INSTALLATION" -> VisitType.DELIVERY
        "COLLECT", "COLLECTION", "PAYMENT" -> VisitType.PAYMENT
        "COMPLAINT", "SUPPORT", "TECH", "TECHNICAL" -> VisitType.TECH_SUPPORT
        "DEMO", "DEMONSTRATION" -> VisitType.DEMO
        "CONTRACT" -> VisitType.CONTRACT
        "NEGOTIATION", "NEGOTIATE" -> VisitType.NEGOTIATION
        "AFTER_SALES", "AFTERSALES" -> VisitType.AFTER_SALES
        "COURTESY", "OTHER", "" -> VisitType.COURTESY
        else -> VisitType.COURTESY   // unknown legacy value: a neutral bucket, never a fake "first visit"
    }
}

/** Where a customer stands with us — drives the "prospect bank" filter (plan 2.1). */
enum class RelationshipStatus(val label: String, val labelEn: String) {
    PROSPECT("عميل محتمل", "Prospect"),
    CUSTOMER("عميل", "Customer"),
    ARCHIVED("مؤرشف", "Archived"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun relationshipEnum(raw: String?): RelationshipStatus =
    runCatching { RelationshipStatus.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(RelationshipStatus.CUSTOMER)

/** A contact's role in a buying decision (plan 2.2). A person can hold several. */
enum class DecisionRole(val label: String, val labelEn: String) {
    DECISION_MAKER("صاحب قرار", "Decision maker"),
    INFLUENCER("مؤثّر", "Influencer"),
    TECHNICAL("فني", "Technical"),
    PROCUREMENT("مشتريات", "Procurement"),
    GATEKEEPER("بوابة وصول", "Gatekeeper"),
    UNKNOWN("غير معروف", "Unknown"),
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

/** Bump when the backup/sync schema changes. Readers accept anything in 1..CURRENT and migrate up. */
const val CURRENT_BACKUP_VERSION = 2

@Serializable
data class Visit(
    val id: String,
    val client: String,               // display snapshot of the customer name at record time
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
    val checklist: List<ChecklistItem> = emptyList(),   // user's own to-do items on the visit
    val images: List<String> = emptyList(),             // local file paths of attached photos
    val customerId: String = "",      // stable link to Customer.id ("" = not yet linked)
)

/** A single checkable to-do item on a visit. */
@Serializable
data class ChecklistItem(val text: String = "", val done: Boolean = false)

/** One person to contact at a customer. */
@Serializable
data class ContactPerson(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val id: String = "",              // stable id ("" = pre-migration; backfilled on load)
    val jobTitle: String = "",        // e.g. "Production Manager"
    val roles: List<String> = emptyList(),   // DecisionRole names — who this person is in the decision
)

/** A reusable customer record. Visits remain linked by the normalized customer name. */
@Serializable
data class Customer(
    val id: String,
    val name: String,
    val industry: String = "",            // the customer company's line of business
    val contact: String = "",             // primary contact name (kept in sync with contacts[0])
    val phone: String = "",
    val address: String = "",
    val locationUrl: String = "",         // optional maps link for the address
    val notes: String = "",
    val createdAt: String = "",
    val contacts: List<ContactPerson> = emptyList(),
    // Prospect-bank fields (plan 2.1) — all optional, filled in gradually.
    val city: String = "",
    val region: String = "",
    val website: String = "",
    val source: String = "",              // how we got to know them (referral / web / event / cold call…)
    val relationship: String = "CUSTOMER",// PROSPECT | CUSTOMER | ARCHIVED
    val equipment: String = "",           // current machines/equipment they own
    val apps: String = "",                // applications/workflows they run
)

fun Customer.relationshipStatus(): RelationshipStatus = relationshipEnum(relationship)

/** Role of an account inside a company/team. */
enum class TeamRole { MANAGER, REP;
    companion object { fun from(s: String?) = if (s == "MANAGER") MANAGER else REP }
}

/** One member of a company team (as read from the cloud). */
data class TeamMember(
    val uid: String,
    val name: String,
    val email: String,
    val role: TeamRole,
)

/** A shared company-pipeline opportunity (plan 6.2) as read from the cloud. Owner-scoped writes. */
data class TeamOpp(
    val id: String,
    val title: String,
    val customerName: String = "",
    val value: Double = 0.0,
    val currency: String = "",
    val stage: String = "NEW",
    val ownerUid: String = "",
    val ownerName: String = "",
)

/** A customer shared to the whole company (plan 6.2) — visible to every member; editable by its
 *  owner (the rep who shared it) or a manager, enforced by the backend rules. */
data class TeamCustomer(
    val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val industry: String = "",
    val note: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
)

/** A unit of work a manager assigns to a rep: a customer to visit (shared at company level). */
data class Assignment(
    val id: String,
    val customerName: String,
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val assignedTo: String = "",        // rep uid
    val assignedToName: String = "",
    val done: Boolean = false,
)

/** A product/item in our stock, with its price — used by the AI to decide the next step. */
@Serializable
data class InventoryItem(
    val id: String,
    val name: String,
    val quantity: Int = 0,          // 0 ⇒ out of stock
    val price: Double = 0.0,        // 0 ⇒ no price on record
    val currency: String = "",
    val notes: String = "",
) {
    val inStock: Boolean get() = quantity > 0
    val hasPrice: Boolean get() = price > 0.0
}

/** An order/purchase a customer made — the basis of the customer's total purchases. */
@Serializable
data class Order(
    val id: String,
    val customerName: String,        // display snapshot of the customer name
    val title: String = "",          // what was ordered (items / description)
    val amount: Double = 0.0,
    val currency: String = "",
    val date: String = "",           // yyyy-MM-dd
    val status: String = "DELIVERED",  // ORDERED | DELIVERED
    val notes: String = "",
    val customerId: String = "",     // stable link to Customer.id ("" = not yet linked)
)

/**
 * Stage of a sales opportunity — the default pipeline (plan 2.4). [POSTPONED] parks a deal outside
 * active work; [WON]/[LOST] close it. A deal need not pass through every stage.
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
    /** An active deal is one still being worked (not closed and not parked). */
    val isActive: Boolean get() = this != WON && this != LOST && this != POSTPONED
}

/** Maps any stored stage string, incl. the legacy NEW/FOLLOW/WON/LOST, onto the current pipeline. */
fun mapLegacyStage(raw: String?): OppStage {
    val key = raw?.trim()?.uppercase().orEmpty()
    OppStage.values().firstOrNull { it.name == key }?.let { return it }
    return when (key) {
        "FOLLOW", "FOLLOWUP", "FOLLOW_UP" -> OppStage.QUALIFYING   // old "follow-up" → an active stage, not a fake "new"
        else -> OppStage.NEW
    }
}

/** Whether a customer's budget for a deal is known (plan 2.3). */
enum class BudgetStatus(val label: String, val labelEn: String) {
    UNKNOWN("غير معروفة", "Unknown"),
    HAS_BUDGET("متوفرة", "Has budget"),
    NO_BUDGET("غير متوفرة", "No budget"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun budgetEnum(raw: String?): BudgetStatus =
    runCatching { BudgetStatus.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(BudgetStatus.UNKNOWN)

/** One recorded stage transition on an opportunity (plan 2.4 — the movement log). */
@Serializable
data class StageChange(
    val from: String = "",
    val to: String = "",
    val at: String = "",       // yyyy-MM-dd
    val note: String = "",
    val by: String = "",       // who moved it (owner/display name)
)

/** A sales opportunity: a deal in the pipeline, optionally linked to a customer. */
@Serializable
data class Opportunity(
    val id: String,
    val title: String,
    val customerName: String = "",
    val value: Double = 0.0,
    val currency: String = "",
    val stage: String = "NEW",
    val owner: String = "",
    val nextStep: String = "",
    val nextDate: String = "",
    val notes: String = "",
    val createdAt: String = "",
    val customerId: String = "",     // stable link to Customer.id ("" = not yet linked)
    // Qualification fields (plan 2.3) — all optional, filled in gradually.
    val product: String = "",        // proposed product / solution
    val need: String = "",           // what the customer needs
    val problem: String = "",        // the problem + its impact
    val budgetStatus: String = "UNKNOWN",
    val budgetAmount: Double = 0.0,
    val closeDate: String = "",      // expected purchase / close date
    val competitor: String = "",
    val blocker: String = "",        // current obstacle
    val lossReason: String = "",
    val lossReasonDeclared: Boolean = false,  // true = the customer stated it; false = our inference
    val stageChangedAt: String = "",
    val lastInteraction: String = "",
    val decisionContactIds: List<String> = emptyList(),  // the customer's contacts involved in THIS deal
    val history: List<StageChange> = emptyList(),
)

fun Opportunity.stageEnum(): OppStage = mapLegacyStage(stage)

/**
 * Returns a copy moved to [newStage], appending a [StageChange] to the history — but only if the
 * stage actually changed. Pure and idempotent for a no-op move.
 */
fun Opportunity.withStage(newStage: OppStage, at: String, note: String = "", by: String = ""): Opportunity {
    if (stageEnum() == newStage) return this
    return copy(
        stage = newStage.name,
        stageChangedAt = at,
        history = history + StageChange(from = stageEnum().name, to = newStage.name, at = at, note = note, by = by),
    )
}

/**
 * The qualification pieces the plan wants present before a final quote (2.4). Returns the missing
 * keys so the UI can nudge (never hard-blocks — an explicit exception is allowed).
 */
fun Opportunity.missingForQuote(hasDecisionMaker: Boolean): List<String> = buildList {
    if (need.isBlank()) add("need")
    if (product.isBlank()) add("product")
    if (!hasDecisionMaker) add("decision_maker")
    if (closeDate.isBlank()) add("timing")
    if (budgetEnum(budgetStatus) == BudgetStatus.UNKNOWN) add("budget")
}

/** Status of a task/follow-up (plan 3.3) — cancelled/postponed are distinct from just moving the date. */
enum class TaskStatus(val label: String, val labelEn: String) {
    OPEN("مفتوحة", "Open"),
    DONE("تمّت", "Done"),
    CANCELLED("أُلغيت", "Cancelled"),
    POSTPONED("مؤجلة", "Postponed"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

/** Where a follow-up came from (plan 3.3) — lets an automation avoid creating a duplicate. */
enum class TaskSource(val label: String, val labelEn: String) {
    MANUAL("يدوي", "Manual"),
    VISIT("زيارة", "Visit"),
    QUOTE("عرض سعر", "Quote"),
    OPPORTUNITY("فرصة", "Opportunity"),
    AI("مساعد", "Assistant"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

/** A client planned to be visited on a given day (the "Today" plan), or a follow-up task. */
@Serializable
data class PlanItem(
    val id: String,
    val client: String,
    val date: String,       // yyyy-MM-dd
    val done: Boolean = false,
    val action: String = "",   // what to do for the client (visit, deliver, collect…)
    val time: String = "",     // HH:mm — when the task is planned (drives Morning/Afternoon/Evening)
    val minutes: Int = 0,      // estimated duration in minutes
    val customerId: String = "",   // stable link to Customer.id ("" = free-text / not linked)
    val opportunityId: String = "",// optional link to a deal
    val source: String = "",       // TaskSource — how this follow-up was created
    val sourceRef: String = "",    // id of the originating record (e.g. the quote) — used to de-duplicate
    val status: String = "",       // TaskStatus — "" means fall back to the legacy `done` flag
    val completedAt: String = "",  // yyyy-MM-dd when marked done
    val result: String = "",       // free-text outcome of the follow-up
    val calendarEventId: String = "", // Google Calendar event id once synced (plan 6.4) — enables 2-way
)

/** Resolved status: honors the new `status` field, falling back to the legacy `done` boolean. */
fun PlanItem.statusEnum(): TaskStatus = when {
    status.isNotBlank() -> runCatching { TaskStatus.valueOf(status.trim().uppercase()) }
        .getOrDefault(if (done) TaskStatus.DONE else TaskStatus.OPEN)
    done -> TaskStatus.DONE
    else -> TaskStatus.OPEN
}

fun PlanItem.isDoneTask(): Boolean = statusEnum() == TaskStatus.DONE

/** A lightweight interaction record (plan 3.1) — a call, meeting, or logged message, sitting alongside
 *  visits in one customer timeline. A visit stays its own record; activities never duplicate it. */
enum class ActivityType(val label: String, val labelEn: String) {
    CALL("مكالمة", "Call"),
    MEETING("اجتماع", "Meeting"),
    EMAIL("بريد", "Email"),
    MESSAGE("رسالة", "Message"),
    NOTE("ملاحظة", "Note"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

/** Outcome of an activity. A logged call is NOT assumed answered; email is NOT assumed delivered. */
enum class ActivityResult(val label: String, val labelEn: String) {
    NONE("—", "—"),
    CONNECTED("تم التواصل", "Connected"),
    NO_ANSWER("لا رد", "No answer"),
    CALLBACK("إعادة اتصال", "Call back"),
    DONE("تمّ", "Done"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun activityTypeEnum(raw: String?): ActivityType =
    runCatching { ActivityType.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(ActivityType.CALL)

fun activityResultEnum(raw: String?): ActivityResult =
    runCatching { ActivityResult.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(ActivityResult.NONE)

@Serializable
data class Activity(
    val id: String,
    val customerId: String = "",     // stable link to Customer.id
    val customerName: String = "",   // display snapshot
    val opportunityId: String = "",  // optional link to a deal (must be the same customer's)
    val contactId: String = "",      // optional link to a ContactPerson
    val type: String = "CALL",
    val date: String = "",           // yyyy-MM-dd (when it happened)
    val time: String = "",           // HH:mm (optional)
    val summary: String = "",
    val result: String = "NONE",
    val by: String = "",             // who logged/did it
    val createdAt: String = "",
)

/** Status of a price quote (plan 3.2). "Viewed" is deferred until there's reliable proof. */
enum class QuoteStatus(val label: String, val labelEn: String) {
    DRAFT("مسودة", "Draft"),
    SENT("أُرسل", "Sent"),
    ACCEPTED("مقبول", "Accepted"),
    REJECTED("مرفوض", "Rejected"),
    EXPIRED("منتهي", "Expired"),
    CANCELLED("ملغى", "Cancelled"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun quoteStatusEnum(raw: String?): QuoteStatus =
    runCatching { QuoteStatus.valueOf(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(QuoteStatus.DRAFT)

/** A single line on a quote. Price is a snapshot — updating inventory never changes an old quote. */
@Serializable
data class QuoteLine(
    val id: String = "",
    val description: String = "",
    val productId: String = "",      // link to an InventoryItem, if the line came from stock
    val quantity: Double = 1.0,
    val unit: String = "",
    val unitPrice: Double = 0.0,
    val discountPct: Double = 0.0,   // per-line discount, 0..100
    val unitCost: Double = 0.0,      // your cost per unit (optional) — drives the profit margin
)

/** A price quote — a record of what was offered, with line items and a status. */
@Serializable
data class Quote(
    val id: String,
    val number: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val opportunityId: String = "",
    val contactId: String = "",
    val currency: String = "",
    val status: String = "DRAFT",
    val version: Int = 1,
    val taxPct: Double = 0.0,        // entered by the user per their setup; never assumed
    val lines: List<QuoteLine> = emptyList(),
    val notes: String = "",
    val attachmentPath: String = "", // optional local file of a quote issued elsewhere (local-only for now)
    val orderId: String = "",        // set once an order is created from this quote — prevents duplicates
    val createdAt: String = "",
    val sentDate: String = "",
    val expiryDate: String = "",
)

fun Quote.statusEnum(): QuoteStatus = quoteStatusEnum(status)

/** Turns a (sent) quote into a fresh editable revision — the original is left untouched. */
fun Quote.asRevision(newId: String): Quote =
    copy(id = newId, version = version + 1, status = QuoteStatus.DRAFT.name, orderId = "", sentDate = "")

/**
 * Quote arithmetic — done in code with a declared rounding policy (HALF_UP to 2 decimals), never by AI.
 * One currency per quote. Rounding each line before summing avoids Double drift.
 */
object QuoteMath {
    fun round2(v: Double): Double =
        if (!v.isFinite()) 0.0 else java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    fun lineNet(line: QuoteLine): Double =
        round2(line.quantity * line.unitPrice * (1.0 - (line.discountPct.coerceIn(0.0, 100.0) / 100.0)))

    fun subtotal(q: Quote): Double = round2(q.lines.sumOf { lineNet(it) })
    fun tax(q: Quote): Double = round2(subtotal(q) * (q.taxPct.coerceAtLeast(0.0) / 100.0))
    fun total(q: Quote): Double = round2(subtotal(q) + tax(q))

    /** Total cost across lines that have a cost entered (lines with no cost contribute 0). */
    fun cost(q: Quote): Double = round2(q.lines.sumOf { round2(it.quantity * it.unitCost) })

    /** Profit = subtotal (before tax) − total cost. Only meaningful when some costs are entered. */
    fun profit(q: Quote): Double = round2(subtotal(q) - cost(q))

    /** True once at least one line carries a cost, so margin figures are worth showing. */
    fun hasCost(q: Quote): Boolean = q.lines.any { it.unitCost > 0.0 }

    /** Profit margin on the pre-tax subtotal, as a percentage (0 when subtotal is 0). */
    fun marginPct(q: Quote): Double {
        val sub = subtotal(q)
        return if (sub <= 0.0) 0.0 else round2(profit(q) / sub * 100.0)
    }
}

/**
 * Product knowledge (plan 6.1) — specs/applications, kept SEPARATE from stock+price (InventoryItem).
 * Each record carries a source + last-updated + a verified flag, so unverified facts are shown as such
 * (having a brochure never proves availability).
 */
@Serializable
data class ProductKnowledge(
    val id: String,
    val productId: String = "",       // optional link to an InventoryItem (stock/price live there)
    val name: String,
    val manufacturer: String = "",
    val model: String = "",
    val specs: String = "",
    val applications: String = "",    // what jobs/uses it fits
    val materials: String = "",       // supported materials/media
    val limits: String = "",          // constraints/limitations
    val suitableFor: String = "",     // the kind of customer it suits
    val documents: List<String> = emptyList(),  // brochure/spec doc paths or URLs
    val source: String = "",          // where the info came from
    val updatedAt: String = "",       // yyyy-MM-dd the info was last verified
    val verified: Boolean = false,    // false ⇒ "needs verification"
)

/** An objection-handling entry (plan 6.1): the objection, a discovery probe, evidence, and a reply. */
@Serializable
data class Objection(
    val id: String,
    val text: String = "",
    val probe: String = "",
    val evidence: String = "",
    val response: String = "",
)

/**
 * An attachment (plan 6.3): file metadata that syncs; the bytes live in cloud storage (not the
 * snapshot). `uploaded=false` means it's still local-only (deferred upload, or storage not enabled).
 */
@Serializable
data class Attachment(
    val id: String,
    val recordType: String = "",     // CUSTOMER | QUOTE | OPP | VISIT
    val recordId: String = "",
    val name: String = "",
    val mime: String = "",
    val size: Long = 0,
    val storagePath: String = "",    // e.g. users/{uid}/attachments/{id}
    val localPath: String = "",      // on-device path ("" once only in cloud)
    val uploaded: Boolean = false,
    val createdAt: String = "",
)

/** A big project / account initiative (plan 6.6): groups several opportunities under one account
 *  effort and maps the stakeholders driving the decision. Value is never summed across currencies. */
@Serializable
data class Project(
    val id: String,
    val customerId: String = "",       // stable link to Customer.id
    val customerName: String = "",     // display snapshot
    val name: String = "",
    val description: String = "",
    val opportunityIds: List<String> = emptyList(),  // deals rolled into this project
    val stakeholderIds: List<String> = emptyList(),   // ContactPerson.id driving the decision
    val status: String = "ACTIVE",     // ProjectStatus
    val targetDate: String = "",       // yyyy-MM-dd expected decision/close
    val createdAt: String = "",
)

enum class ProjectStatus(val label: String, val labelEn: String) {
    ACTIVE("نشط", "Active"),
    WON("مكسوب", "Won"),
    LOST("خاسر", "Lost"),
    ON_HOLD("مؤجّل", "On hold"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

fun Project.statusEnum(): ProjectStatus =
    runCatching { ProjectStatus.valueOf(status.trim().uppercase()) }.getOrDefault(ProjectStatus.ACTIVE)

/** Combined value of the project's linked opportunities, split per currency (never summed across). */
fun Project.valueByCurrency(opps: List<Opportunity>): Map<String, Double> =
    opps.filter { it.id in opportunityIds }
        .groupBy { it.currency.ifBlank { "—" } }
        .mapValues { (_, list) -> list.sumOf { it.value } }

/** Versioned portable payload used by the settings backup/import flow.
 *  New lists carry defaults so older backups (which lack them) still restore cleanly. */
@Serializable
data class AppBackup(
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: String,
    val visits: List<Visit>,
    val customers: List<Customer>,
    val plan: List<PlanItem>,
    val tasks: List<PlanItem> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val opportunities: List<Opportunity> = emptyList(),
    val orders: List<Order> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val products: List<ProductKnowledge> = emptyList(),
    val objections: List<Objection> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val projects: List<Project> = emptyList(),
)

/** Product matching (plan 6.1) — pure. Explains fit against a customer need; flags what's unverified. */
object ProductMatch {
    data class Match(val product: ProductKnowledge, val score: Int, val why: String, val verified: Boolean)

    /** Ranks products whose applications/specs/materials/name overlap the [need] keywords. */
    fun forNeed(need: String, products: List<ProductKnowledge>): List<Match> {
        val words = need.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        if (words.isEmpty()) return emptyList()
        return products.mapNotNull { p ->
            val hay = listOf(p.name, p.applications, p.specs, p.materials, p.suitableFor).joinToString(" ").lowercase()
            val hits = words.filter { hay.contains(it) }
            if (hits.isEmpty()) null
            else ProductMatch.Match(p, hits.size, hits.joinToString(", "), p.verified)
        }.sortedByDescending { it.score }
    }
}

@Serializable
data class UserProfile(
    val name: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val companyEmail: String = "",   // work email at the company
    val shareLoginEmail: Boolean = true,   // include the account email on the shared card
)

/** Three-way sync resolves whole-list changes; local wins only when the same record changed on both devices. */
internal fun mergeBackups(local: AppBackup, remote: AppBackup): AppBackup = AppBackup(
    exportedAt = "",
    visits = (remote.visits.associateBy { it.id } + local.visits.associateBy { it.id }).values.toList(),
    customers = (remote.customers.associateBy { it.id } + local.customers.associateBy { it.id }).values.toList(),
    plan = (remote.plan.associateBy { it.id } + local.plan.associateBy { it.id }).values.toList(),
    tasks = (remote.tasks.associateBy { it.id } + local.tasks.associateBy { it.id }).values.toList(),
    inventory = (remote.inventory.associateBy { it.id } + local.inventory.associateBy { it.id }).values.toList(),
    opportunities = (remote.opportunities.associateBy { it.id } + local.opportunities.associateBy { it.id }).values.toList(),
    orders = (remote.orders.associateBy { it.id } + local.orders.associateBy { it.id }).values.toList(),
    activities = (remote.activities.associateBy { it.id } + local.activities.associateBy { it.id }).values.toList(),
    quotes = (remote.quotes.associateBy { it.id } + local.quotes.associateBy { it.id }).values.toList(),
    products = (remote.products.associateBy { it.id } + local.products.associateBy { it.id }).values.toList(),
    objections = (remote.objections.associateBy { it.id } + local.objections.associateBy { it.id }).values.toList(),
    attachments = (remote.attachments.associateBy { it.id } + local.attachments.associateBy { it.id }).values.toList(),
    projects = (remote.projects.associateBy { it.id } + local.projects.associateBy { it.id }).values.toList(),
)

internal fun checkCloudMerge() {
    val remote = AppBackup(exportedAt = "", visits = listOf(Visit("v1", "Remote", date = "2026-01-01")), customers = emptyList(), plan = emptyList())
    val local = AppBackup(exportedAt = "", visits = listOf(Visit("v1", "Local", date = "2026-01-01"), Visit("v2", "Only local", date = "2026-01-01")), customers = emptyList(), plan = emptyList())
    val merged = mergeBackups(local, remote)
    check(merged.visits.size == 2 && merged.visits.first { it.id == "v1" }.client == "Local")
}

/** One entry in a customer's unified timeline — a visit or a logged activity (plan 3.1). */
sealed interface TimelineItem {
    val date: String
    val time: String
    val sortKey: String get() = date + "T" + time
}
data class VisitItem(val visit: Visit) : TimelineItem {
    override val date get() = visit.date
    override val time get() = visit.time
}
data class ActivityItem(val activity: Activity) : TimelineItem {
    override val date get() = activity.date
    override val time get() = activity.time
}

/** Merges visits and activities into one timeline, newest first. Pure — the visit stays its own record. */
fun mergeTimeline(visits: List<Visit>, activities: List<Activity>): List<TimelineItem> =
    (visits.map { VisitItem(it) } + activities.map { ActivityItem(it) }).sortedByDescending { it.sortKey }

/** Why an agenda item is surfaced today (plan 3.5) — shown to the rep so priority is explained. */
enum class AgendaReason { APPOINTMENT, OVERDUE, DUE_TODAY, NEEDS_ACTION, EXPIRED }

/** One thing on the day's agenda, from any source, with the reason it's here. */
data class AgendaItem(
    val refId: String,
    val kind: String,          // TASK | VISIT | OPP | QUOTE
    val title: String,
    val customer: String,
    val date: String,
    val time: String,
    val reason: AgendaReason,
)

/** Today's three buckets: overdue, due-today, and suggestions that need a decision. */
data class Agenda(
    val overdue: List<AgendaItem>,
    val today: List<AgendaItem>,
    val suggestions: List<AgendaItem>,
)

/**
 * Builds the day's agenda from tasks, visit follow-ups, opportunity next-steps and quote expiries —
 * pure, offline, no AI (plan 3.5). No double counting: an opportunity whose follow-up is already an
 * open task is not also listed on its own. Inactive/closed deals don't chase the rep.
 */
fun buildAgenda(
    today: String,
    tasks: List<PlanItem>,
    visits: List<Visit>,
    opportunities: List<Opportunity>,
    quotes: List<Quote>,
): Agenda {
    val overdue = ArrayList<AgendaItem>()
    val todayL = ArrayList<AgendaItem>()
    val suggestions = ArrayList<AgendaItem>()

    // Open tasks (not done, not cancelled — postponed still carries a date and is shown).
    val openTasks = tasks.filter { it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED }
    val oppsWithOpenTask = openTasks.mapNotNull { it.opportunityId.takeIf(String::isNotBlank) }.toSet()
    for (task in openTasks) {
        val title = task.action.ifBlank { task.client }
        when {
            task.date < today -> overdue.add(AgendaItem(task.id, "TASK", title, task.client, task.date, task.time, AgendaReason.OVERDUE))
            task.date == today -> todayL.add(AgendaItem(task.id, "TASK", title, task.client, task.date, task.time,
                if (task.time.isNotBlank()) AgendaReason.APPOINTMENT else AgendaReason.DUE_TODAY))
        }
    }

    // Visit follow-ups.
    for (v in visits) {
        if (v.next.isBlank() || v.nextDate.isBlank()) continue
        when {
            v.nextDate < today -> overdue.add(AgendaItem(v.id, "VISIT", v.next, v.client, v.nextDate, v.time, AgendaReason.OVERDUE))
            v.nextDate == today -> todayL.add(AgendaItem(v.id, "VISIT", v.next, v.client, v.nextDate, v.time, AgendaReason.DUE_TODAY))
        }
    }

    // Opportunity next-steps (only ACTIVE deals; skip ones already represented by an open task).
    for (o in opportunities) {
        if (!o.stageEnum().isActive || o.id in oppsWithOpenTask) continue
        val label = o.nextStep.ifBlank { o.title }
        when {
            o.nextDate.isBlank() -> suggestions.add(AgendaItem(o.id, "OPP", o.title, o.customerName, "", "", AgendaReason.NEEDS_ACTION))
            o.nextDate < today -> overdue.add(AgendaItem(o.id, "OPP", label, o.customerName, o.nextDate, "", AgendaReason.OVERDUE))
            o.nextDate == today -> todayL.add(AgendaItem(o.id, "OPP", label, o.customerName, o.nextDate, "", AgendaReason.DUE_TODAY))
        }
    }

    // Sent quotes past their expiry — a decision is needed.
    for (q in quotes) {
        if (q.statusEnum() == QuoteStatus.SENT && q.expiryDate.isNotBlank() && q.expiryDate < today) {
            suggestions.add(AgendaItem(q.id, "QUOTE", q.number.ifBlank { "Quote" }, q.customerName, q.expiryDate, "", AgendaReason.EXPIRED))
        }
    }

    fun List<AgendaItem>.ordered() = sortedWith(compareBy({ it.date }, { it.time.ifBlank { "99:99" } }))
    return Agenda(overdue.ordered(), todayL.ordered(), suggestions.ordered())
}

/** A parsed in-app command (plan 4.4). The AI only fills this; the APP validates and executes it. */
enum class CommandAction { ADD_TASK, RESCHEDULE_TASK, SEARCH, UNKNOWN }

data class CommandIntent(
    val action: CommandAction = CommandAction.UNKNOWN,
    val customer: String = "",
    val text: String = "",     // the task/action text
    val date: String = "",     // resolved yyyy-MM-dd
    val time: String = "",     // HH:mm
    val query: String = "",    // for SEARCH / finding a task to reschedule
)

/** True if an OPEN task with the same client+action+date already exists (dedup for command add). */
fun taskExists(tasks: List<PlanItem>, client: String, action: String, date: String): Boolean =
    tasks.any {
        it.statusEnum() != TaskStatus.CANCELLED &&
            it.client.trim().equals(client.trim(), true) &&
            it.action.trim().equals(action.trim(), true) && it.date == date
    }

/** Kind of message the AI drafts (plan 4.3). */
enum class DraftType(val label: String, val labelEn: String) {
    POST_VISIT("بعد الزيارة", "After visit"),
    QUOTE_FOLLOWUP("متابعة عرض سعر", "Quote follow-up"),
    MEETING_REQUEST("طلب اجتماع", "Meeting request"),
    PRODUCT_INFO("معلومات منتج", "Product info"),
    PROCUREMENT_PRICE("سؤال المشتريات", "Ask procurement"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
    /** True when the recipient is our own team, not the customer. */
    val isInternal: Boolean get() = this == PROCUREMENT_PRICE
}

enum class DraftTone(val label: String, val labelEn: String) {
    FRIENDLY("ودّي", "Friendly"),
    FORMAL("رسمي", "Formal"),
    CONCISE("مختصر", "Concise"),
    ;
    fun label(en: Boolean) = if (en) labelEn else label
}

/* ---- Specific automations (plan 3.4) — pure rules, no AI. ---- */

/** True if a sent quote needs a follow-up task created (and one doesn't already exist — de-dup). */
fun shouldCreateQuoteFollowUp(quote: Quote, tasks: List<PlanItem>): Boolean =
    quote.statusEnum() == QuoteStatus.SENT &&
        tasks.none { it.source == TaskSource.QUOTE.name && it.sourceRef == quote.id }

/** Follow-up date for a sent quote: the customer's explicit expiry if set, else an editable +N-day suggestion. */
fun followUpDateForQuote(quote: Quote, today: String, suggestDays: Long = 3): String =
    quote.expiryDate.ifBlank {
        runCatching { java.time.LocalDate.parse(today).plusDays(suggestDays).toString() }.getOrDefault(today)
    }

/** True if sending [quote] should advance its linked opportunity forward to the Quote stage. */
fun shouldAdvanceOppToQuote(opp: Opportunity): Boolean =
    opp.stageEnum().isActive && opp.stageEnum().ordinal < OppStage.QUOTE.ordinal

/** Ids of a closed deal's still-open, today-or-future tasks — candidates to auto-cancel. */
fun openFutureTasksForOpp(oppId: String, tasks: List<PlanItem>, today: String): List<String> =
    tasks.filter {
        it.opportunityId == oppId && it.date >= today &&
            it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED
    }.map { it.id }

fun Visit.typeEnum(): VisitType = mapLegacyVisitType(type)
fun Visit.outcomeEnum(): Outcome = runCatching { Outcome.valueOf(outcome) }.getOrDefault(Outcome.NONE)

/** The note to display: the AI-formatted version for the active language, falling back to the raw note. */
fun Visit.notesFor(en: Boolean): String = (if (en) notesEn else notesAr).ifBlank { notes }
