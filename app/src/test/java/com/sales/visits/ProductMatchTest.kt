package com.sales.visits

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMatchTest {

    private val products = listOf(
        ProductKnowledge(id = "p1", name = "HP Latex 700", applications = "printing on fabric and vinyl banners", materials = "fabric, vinyl", verified = true),
        ProductKnowledge(id = "p2", name = "Eco Solvent X", applications = "outdoor signage on vinyl", materials = "vinyl", verified = false),
        ProductKnowledge(id = "p3", name = "Office Laser", applications = "paper documents", materials = "paper"),
    )

    @Test fun matchesOnNeedKeywords() {
        val r = ProductMatch.forNeed("I need printing on fabric", products)
        assertEquals("p1", r.first().product.id)        // best overlap (printing, fabric)
        assertTrue(r.none { it.product.id == "p3" })     // paper product doesn't match fabric
    }

    @Test fun rankedByOverlapCount() {
        val r = ProductMatch.forNeed("vinyl printing", products)
        // p1 (printing+vinyl) should outrank p2 (vinyl only).
        assertEquals("p1", r.first().product.id)
        assertTrue(r.first().score >= r.last().score)
    }

    @Test fun carriesVerifiedFlagAndWhy() {
        val r = ProductMatch.forNeed("fabric", products)
        val m = r.first { it.product.id == "p1" }
        assertTrue(m.verified)
        assertTrue(m.why.contains("fabric"))
    }

    @Test fun blankOrTinyNeedYieldsNothing() {
        assertTrue(ProductMatch.forNeed("", products).isEmpty())
        assertTrue(ProductMatch.forNeed("a to", products).isEmpty())   // words < 3 chars ignored
    }

    @Test fun productAndObjectionRoundTripAndSurviveMerge() {
        val json = Json { ignoreUnknownKeys = true }
        val p = products[0]
        assertEquals(p, json.decodeFromString(ProductKnowledge.serializer(), json.encodeToString(ProductKnowledge.serializer(), p)))
        val o = Objection(id = "o1", text = "too expensive", response = "value framing")
        assertEquals(o, json.decodeFromString(Objection.serializer(), json.encodeToString(Objection.serializer(), o)))

        val base = AppBackup(exportedAt = "", visits = emptyList(), customers = emptyList(), plan = emptyList())
        val merged = SyncMerge.merge(base, base.copy(products = listOf(p)), base.copy(objections = listOf(o))).backup
        assertEquals(listOf("p1"), merged.products.map { it.id })
        assertEquals(listOf("o1"), merged.objections.map { it.id })
    }
}
