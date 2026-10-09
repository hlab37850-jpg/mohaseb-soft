package com.muhasib.soft.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.muhasib.soft.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: ctx.getString(R.string.app_name)
        val note = intent.getStringExtra("note") ?: ""
        val id = intent.getIntExtra("id", 0)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("muhasib_channel", ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
        val n = NotificationCompat.Builder(ctx, "muhasib_channel")
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(note)
            .setAutoCancel(true)
            .build()
        try { nm.notify(id + 1000, n) } catch (e: Exception) { /* missing perm */ }
    }
}
