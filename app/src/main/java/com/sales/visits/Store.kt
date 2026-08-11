package com.sales.visits

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Simple synchronous persistence backed by SharedPreferences with a JSON blob.
 * Exposes Compose-observable state so screens recompose on change.
 */
class Store(context: Context) {
    private val sp = context.getSharedPreferences("sales_visits", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var visits by mutableStateOf(load())
        private set

    var theme by mutableStateOf(sp.getString("theme", "auto") ?: "auto")
        private set

    var lang by mutableStateOf(sp.getString("lang", "ar") ?: "ar")
        private set

    init { I18n.en = (lang == "en") }

    fun chooseLang(l: String) {
        lang = l
        I18n.en = (l == "en")
        sp.edit().putString("lang", l).apply()
    }

    private fun load(): List<Visit> =
        runCatching { json.decodeFromString<List<Visit>>(sp.getString("visits", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persist() {
        sp.edit().putString("visits", json.encodeToString(visits)).apply()
    }

    fun upsert(v: Visit) {
        visits = if (visits.any { it.id == v.id }) visits.map { if (it.id == v.id) v else it }
        else visits + v
        persist()
    }

    fun delete(id: String) {
        visits = visits.filter { it.id != id }
        persist()
    }

    fun clearAll() {
        visits = emptyList()
        persist()
    }

    // ---- Today plan (roadmap) ----
    var plan by mutableStateOf(loadPlan())
        private set

    private fun loadPlan(): List<PlanItem> =
        runCatching { json.decodeFromString<List<PlanItem>>(sp.getString("plan", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistPlan() {
        sp.edit().putString("plan", json.encodeToString(plan)).apply()
    }

    private fun pid(): String =
        System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

    fun todayPlan(): List<PlanItem> = plan.filter { it.date == todayIso() }

    fun addPlan(client: String) {
        if (client.isBlank()) return
        plan = plan + PlanItem(pid(), client.trim(), todayIso())
        persistPlan()
    }

    fun togglePlan(id: String) {
        plan = plan.map { if (it.id == id) it.copy(done = !it.done) else it }
        persistPlan()
    }

    fun deletePlan(id: String) {
        plan = plan.filter { it.id != id }
        persistPlan()
    }

    fun chooseTheme(t: String) {
        theme = t
        sp.edit().putString("theme", t).apply()
    }

    fun countThisWeek(): Int = visits.count { inWeek(it.date, 0) }
}
