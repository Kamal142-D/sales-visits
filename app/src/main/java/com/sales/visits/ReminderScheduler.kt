package com.sales.visits

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The time of day follow-up reminders fire (set from settings; defaults to 09:00). */
object ReminderConfig {
    var hour: Int = 9
    var minute: Int = 0
}

object ReminderScheduler {
    private const val CHANNEL_ID = "sales_follow_ups"

    fun notificationsEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, visit: Visit) {
        cancel(context, visit.id)
        if (visit.next.isBlank() || visit.nextDate.isBlank()) return
        val date = runCatching { LocalDate.parse(visit.nextDate) }.getOrNull() ?: return
        val trigger = date.atTime(LocalTime.of(ReminderConfig.hour, ReminderConfig.minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminderIntent(context, visit))
    }

    fun cancel(context: Context, visitId: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(reminderIntent(context, Visit(visitId, "", date = "")))
    }

    fun reschedule(context: Context, visits: List<Visit>) {
        visits.forEach { schedule(context, it) }
    }

    private fun reminderIntent(context: Context, visit: Visit): PendingIntent {
        val intent = Intent(context, FollowUpReminderReceiver::class.java).apply {
            putExtra("visit_id", visit.id)
            putExtra("client", visit.client)
            putExtra("step", visit.next)
        }
        return PendingIntent.getBroadcast(
            context, visit.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun show(context: Context, visitId: String, client: String, step: String) {
        if (!notificationsEnabled(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val en = I18n.en
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, if (en) "Customer follow-ups" else "متابعات العملاء", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = if (en) "Reminders for customer follow-up dates" else "تذكير بمواعيد متابعة العملاء"
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle((if (en) "Follow up: " else "متابعة ") + client)
            .setContentText(step)
            .setStyle(NotificationCompat.BigTextStyle().bigText(step))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(context).notify(visitId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the system call.
        }
    }
}

class FollowUpReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.show(
            context,
            intent.getStringExtra("visit_id").orEmpty(),
            intent.getStringExtra("client").orEmpty(),
            intent.getStringExtra("step").orEmpty(),
        )
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Store(context)
    }
}
