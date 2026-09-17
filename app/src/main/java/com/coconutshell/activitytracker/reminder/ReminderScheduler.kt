
package com.coconutshell.activitytracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun setMorning(enabled: Boolean, hour: Int, minute: Int) =
        set(ReminderKind.MORNING, enabled, hour, minute)

    fun setNight(enabled: Boolean, hour: Int, minute: Int) =
        set(ReminderKind.NIGHT, enabled, hour, minute)

    private fun set(kind: ReminderKind, enabled: Boolean, hour: Int, minute: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("kind", kind.name)
        val requestCode = if (kind == ReminderKind.MORNING) 1001 else 1002
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!enabled) {
            alarmManager.cancel(pending)
            return
        }
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    enum class ReminderKind { MORNING, NIGHT }
}
