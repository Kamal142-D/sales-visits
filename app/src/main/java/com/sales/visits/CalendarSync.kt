package com.sales.visits

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone

/**
 * Google Calendar sync (plan 6.4) — app → calendar. Adding a task creates a real event via the Calendar
 * API; its id is stored on the task ([PlanItem.calendarEventId]) so a later edit UPDATES the same event
 * and deleting the task removes it (no duplicates). This is one-directional: changes made inside Google
 * Calendar are NOT pulled back into the app (a Google → app path with a conflict policy is future work).
 * A failed update/delete is not yet queued for retry.
 *
 * Uses the same Google connection as [DriveSync] (one sign-in grants both scopes). calendar.events is a
 * "sensitive" scope; for the user's own account it works, though an unverified-app screen may appear once.
 */
object CalendarSync {
    const val SCOPE_STR = "https://www.googleapis.com/auth/calendar.events"
    private val SCOPE = Scope(SCOPE_STR)
    private const val BASE = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

    fun isConnected(ctx: Context): Boolean =
        GoogleSignIn.getLastSignedInAccount(ctx)?.let { GoogleSignIn.hasPermissions(it, SCOPE) } == true

    private fun token(ctx: Context): String? {
        val acct = GoogleSignIn.getLastSignedInAccount(ctx)?.account ?: return null
        return runCatching { GoogleAuthUtil.getToken(ctx, acct, "oauth2:$SCOPE_STR") }.getOrNull()
    }

    private fun conn(urlStr: String, method: String, token: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

    /** Builds the event body. All-day when no time is given; otherwise a timed event of [minutes]. */
    private fun body(title: String, description: String, dateIso: String, timeHm: String, minutes: Int): JSONObject {
        val o = JSONObject()
        o.put("summary", title.ifBlank { "VisitFlow" })
        if (description.isNotBlank()) o.put("description", description)
        if (timeHm.isBlank()) {
            // All-day event: Google treats `end.date` as EXCLUSIVE, so a one-day event ends the NEXT day.
            val endDate = runCatching { java.time.LocalDate.parse(dateIso).plusDays(1).toString() }.getOrDefault(dateIso)
            o.put("start", JSONObject().put("date", dateIso))
            o.put("end", JSONObject().put("date", endDate))
        } else {
            val tz = TimeZone.getDefault().id
            val startLocal = "${dateIso}T${normHm(timeHm)}:00"
            val dur = if (minutes > 0) minutes else 60
            o.put("start", JSONObject().put("dateTime", startLocal).put("timeZone", tz))
            o.put("end", JSONObject().put("dateTime", addMinutes(dateIso, timeHm, dur)).put("timeZone", tz))
        }
        return o
    }

    /** Creates an event; returns its id (or null on failure). Blocking — call off the main thread. */
    fun create(ctx: Context, title: String, description: String, dateIso: String, timeHm: String, minutes: Int): String? {
        val token = token(ctx) ?: return null
        return runCatching {
            val c = conn(BASE, "POST", token).apply {
                doOutput = true
                outputStream.use { it.write(body(title, description, dateIso, timeHm, minutes).toString().toByteArray()) }
            }
            if (c.responseCode !in 200..299) { c.disconnect(); return null }
            val id = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("id")
            c.disconnect()
            id.ifBlank { null }
        }.getOrNull()
    }

    /** Updates an existing event to match the task. Blocking. */
    fun update(ctx: Context, eventId: String, title: String, description: String, dateIso: String, timeHm: String, minutes: Int): Boolean {
        val token = token(ctx) ?: return false
        return runCatching {
            val c = conn("$BASE/$eventId", "PUT", token).apply {
                doOutput = true
                outputStream.use { it.write(body(title, description, dateIso, timeHm, minutes).toString().toByteArray()) }
            }
            val ok = c.responseCode in 200..299
            c.disconnect(); ok
        }.getOrDefault(false)
    }

    /** Deletes the event. Blocking. */
    fun delete(ctx: Context, eventId: String): Boolean {
        val token = token(ctx) ?: return false
        return runCatching {
            val c = conn("$BASE/$eventId", "DELETE", token)
            val ok = c.responseCode in 200..299 || c.responseCode == 404
            c.disconnect(); ok
        }.getOrDefault(false)
    }

    private fun normHm(hm: String): String {
        val parts = hm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return "%02d:%02d".format(h.coerceIn(0, 23), m.coerceIn(0, 59))
    }

    /** Adds minutes to a local date+time, returning a local ISO datetime (no zone offset). */
    private fun addMinutes(dateIso: String, timeHm: String, minutes: Int): String {
        return runCatching {
            val d = java.time.LocalDate.parse(dateIso)
            val parts = normHm(timeHm).split(":")
            val start = d.atTime(parts[0].toInt(), parts[1].toInt())
            val end = start.plusMinutes(minutes.toLong())
            "%04d-%02d-%02dT%02d:%02d:00".format(end.year, end.monthValue, end.dayOfMonth, end.hour, end.minute)
        }.getOrDefault("${dateIso}T10:00:00")
    }
}
