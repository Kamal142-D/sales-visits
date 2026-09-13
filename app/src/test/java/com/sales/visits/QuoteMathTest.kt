package com.sales.visits

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteMathTest {

    private fun line(qty: Double, price: Double, disc: Double = 0.0) =
        QuoteLine(id = "l", description = "x", quantity = qty, unitPrice = price, discountPct = disc)

    @Test fun lineNetAppliesDiscountAndRounds() {
        assertEquals(90.0, QuoteMath.lineNet(line(1.0, 100.0, 10.0)), 0.0001)
        // 3 * 33.33 = 99.99 → rounds to 99.99
        assertEquals(99.99, QuoteMath.lineNet(line(3.0, 33.33)), 0.0001)
    }

    @Test fun subtotalTaxTotal() {
        val q = Quote(id = "q", lines = listOf(line(2.0, 100.0), line(1.0, 50.0, 20.0)), taxPct = 15.0)
        // lines: 200 + 40 = 240 subtotal; tax 15% = 36; total 276
        assertEquals(240.0, QuoteMath.subtotal(q), 0.0001)
        assertEquals(36.0, QuoteMath.tax(q), 0.0001)
        assertEquals(276.0, QuoteMath.total(q), 0.0001)
    }

    @Test fun noTaxByDefault() {
        val q = Quote(id = "q", lines = listOf(line(1.0, 100.0)))   // taxPct defaults 0 — never assumed
        assertEquals(0.0, QuoteMath.tax(q), 0.0001)
        assertEquals(100.0, QuoteMath.total(q), 0.0001)
    }

    @Test fun roundingIsHalfUpTwoDecimals() {
        assertEquals(0.13, QuoteMath.round2(0.125), 0.0)   // 0.125 → 0.13 (HALF_UP)
        assertEquals(2.35, QuoteMath.round2(2.345), 0.0)
    }

    @Test fun discountClampedToValidRange() {
        // A nonsensical 150% discount can't drive the line negative.
        assertEquals(0.0, QuoteMath.lineNet(line(1.0, 100.0, 150.0)), 0.0001)
    }

    @Test fun revisionBumpsVersionAndResets() {
        val sent = Quote(id = "q1", number = "Q-0001", version = 1, status = "SENT", orderId = "o1", sentDate = "2026-09-01")
        val rev = sent.asRevision("q2")
        assertEquals("q2", rev.id)
        assertEquals(2, rev.version)
        assertEquals("Q-0001", rev.number)          // same number
        assertEquals(QuoteStatus.DRAFT, rev.statusEnum())
        assertEquals("", rev.orderId)               // a revision hasn't produced an order
        assertEquals("", rev.sentDate)
    }

    @Test fun quoteJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val q = Quote(
            id = "q1", number = "Q-0001", customerName = "Acme", currency = "SAR", status = "SENT",
            taxPct = 15.0, lines = listOf(line(2.0, 100.0, 5.0)), notes = "n",
        )
        val back = json.decodeFromString(Quote.serializer(), json.encodeToString(Quote.serializer(), q))
        assertEquals(q, back)
    }

    @Test fun statusDefaults() {
        assertEquals(QuoteStatus.DRAFT, quoteStatusEnum(""))
        assertEquals(QuoteStatus.ACCEPTED, quoteStatusEnum("accepted"))
        assertEquals(QuoteStatus.DRAFT, quoteStatusEnum("weird"))
    }

    @Test fun quotesSurviveMergeAndMigration() {
        val cu = Customer(id = "c1", name = "Acme")
        val backup = AppBackup(
            exportedAt = "", visits = emptyList(), customers = listOf(cu), plan = emptyList(),
            quotes = listOf(Quote(id = "q1", customerName = "acme")),
        )
        val migrated = DataMigration.migrate(backup) { "id1" }
        assertEquals("c1", migrated.quotes.single().customerId)

        val base = AppBackup(exportedAt = "", visits = emptyList(), customers = emptyList(), plan = emptyList())
        val merged = SyncMerge.merge(base, base.copy(quotes = listOf(Quote(id = "q1"))), base.copy(quotes = listOf(Quote(id = "q2")))).backup
        assertTrue(merged.quotes.map { it.id }.toSet() == setOf("q1", "q2"))
    }
}
