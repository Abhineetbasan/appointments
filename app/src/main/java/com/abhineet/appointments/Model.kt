package com.abhineet.appointments

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One appointment. `start` is a point in time (milliseconds since 1970). */
data class Appointment(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val category: String = "Health",
    val start: Long,
    val durationMin: Int = 60,
    val reminderMin: Int = 1440,
    val location: String = "",
    val contact: String = "",
    val phone: String = "",
    val notes: String = "",
    val done: Boolean = false,
    /** The matching event in the phone's calendar, once synced. */
    val eventId: Long? = null,
    val calendarId: Long? = null,
) {
    val end: Long get() = start + durationMin * 60_000L
}

val CATEGORIES = listOf("Health", "Car", "Home", "Admin & money", "Family", "Personal")

val DURATIONS = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1.5 hours", 120 to "2 hours", 240 to "Half day")

val REMINDERS = listOf(0 to "None", 15 to "15 min before", 60 to "1 hour before", 1440 to "1 day before", 2880 to "2 days before", 10080 to "1 week before")

/** Saves appointments as a small JSON file inside the app, plus the chosen calendar. */
class Store(private val context: Context) {
    private val file get() = File(context.filesDir, "appointments.json")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var calendarId: Long
        get() = prefs.getLong("calendarId", -1L)
        set(v) = prefs.edit().putLong("calendarId", v).apply()

    var calendarName: String
        get() = prefs.getString("calendarName", "") ?: ""
        set(v) = prefs.edit().putString("calendarName", v).apply()

    fun load(): List<Appointment> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Appointment>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(context.filesDir, "appointments.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun toJson(a: Appointment) = JSONObject().apply {
        put("id", a.id); put("title", a.title); put("category", a.category)
        put("start", a.start); put("durationMin", a.durationMin); put("reminderMin", a.reminderMin)
        put("location", a.location); put("contact", a.contact); put("phone", a.phone)
        put("notes", a.notes); put("done", a.done)
        if (a.eventId != null) put("eventId", a.eventId)
        if (a.calendarId != null) put("calendarId", a.calendarId)
    }

    private fun fromJson(o: JSONObject) = Appointment(
        id = o.getString("id"),
        title = o.optString("title"),
        category = o.optString("category", "Personal"),
        start = o.getLong("start"),
        durationMin = o.optInt("durationMin", 60),
        reminderMin = o.optInt("reminderMin", 0),
        location = o.optString("location"),
        contact = o.optString("contact"),
        phone = o.optString("phone"),
        notes = o.optString("notes"),
        done = o.optBoolean("done"),
        eventId = if (o.has("eventId")) o.getLong("eventId") else null,
        calendarId = if (o.has("calendarId")) o.getLong("calendarId") else null,
    )
}
