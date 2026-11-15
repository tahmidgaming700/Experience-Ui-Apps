package com.experience.clock.alarm

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Alarms in SQLite inside device-protected storage, so they can be read and rescheduled
 * on LOCKED_BOOT_COMPLETED before the user unlocks (direct boot).
 */
class AlarmStore(context: Context, name: String? = "alarms.db") :
    SQLiteOpenHelper(context.applicationContext.createDeviceProtectedStorageContext(), name, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE alarms (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                hour INTEGER NOT NULL, minute INTEGER NOT NULL,
                enabled INTEGER NOT NULL, days INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT '', ringtone TEXT,
                vibrate INTEGER NOT NULL, snooze_minutes INTEGER NOT NULL,
                snoozed_until INTEGER, skip_until INTEGER
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun values(a: Alarm) = ContentValues().apply {
        put("hour", a.hour); put("minute", a.minute)
        put("enabled", if (a.enabled) 1 else 0); put("days", a.days)
        put("label", a.label)
        if (a.ringtone == null) putNull("ringtone") else put("ringtone", a.ringtone)
        put("vibrate", if (a.vibrate) 1 else 0); put("snooze_minutes", a.snoozeMinutes)
        if (a.snoozedUntil == null) putNull("snoozed_until") else put("snoozed_until", a.snoozedUntil)
        if (a.skipUntil == null) putNull("skip_until") else put("skip_until", a.skipUntil)
    }

    private fun Cursor.alarm() = Alarm(
        id = getLong(0), hour = getInt(1), minute = getInt(2), enabled = getInt(3) != 0, days = getInt(4),
        label = getString(5) ?: "", ringtone = if (isNull(6)) null else getString(6), vibrate = getInt(7) != 0,
        snoozeMinutes = getInt(8), snoozedUntil = if (isNull(9)) null else getLong(9), skipUntil = if (isNull(10)) null else getLong(10),
    )

    private val cols = arrayOf("_id", "hour", "minute", "enabled", "days", "label", "ringtone", "vibrate", "snooze_minutes", "snoozed_until", "skip_until")

    fun all(): List<Alarm> = readableDatabase.query("alarms", cols, null, null, null, null, "hour, minute, _id").use { c ->
        buildList { while (c.moveToNext()) add(c.alarm()) }
    }

    fun get(id: Long): Alarm? = readableDatabase.query("alarms", cols, "_id = ?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) c.alarm() else null
    }

    fun insert(a: Alarm): Alarm = a.copy(id = writableDatabase.insertOrThrow("alarms", null, values(a)))
    fun update(a: Alarm) { writableDatabase.update("alarms", values(a), "_id = ?", arrayOf(a.id.toString())) }
    fun delete(id: Long) { writableDatabase.delete("alarms", "_id = ?", arrayOf(id.toString())) }
}
