package com.sales.visits

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProspectModelTest {

    @Test fun relationshipMappingAndDefault() {
        assertEquals(RelationshipStatus.PROSPECT, relationshipEnum("PROSPECT"))
        assertEquals(RelationshipStatus.CUSTOMER, relationshipEnum("CUSTOMER"))
        assertEquals(RelationshipStatus.ARCHIVED, relationshipEnum("archived"))
        // Unknown / blank falls back to CUSTOMER (existing records stay real customers).
        assertEquals(RelationshipStatus.CUSTOMER, relationshipEnum(""))
        assertEquals(RelationshipStatus.CUSTOMER, relationshipEnum("xyz"))
        assertEquals(RelationshipStatus.CUSTOMER, relationshipEnum(null))
    }

    @Test fun newCustomerAndContactFieldsSurviveMigration() {
        val cu = Customer(
            id = "c1", name = "Acme", city = "Riyadh", region = "Central", relationship = "PROSPECT",
            website = "acme.com", source = "event", equipment = "HP Latex 700", apps = "Signage",
            contacts = listOf(ContactPerson(name = "Ali", jobTitle = "Buyer", roles = listOf("PROCUREMENT"))),
        )
        val backup = AppBackup(exportedAt = "", visits = emptyList(), customers = listOf(cu), plan = emptyList())
        val migrated = DataMigration.migrate(backup) { "id1" }
        val out = migrated.customers.single()
        assertEquals("Riyadh", out.city)
        assertEquals("Central", out.region)
        assertEquals("PROSPECT", out.relationship)
        assertEquals("acme.com", out.website)
        assertEquals("Signage", out.apps)
        val ct = out.contacts.single()
        assertEquals("Buyer", ct.jobTitle)
        assertEquals(listOf("PROCUREMENT"), ct.roles)
        assertTrue(ct.id.isNotBlank())   // contact id was backfilled
    }

    @Test fun jsonRoundTripPreservesEverything() {
        val json = Json { ignoreUnknownKeys = true }
        val cu = Customer(
            id = "c1", name = "Acme", website = "a.com", source = "referral",
            relationship = "ARCHIVED", equipment = "X", apps = "Y",
            contacts = listOf(ContactPerson(name = "Ali", id = "p1", roles = listOf("DECISION_MAKER", "TECHNICAL"))),
        )
        val back = json.decodeFromString(Customer.serializer(), json.encodeToString(Customer.serializer(), cu))
        assertEquals(cu, back)
    }
}
