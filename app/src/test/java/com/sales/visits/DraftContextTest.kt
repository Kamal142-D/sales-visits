package com.sales.visits

import org.junit.Assert.assertTrue
import org.junit.Test

class DraftContextTest {

    private val cust = Customer(id = "c1", name = "Acme", industry = "Printing")
    private val contact = ContactPerson(id = "p1", name = "Sara", jobTitle = "Buyer", email = "sara@acme.com")

    @Test fun customerMessageNamesRecipient() {
        val ctx = DraftContext.build(DraftType.POST_VISIT, cust, null, contact, "", null)
        assertTrue(ctx.contains("After visit"))
        assertTrue(ctx.contains("Sara"))
        assertTrue(ctx.contains("Acme"))
    }

    @Test fun internalTypeMarksProcurementRecipient() {
        val ctx = DraftContext.build(DraftType.PROCUREMENT_PRICE, cust, null, contact, "", null)
        assertTrue(ctx.contains("internal"))
    }

    @Test fun quoteFollowUpIncludesQuoteTotal() {
        val q = Quote(id = "q1", number = "Q-0007", customerName = "Acme", status = "SENT", currency = "SAR", lines = listOf(QuoteLine(quantity = 2.0, unitPrice = 250.0)))
        val ctx = DraftContext.build(DraftType.QUOTE_FOLLOWUP, cust, null, contact, "", q)
        assertTrue(ctx.contains("Q-0007"))
        assertTrue(ctx.contains("500"))
    }

    @Test fun missingRecipientMarkedNotInvented() {
        val ctx = DraftContext.build(DraftType.MEETING_REQUEST, cust, null, null, "", null)
        assertTrue(ctx.contains("(not recorded)"))
    }
}
