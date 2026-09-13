package com.sales.visits

/**
 * Assembles the context handed to the AI for a pre-visit brief (plan 4.1) — PURE and unit-testable.
 *
 * It sends ONLY real, recorded data. Anything absent is written as "(not recorded)" so the model
 * treats it as unknown instead of inventing it. The output is bounded (recent items are capped) so we
 * never dump the whole database into the request (plan 4.6).
 */
object PrevisitContext {

    const val RECENT_LIMIT = 6

    private fun na(s: String) = s.trim().ifBlank { "(not recorded)" }

    /**
     * Builds the context block for [customer], optionally focused on [opp]. [activities] and [visits]
     * should already be the customer's, newest-first; this caps them to [limit].
     */
    fun build(
        customer: Customer,
        opp: Opportunity?,
        visits: List<Visit>,
        activities: List<Activity>,
        quotes: List<Quote>,
        openTasks: List<PlanItem>,
        limit: Int = RECENT_LIMIT,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("CUSTOMER: ${na(customer.name)}")
        sb.appendLine("Industry: ${na(customer.industry)} | Relationship: ${customer.relationshipStatus().labelEn} | City: ${na(customer.city)}")

        val contacts = customer.contacts.filter { it.name.isNotBlank() }
        if (contacts.isEmpty()) sb.appendLine("Contacts: (not recorded)")
        else {
            sb.appendLine("Contacts:")
            contacts.forEach { p ->
                val roles = p.roles.mapNotNull { r -> runCatching { DecisionRole.valueOf(r).labelEn }.getOrNull() }
                sb.appendLine("- ${p.name}${if (p.jobTitle.isNotBlank()) " (${p.jobTitle})" else ""}${if (roles.isNotEmpty()) " [${roles.joinToString(", ")}]" else ""}")
            }
        }

        if (opp != null) {
            sb.appendLine()
            sb.appendLine("FOCUS OPPORTUNITY: ${na(opp.title)}")
            sb.appendLine("Stage: ${opp.stageEnum().labelEn} | Value: ${if (opp.value > 0) opp.value.toString() + " " + opp.currency else "(not recorded)"}")
            sb.appendLine("Need: ${na(opp.need)} | Problem: ${na(opp.problem)} | Product: ${na(opp.product)}")
            sb.appendLine("Budget: ${budgetEnum(opp.budgetStatus).labelEn} | Expected close: ${na(opp.closeDate)} | Competitor: ${na(opp.competitor)}")
            sb.appendLine("Current blocker: ${na(opp.blocker)}")
            sb.appendLine("Recorded next step: ${na(opp.nextStep)}${if (opp.nextDate.isNotBlank()) " (by ${opp.nextDate})" else ""}")
        } else {
            sb.appendLine()
            sb.appendLine("FOCUS OPPORTUNITY: (none selected — give a general brief)")
        }

        sb.appendLine()
        if (openTasks.isEmpty()) sb.appendLine("OPEN PROMISES / TASKS: (none)")
        else {
            sb.appendLine("OPEN PROMISES / TASKS:")
            openTasks.take(limit).forEach { sb.appendLine("- ${na(it.action)}${if (it.date.isNotBlank()) " (due ${it.date})" else ""}") }
        }

        sb.appendLine()
        val recent = (visits.map { it.date to ("Visit: " + it.notesFor(true).ifBlank { it.typeEnum().labelEn }) } +
            activities.map { it.date to (activityTypeEnum(it.type).labelEn + ": " + it.summary.ifBlank { activityResultEnum(it.result).labelEn }) })
            .sortedByDescending { it.first }.take(limit)
        if (recent.isEmpty()) sb.appendLine("RECENT INTERACTIONS: (not recorded)")
        else {
            sb.appendLine("RECENT INTERACTIONS:")
            recent.forEach { (date, line) -> sb.appendLine("- [${date.ifBlank { "?" }}] ${line.trim().take(200)}") }
        }

        sb.appendLine()
        if (quotes.isEmpty()) sb.appendLine("QUOTES: (none)")
        else {
            sb.appendLine("QUOTES:")
            quotes.take(limit).forEach { sb.appendLine("- ${it.number.ifBlank { "quote" }} | ${it.statusEnum().labelEn} | ${QuoteMath.total(it)} ${it.currency}") }
        }
        return sb.toString().trim()
    }
}
