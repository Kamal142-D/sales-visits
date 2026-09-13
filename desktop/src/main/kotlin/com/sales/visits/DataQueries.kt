package com.sales.visits

/**
 * "Ask your data" queries (plan 4.5) — PURE and unit-testable. The APP computes the records and
 * numbers here (offline, exact); the AI is only ever asked to *summarize* these results, never to
 * guess totals from text. Each result is a concrete list of records the UI can show and drill into.
 */
object DataQueries {

    data class Finding(val id: String, val title: String, val subtitle: String = "")

    private fun sameCustomer(customerId: String, customerName: String, cust: Customer): Boolean =
        (customerId.isNotBlank() && customerId == cust.id) ||
            (customerId.isBlank() && customerName.trim().equals(cust.name.trim(), true))

    /** SENT quotes with NO follow-up interaction logged on/after the send date. ("no logged" ≠ "no reply".) */
    fun sentQuotesNoFollowup(quotes: List<Quote>, activities: List<Activity>): List<Finding> =
        quotes.filter { q ->
            q.statusEnum() == QuoteStatus.SENT && q.sentDate.isNotBlank() &&
                activities.none { a ->
                    a.date >= q.sentDate &&
                        ((a.customerId.isNotBlank() && a.customerId == q.customerId) ||
                            a.customerName.trim().equals(q.customerName.trim(), true))
                }
        }.map { Finding(it.id, it.number.ifBlank { "Quote" }, it.customerName) }

    /** Active opportunities whose customer has no contact marked as a decision maker. */
    fun oppsNoDecisionMaker(opps: List<Opportunity>, customers: List<Customer>): List<Finding> =
        opps.filter { o ->
            if (!o.stageEnum().isActive) return@filter false
            val cust = customers.firstOrNull { sameCustomer(o.customerId, o.customerName, it) }
            val hasDM = cust?.contacts?.any { DecisionRole.DECISION_MAKER.name in it.roles } ?: false
            !hasDM
        }.map { Finding(it.id, it.title, it.customerName) }

    /** Active opportunities with no dated next step. */
    fun oppsNoNextStep(opps: List<Opportunity>): List<Finding> =
        opps.filter { it.stageEnum().isActive && (it.nextStep.isBlank() || it.nextDate.isBlank()) }
            .map { Finding(it.id, it.title, it.customerName) }

    /** Prospects (relationship = PROSPECT) optionally filtered by city (blank = all prospects). */
    fun prospects(customers: List<Customer>, city: String = ""): List<Finding> =
        customers.filter { it.relationshipStatus() == RelationshipStatus.PROSPECT && (city.isBlank() || it.city.contains(city.trim(), true)) }
            .map { Finding(it.id, it.name, listOfNotNull(it.city.takeIf { c -> c.isNotBlank() }, it.industry.takeIf { i -> i.isNotBlank() }).joinToString(" · ")) }

    /** Open tasks that are past their due date. */
    fun overdueTasks(tasks: List<PlanItem>, today: String): List<Finding> =
        tasks.filter {
            it.date < today && it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED
        }.map { Finding(it.id, it.action.ifBlank { it.client }, it.client) }
}
