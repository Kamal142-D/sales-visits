package com.sales.visits

/**
 * Sales analytics (plan 5.1) — PURE and unit-testable. Every metric has an explicit definition and is
 * computed here in code, offline. Money is ALWAYS kept per-currency (SAR and USD are never summed).
 * Dates that are unknown are simply excluded from period counts rather than assumed to be zero.
 */
object Analytics {

    /** Inclusive date range, yyyy-MM-dd. */
    data class DateRange(val start: String, val end: String) {
        fun has(date: String): Boolean = date.isNotBlank() && date >= start && date <= end
    }

    data class Metrics(
        val newProspects: Int,            // customers created in the period whose relationship is PROSPECT
        val completedInteractions: Int,   // visits in period + activities in period that got a real result
        val callsNoAnswer: Int,           // call attempts with no answer (kept separate — not "reached")
        val quotesSent: Int,              // quotes whose sentDate falls in the period
        val won: Int,                     // opportunities closed WON in the period (by stageChangedAt)
        val lost: Int,                    // opportunities closed LOST in the period
        val pipelineByCurrency: Map<String, Double>,  // CURRENT active pipeline value (not period-bound)
        val wonValueByCurrency: Map<String, Double>,  // value of deals won in the period
        val salesByCurrency: Map<String, Double>,     // orders dated in the period (actual sales, not pipeline)
        val followUpOnTime: Int,          // tasks due in period completed on/before their due date
        val followUpTotal: Int,           // tasks due in period (excluding cancelled) — the adherence denominator
    ) {
        /** Follow-up adherence 0..1, or null when there were no due tasks (avoid divide-by-zero). */
        val adherence: Double? get() = if (followUpTotal == 0) null else followUpOnTime.toDouble() / followUpTotal
    }

    fun compute(
        range: DateRange,
        customers: List<Customer>,
        visits: List<Visit>,
        activities: List<Activity>,
        quotes: List<Quote>,
        opportunities: List<Opportunity>,
        orders: List<Order>,
        tasks: List<PlanItem>,
        defaultCurrency: String,
    ): Metrics {
        fun cur(s: String) = s.trim().ifBlank { defaultCurrency.ifBlank { "?" } }
        fun sumBy(pairs: List<Pair<String, Double>>): Map<String, Double> =
            pairs.groupBy({ it.first }, { it.second }).mapValues { e -> e.value.sum() }.filterValues { it != 0.0 }

        val newProspects = customers.count { range.has(it.createdAt) && it.relationshipStatus() == RelationshipStatus.PROSPECT }

        val visitInteractions = visits.count { range.has(it.date) }
        val activityDone = activities.count { range.has(it.date) && activityResultEnum(it.result) != ActivityResult.NO_ANSWER }
        val noAnswer = activities.count { range.has(it.date) && activityResultEnum(it.result) == ActivityResult.NO_ANSWER }

        val quotesSent = quotes.count { it.statusEnum() != QuoteStatus.DRAFT && range.has(it.sentDate) }

        val wonOpps = opportunities.filter { it.stageEnum() == OppStage.WON && range.has(it.stageChangedAt) }
        val lostOpps = opportunities.filter { it.stageEnum() == OppStage.LOST && range.has(it.stageChangedAt) }

        val pipeline = sumBy(opportunities.filter { it.stageEnum().isActive && it.value > 0 }.map { cur(it.currency) to it.value })
        val wonValue = sumBy(wonOpps.filter { it.value > 0 }.map { cur(it.currency) to it.value })
        val sales = sumBy(orders.filter { range.has(it.date) && it.amount > 0 }.map { cur(it.currency) to it.amount })

        val dueTasks = tasks.filter { range.has(it.date) && it.statusEnum() != TaskStatus.CANCELLED }
        val onTime = dueTasks.count { it.statusEnum() == TaskStatus.DONE && it.completedAt.isNotBlank() && it.completedAt <= it.date }

        return Metrics(
            newProspects = newProspects,
            completedInteractions = visitInteractions + activityDone,
            callsNoAnswer = noAnswer,
            quotesSent = quotesSent,
            won = wonOpps.size,
            lost = lostOpps.size,
            pipelineByCurrency = pipeline,
            wonValueByCurrency = wonValue,
            salesByCurrency = sales,
            followUpOnTime = onTime,
            followUpTotal = dueTasks.size,
        )
    }
}

/** Lost-deal review helpers (plan 5.4) — pure. Patterns need a case count; a single loss is not a trend. */
object LostDeals {
    /** The stage a deal was in right before it was marked lost (from its transition history), or "". */
    fun lastStageBeforeLost(o: Opportunity): String =
        o.history.lastOrNull { it.to == OppStage.LOST.name }?.from.orEmpty()

    /** Recurring loss patterns — grouped by competitor and by reason, only where they repeat (count ≥ 2). */
    fun patterns(lost: List<Opportunity>): List<Pair<String, Int>> {
        val byCompetitor = lost.filter { it.competitor.isNotBlank() }
            .groupingBy { it.competitor.trim() }.eachCount().filter { it.value >= 2 }.map { "vs ${it.key}" to it.value }
        val byReason = lost.filter { it.lossReason.isNotBlank() }
            .groupingBy { it.lossReason.trim() }.eachCount().filter { it.value >= 2 }.map { it.key to it.value }
        return (byCompetitor + byReason).sortedByDescending { it.second }
    }
}

/** Weekly review helpers (plan 5.3) — pure. Picks ONE focus for next week from computed signals. */
object WeeklyReview {
    /** Returns a focus key based on which gap is most worth closing first. */
    fun focus(overdue: Int, quotesNoFollowup: Int, oppsNoDecisionMaker: Int, oppsNoNextStep: Int): String = when {
        overdue > 0 -> "FOCUS_OVERDUE"
        quotesNoFollowup > 0 -> "FOCUS_QUOTES"
        oppsNoNextStep > 0 -> "FOCUS_NEXTSTEP"
        oppsNoDecisionMaker > 0 -> "FOCUS_DM"
        else -> "FOCUS_OK"
    }
}
