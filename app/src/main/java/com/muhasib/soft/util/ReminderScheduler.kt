package com.muhasib.soft.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.muhasib.soft.data.db.ReminderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderScheduler {
    fun schedule(ctx: Context, r: ReminderEntity) {
        try {
            val dt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("${r.date} ${r.time}") ?: return
            if (dt.before(Date())) return
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                putExtra("title", r.name)
                putExtra("note", r.note)
                putExtra("id", r.id.toInt())
            }
            val pi = PendingIntent.getBroadcast(ctx, r.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dt.time, pi)
        } catch (e: Exception) {
            // silent
        }
    }
}
