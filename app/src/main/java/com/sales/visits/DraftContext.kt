package com.sales.visits

/**
 * Assembles the minimal context for the AI to draft a follow-up/email (plan 4.3) — PURE and testable.
 * Sends only recorded facts; unknowns are written as "(not recorded)" so the model asks instead of
 * inventing (no fake price/stock/delivery).
 */
object DraftContext {

    private fun na(s: String) = s.trim().ifBlank { "(not recorded)" }

    fun build(
        type: DraftType,
        customer: Customer,
        opp: Opportunity?,
        recipient: ContactPerson?,
        recentSummary: String,
        quote: Quote?,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("MESSAGE TYPE: ${type.labelEn}")
        if (type.isInternal) sb.appendLine("RECIPIENT: our own purchasing/procurement team (internal)")
        else sb.appendLine("RECIPIENT: ${na(recipient?.name.orEmpty())}${recipient?.jobTitle?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} at ${na(customer.name)}")

        sb.appendLine("CUSTOMER: ${na(customer.name)} | Industry: ${na(customer.industry)}")
        if (opp != null) {
            sb.appendLine("DEAL: ${na(opp.title)} | Stage: ${opp.stageEnum().labelEn}")
            sb.appendLine("Need: ${na(opp.need)} | Product of interest: ${na(opp.product)}")
        }
        if (quote != null) {
            sb.appendLine("QUOTE: ${quote.number.ifBlank { "quote" }} | ${quote.statusEnum().labelEn} | total ${QuoteMath.total(quote)} ${quote.currency}")
        }
        if (recentSummary.isNotBlank()) sb.appendLine("RECENT CONTEXT: ${recentSummary.trim().take(300)}")
        return sb.toString().trim()
    }
}
