package com.jarvis.assistant.util

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.jarvis.assistant.ai.InlineData
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/** Offline calculator: + - * / % ^ ( ) sqrt sin cos tan ln log pi e (angles in degrees). */
object Calc {
    fun eval(src: String): Double {
        val p = P(src.replace(" ", "").lowercase().replace("×", "*").replace("÷", "/"))
        val v = p.expr()
        if (p.i != p.s.length) error("Unexpected '${p.s[p.i]}'")
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun term(): Double {
            var v = pow()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]; val r = pow()
                v = when (op) {
                    '*' -> v * r
                    '/' -> { if (r == 0.0) error("Division by zero"); v / r }
                    else -> v % r
                }
            }
            return v
        }
        fun pow(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') { i++; return b.pow(pow()) }
            return b
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }
        fun atom(): Double {
            if (i >= s.length) error("Unexpected end")
            if (s[i] == '(') {
                i++; val v = expr()
                if (i >= s.length || s[i] != ')') error("Missing )")
                i++; return v
            }
            if (s[i].isDigit() || s[i] == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDouble()
            }
            if (s[i].isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                if (name == "pi") return PI
                if (name == "e") return E
                val a = atom()
                return when (name) {
                    "sqrt" -> sqrt(a)
                    "sin" -> sin(Math.toRadians(a))
                    "cos" -> cos(Math.toRadians(a))
                    "tan" -> tan(Math.toRadians(a))
                    "ln" -> ln(a)
                    "log" -> log10(a)
                    else -> error("Unknown function '$name'")
                }
            }
            error("Unexpected '${s[i]}'")
        }
    }

    fun format(v: Double): String =
        if (v % 1.0 == 0.0 && abs(v) < 1e15) v.toLong().toString() else "%.8g".format(Locale.US, v).trimEnd('0').trimEnd('.')
}

object Units {
    private val length = mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254, "yd" to 0.9144)
    private val mass = mapOf("kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.028349523125)
    private val volume = mapOf("l" to 1.0, "ml" to 0.001, "gal" to 3.785411784)
    private val temp = setOf("c", "f", "k")

    fun convert(v: Double, from: String, to: String): Double? {
        val f = from.lowercase(); val t = to.lowercase()
        if (f in temp && t in temp) return fromC(toC(v, f), t)
        for (tbl in listOf(length, mass, volume)) if (f in tbl && t in tbl) return v * tbl.getValue(f) / tbl.getValue(t)
        return null
    }

    private fun toC(v: Double, u: String) = when (u) { "c" -> v; "f" -> (v - 32) * 5 / 9; else -> v - 273.15 }
    private fun fromC(v: Double, u: String) = when (u) { "c" -> v; "f" -> v * 9 / 5 + 32; else -> v + 273.15 }

    /** "10 km to mi" */
    fun parse(q: String): String {
        val m = Regex("^\\s*(-?[0-9.]+)\\s*([a-zA-Z]+)\\s*(?:to|in|->)\\s*([a-zA-Z]+)\\s*$").find(q)
            ?: return "Format: 10 km to mi   |   100 c to f   |   5 kg to lb"
        val v = m.groupValues[1].toDoubleOrNull() ?: return "Invalid number"
        val r = convert(v, m.groupValues[2], m.groupValues[3]) ?: return "Cannot convert ${m.groupValues[2]} to ${m.groupValues[3]}"
        return "${Calc.format(v)} ${m.groupValues[2]} = ${Calc.format(r)} ${m.groupValues[3]}"
    }
}

/** Removes markdown/code so TextToSpeech reads naturally. */
fun speakable(t: String): String =
    t.replace(Regex("```[\\s\\S]*?```"), " code block ").replace(Regex("https?://\\S+"), " link ")
        .replace(Regex("[*#`_>]"), "").trim()

fun fmtTime(ts: Long): String = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(ts))

object ImageUtil {
    /** Downscales to <= ~2048px and returns base64 JPEG for Gemini inlineData. */
    fun toInline(ctx: Context, uri: Uri): InlineData? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return InlineData("image/jpeg", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
    }
}

/** Inexact reminder (no special alarm permission). Delivery may be a few minutes late. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("text") ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val n = Notification.Builder(c, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("JARVIS reminder").setContentText(text).setAutoCancel(true).build()
        nm.notify(text.hashCode(), n)
    }

    companion object {
        fun schedule(c: Context, text: String, minutes: Int) {
            val am = c.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                c, text.hashCode(), Intent(c, ReminderReceiver::class.java).putExtra("text", text),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi)
        }
    }
}
