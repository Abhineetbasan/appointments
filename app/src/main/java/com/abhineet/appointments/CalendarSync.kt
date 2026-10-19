package com.abhineet.appointments

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class DeviceCalendar(val id: Long, val name: String, val account: String, val isGoogle: Boolean)

/**
 * Writes appointments straight into a calendar on the phone.
 * When that calendar belongs to your Google account, Android syncs it to
 * Google Calendar on its own, and the reminder becomes a normal phone notification.
 */
object CalendarSync {

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(ctx: Context) = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Calendars on the phone that the app may add events to. Google calendars first. */
    fun calendars(ctx: Context): List<DeviceCalendar> {
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<DeviceCalendar>()
        ctx.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(4) < Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    isGoogle = c.getString(3) == "com.google",
                )
            }
        }
        return out.sortedByDescending { it.isGoogle }
    }

    /** Creates or updates the calendar event for [a]. Returns the event id, or null if it failed. */
    fun upsert(ctx: Context, calendarId: Long, a: Appointment): Long? {
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, a.title)
            put(Events.DTSTART, a.start)
            put(Events.DTEND, a.end)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(Events.EVENT_LOCATION, a.location)
            put(Events.DESCRIPTION, description(a))
            put(Events.HAS_ALARM, if (a.reminderMin > 0) 1 else 0)
        }

        var id = a.eventId
        // Moved to a different calendar: remove the old event and create a new one.
        if (id != null && a.calendarId != null && a.calendarId != calendarId) {
            delete(ctx, id)
            id = null
        }
        if (id != null) {
            val updated = cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null)
            if (updated == 0) id = null // the event was deleted in the calendar app
        }
        if (id == null) {
            val uri = cr.insert(Events.CONTENT_URI, values) ?: return null
            id = ContentUris.parseId(uri)
        }

        cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
        if (a.reminderMin > 0) {
            cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, id)
                put(Reminders.MINUTES, a.reminderMin)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
        return id
    }

    fun delete(ctx: Context, eventId: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    private fun description(a: Appointment) = buildList {
        add("Category: ${a.category}")
        val who = listOf(a.contact, a.phone).filter { it.isNotBlank() }.joinToString(", ")
        if (who.isNotBlank()) add("Contact: $who")
        if (a.notes.isNotBlank()) add("Notes: ${a.notes}")
    }.joinToString("\n")
}
