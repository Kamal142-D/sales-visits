package com.sales.visits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrevisitContextTest {

    private val cust = Customer(id = "c1", name = "Acme", industry = "Printing", city = "Riyadh")

    @Test fun includesCustomerAndMarksMissing() {
        val ctx = PrevisitContext.build(cust, null, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(ctx.contains("Acme"))
        assertTrue(ctx.contains("Printing"))
        // Nothing recorded → explicit "(not recorded)" and "(none)" markers, so the model won't invent.
        assertTrue(ctx.contains("(not recorded)"))
        assertTrue(ctx.contains("Contacts: (not recorded)"))
        assertTrue(ctx.contains("(none selected"))
    }

    @Test fun includesFocusedOpportunityDetails() {
        val opp = Opportunity(id = "o1", title = "Printers deal", customerName = "Acme", stage = "QUOTE", need = "faster output", closeDate = "2026-10-01")
        val ctx = PrevisitContext.build(cust, opp, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(ctx.contains("Printers deal"))
        assertTrue(ctx.contains("faster output"))
        assertTrue(ctx.contains("2026-10-01"))
        assertFalse(ctx.contains("(none selected"))
    }

    @Test fun recentInteractionsAreBounded() {
        val visits = (1..20).map { Visit(id = "v$it", client = "Acme", date = "2026-09-%02d".format(it), notesEn = "note $it") }
        val ctx = PrevisitContext.build(cust, null, visits, emptyList(), emptyList(), emptyList(), limit = 6)
        // Only the 6 most recent are included (bounded request — plan 4.6).
        assertTrue(ctx.contains("note 20"))
        assertFalse(ctx.contains("note 1\n"))
        val lines = ctx.lines().count { it.startsWith("- [2026-09") }
        assertTrue("expected <= 6 recent lines, got $lines", lines <= 6)
    }

    @Test fun listsOpenTasksAndQuotes() {
        val tasks = listOf(PlanItem(id = "t1", client = "Acme", date = "2026-09-14", action = "send catalog", status = "OPEN"))
        val quotes = listOf(Quote(id = "q1", number = "Q-0001", customerName = "Acme", status = "SENT", currency = "SAR", lines = listOf(QuoteLine(quantity = 1.0, unitPrice = 500.0))))
        val ctx = PrevisitContext.build(cust, null, emptyList(), emptyList(), quotes, tasks)
        assertTrue(ctx.contains("send catalog"))
        assertTrue(ctx.contains("Q-0001"))
        assertTrue(ctx.contains("500"))
    }
}
