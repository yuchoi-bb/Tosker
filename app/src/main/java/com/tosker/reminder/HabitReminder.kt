package com.tosker.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tosker.MainActivity
import com.tosker.R
import com.tosker.settings.SettingsStore
import java.util.Calendar

/** 매일 저녁 습관 기록을 알리는 알림 */
object HabitReminder {

    /** 알림이 울릴 시각 (저녁 8시 3분) */
    const val HOUR = 20
    const val MINUTE = 3

    private const val CHANNEL_ID = "tosker_habit_reminder"
    private const val NOTIFICATION_ID = 2003
    private const val REQUEST_CODE = 2003

    /** 다음 알림을 예약한다. 이미 예약돼 있으면 덮어쓴다. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis()
        val pending = alarmPendingIntent(context)

        // 정확한 알람을 쓸 수 없는 기기에서는 대략적인 시각으로라도 예약한다.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    /** 설정에 따라 예약하거나 취소한다. */
    fun apply(context: Context) {
        if (SettingsStore(context).loadReminderEnabled()) schedule(context) else cancel(context)
    }

    fun showNotification(context: Context) {
        createChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_tosker)
            .setContentTitle("오늘 기록하셨나요?")
            .setContentText("눌러서 오늘 한 일을 체크하세요.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "습관 기록 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "매일 저녁 습관 기록을 알립니다."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, HabitReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** 오늘 시각이 지났으면 내일 같은 시각으로 */
    private fun nextTriggerMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }
}

/** 예약된 시각에 알림을 띄우고 다음 날을 다시 예약한다. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (SettingsStore(context).loadReminderEnabled()) {
            HabitReminder.showNotification(context)
        }
        // 하루 뒤로 다시 예약 (setExact는 1회성이라 매번 다시 걸어야 한다)
        HabitReminder.apply(context)
    }
}

/** 재부팅하면 예약이 사라지므로 다시 건다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            HabitReminder.apply(context)
        }
    }
}
