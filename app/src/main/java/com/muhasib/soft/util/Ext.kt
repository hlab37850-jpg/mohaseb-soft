package com.muhasib.soft.util

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun nowTimeStr(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

fun nowFullStr(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

fun fmt(n: Double): String =
    if (n == Math.floor(n) && !n.isInfinite()) n.toLong().toString()
    else String.format(Locale.US, "%.2f", n)

fun parseD(s: String?, d: Double = 0.0): Double = try {
    s?.trim()?.toDouble() ?: d
} catch (e: Exception) {
    d
}

fun Context.shareText(t: String) {
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.putExtra(Intent.EXTRA_TEXT, t)
    startActivity(Intent.createChooser(i, t))
}

fun Context.whatsapp(t: String) {
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.setPackage("com.whatsapp")
    i.putExtra(Intent.EXTRA_TEXT, t)
    try {
        startActivity(i)
    } catch (e: Exception) {
        shareText(t)
    }
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}
