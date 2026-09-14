package com.sales.visits

import kotlinx.serialization.Serializable

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

/** Maps stored visit-type strings (incl. the legacy FIRST/FOLLOW/DELIVER/COLLECT/COMPLAINT/OTHER). */
fun mapLegacyVisitType(raw: String?): VisitType {
    val key = raw?.trim()?.uppercase().orEmpty()
    VisitType.values().firstOrNull { it.name == key }?.let { return it }
    return when (key) {
        "FIRST", "INTRO", "INTRODUCTION" -> VisitType.FIRST_VISIT
        "FOLLOW", "FOLLOWUP", "FOLLOW_UP" -> VisitType.CLIENT_FOLLOW
        "QUOTE", "QUOTATION" -> VisitType.QUOTE_SUBMIT
        "DELIVER", "INSTALL", "INSTALLATION" -> VisitType.DELIVERY
        "COLLECT", "COLLECTION" -> VisitType.PAYMENT
        "COMPLAINT", "SUPPORT", "TECH", "TECHNICAL" -> VisitType.TECH_SUPPORT
        "DEMONSTRATION" -> VisitType.DEMO
        "NEGOTIATE" -> VisitType.NEGOTIATION
        "AFTERSALES" -> VisitType.AFTER_SALES
        else -> VisitType.COURTESY
    }
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
    val checklist: List<ChecklistItem> = emptyList(),   // user's own to-do items on the visit
    val images: List<String> = emptyList(),             // local file paths of attached photos
    val customerId: String = "",      // stable link to Customer.id (carried through for round-trip)
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
    val id: String = "",              // stable id (carried through for round-trip)
    val jobTitle: String = "",
    val roles: List<String> = emptyList(),
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
    val city: String = "",
    val region: String = "",
    val website: String = "",
    val source: String = "",
    val relationship: String = "CUSTOMER",
    val equipment: String = "",
    val apps: String = "",
)

/** A client planned to be visited on a given day (the "Today" plan). */
@Serializable
data class PlanItem(
    val id: String,
    val client: String,
    val date: String,       // yyyy-MM-dd
    val done: Boolean = false,
    val action: String = "",
    val time: String = "",
    val minutes: Int = 0,
    val customerId: String = "",   // stable link to Customer.id (carried through for round-trip)
    val opportunityId: String = "",
    val source: String = "",
    val sourceRef: String = "",
    val status: String = "",
    val completedAt: String = "",
    val result: String = "",
)

/** Stock item (round-tripped for cloud sync with the Android app). */
@Serializable
data class InventoryItem(
    val id: String,
    val name: String,
    val quantity: Int = 0,
    val price: Double = 0.0,
    val currency: String = "",
    val notes: String = "",
)

/** One recorded stage transition on an opportunity (carried through for round-trip). */
@Serializable
data class StageChange(
    val from: String = "",
    val to: String = "",
    val at: String = "",
    val note: String = "",
    val by: String = "",
)

/** Sales opportunity (round-tripped for cloud sync with the Android app). */
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
    val customerId: String = "",     // stable link to Customer.id (carried through for round-trip)
    val product: String = "",
    val need: String = "",
    val problem: String = "",
    val budgetStatus: String = "UNKNOWN",
    val budgetAmount: Double = 0.0,
    val closeDate: String = "",
    val competitor: String = "",
    val blocker: String = "",
    val lossReason: String = "",
    val lossReasonDeclared: Boolean = false,
    val stageChangedAt: String = "",
    val lastInteraction: String = "",
    val decisionContactIds: List<String> = emptyList(),
    val history: List<StageChange> = emptyList(),
)

/** An order/purchase a customer made (round-tripped for cloud sync). */
@Serializable
data class Order(
    val id: String,
    val customerName: String,
    val title: String = "",
    val amount: Double = 0.0,
    val currency: String = "",
    val date: String = "",
    val status: String = "DELIVERED",
    val notes: String = "",
    val customerId: String = "",     // stable link to Customer.id (carried through for round-trip)
)

/** Bump when the backup/sync schema changes. Readers accept anything in 1..CURRENT. */
const val CURRENT_BACKUP_VERSION = 2

/** Interaction record (round-tripped for cloud sync with the Android app). */
@Serializable
data class Activity(
    val id: String,
    val customerId: String = "",
    val customerName: String = "",
    val opportunityId: String = "",
    val contactId: String = "",
    val type: String = "CALL",
    val date: String = "",
    val time: String = "",
    val summary: String = "",
    val result: String = "NONE",
    val by: String = "",
    val createdAt: String = "",
)

/** Quote line item (round-tripped for cloud sync). */
@Serializable
data class QuoteLine(
    val id: String = "",
    val description: String = "",
    val productId: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    val unitPrice: Double = 0.0,
    val discountPct: Double = 0.0,
    val unitCost: Double = 0.0,
)

/** Price quote (round-tripped for cloud sync). */
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
    val taxPct: Double = 0.0,
    val lines: List<QuoteLine> = emptyList(),
    val notes: String = "",
    val attachmentPath: String = "",
    val orderId: String = "",
    val createdAt: String = "",
    val sentDate: String = "",
    val expiryDate: String = "",
)

/** Product knowledge (round-tripped for cloud sync). */
@Serializable
data class ProductKnowledge(
    val id: String,
    val productId: String = "",
    val name: String,
    val manufacturer: String = "",
    val model: String = "",
    val specs: String = "",
    val applications: String = "",
    val materials: String = "",
    val limits: String = "",
    val suitableFor: String = "",
    val documents: List<String> = emptyList(),
    val source: String = "",
    val updatedAt: String = "",
    val verified: Boolean = false,
)

/** Objection-handling entry (round-tripped for cloud sync). */
@Serializable
data class Objection(
    val id: String,
    val text: String = "",
    val probe: String = "",
    val evidence: String = "",
    val response: String = "",
)

/** Attachment metadata (round-tripped for cloud sync; bytes live in cloud storage). */
@Serializable
data class Attachment(
    val id: String,
    val recordType: String = "",
    val recordId: String = "",
    val name: String = "",
    val mime: String = "",
    val size: Long = 0,
    val storagePath: String = "",
    val localPath: String = "",
    val uploaded: Boolean = false,
    val createdAt: String = "",
)

/** Versioned portable payload used by the settings backup/import flow and cloud sync. */
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

/** A big project / account initiative (plan 6.6) — desktop mirror for lossless round-trip. */
@Serializable
data class Project(
    val id: String,
    val customerId: String = "",
    val customerName: String = "",
    val name: String = "",
    val description: String = "",
    val opportunityIds: List<String> = emptyList(),
    val stakeholderIds: List<String> = emptyList(),
    val status: String = "ACTIVE",
    val targetDate: String = "",
    val createdAt: String = "",
)

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

fun Visit.typeEnum(): VisitType = mapLegacyVisitType(type)
fun Visit.outcomeEnum(): Outcome = runCatching { Outcome.valueOf(outcome) }.getOrDefault(Outcome.NONE)

/** The note to display: the AI-formatted version for the active language, falling back to the raw note. */
fun Visit.notesFor(en: Boolean): String = (if (en) notesEn else notesAr).ifBlank { notes }
