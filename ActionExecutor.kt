package com.jarvis.assistant.executor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.jarvis.assistant.automation.JarvisAccessibilityService
import com.jarvis.assistant.data.Settings as AppSettings

/** Runs ONE approved, allow-listed action. Gemini never calls this directly. */
class ActionExecutor(private val ctx: Context) {

    fun run(a: Action, s: AppSettings): Outcome {
        if (!s.master) return Outcome(false, "Master automation switch is OFF (Control tab).")
        val allowed = when (a.type.cap) {
            Cap.APPS -> s.capApps
            Cap.DEVICE -> s.capDevice
            Cap.ACCESS -> s.capAccess
            Cap.COMMS -> s.capComms
            Cap.MEDIA -> s.capMedia
            Cap.NONE -> true
        }
        if (!allowed) return Outcome(false, "${a.type.cap} capability is disabled in the Control tab.")
        return try {
            exec(a)
        } catch (e: ActivityNotFoundException) {
            Outcome(false, "No app on this phone can handle this request.")
        } catch (e: SecurityException) {
            Outcome(false, "Permission problem: ${e.message}")
        } catch (e: Exception) {
            Outcome(false, e.message ?: "Action failed")
        }
    }

    private fun start(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun exec(a: Action): Outcome {
        when (a.type) {
            ActionType.OPEN_APP -> {
                val pm = ctx.packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(q, 0)
                val m = apps.firstOrNull { it.loadLabel(pm).toString().equals(a.arg, true) }
                    ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(a.arg, true) }
                    ?: return Outcome(false, "App '${a.arg}' is not installed on this phone.")
                val i = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
                    ?: return Outcome(false, "Cannot launch '${a.arg}'.")
                start(i)
                return Outcome(true, "Launch request sent for ${m.loadLabel(pm)}.")
            }
            ActionType.OPEN_URL -> {
                if (!a.arg.startsWith("https://") && !a.arg.startsWith("http://"))
                    return Outcome(false, "Only http(s) links are allowed.")
                start(Intent(Intent.ACTION_VIEW, Uri.parse(a.arg)))
                return Outcome(true, "Opened link.")
            }
            ActionType.YOUTUBE_SEARCH -> {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(a.arg))))
                return Outcome(true, "Handed off to YouTube (or browser). I cannot control playback inside YouTube.")
            }
            ActionType.OPEN_SETTINGS -> {
                start(Intent(Settings.ACTION_SETTINGS)); return Outcome(true, "Opened Settings.")
            }
            ActionType.FLASHLIGHT -> {
                val on = when (a.arg.lowercase()) { "on" -> true; "off" -> false; else -> return Outcome(false, "Say flashlight on or off.") }
                val cm = ctx.getSystemService(CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return Outcome(false, "This device has no flashlight.")
                cm.setTorchMode(id, on)
                return Outcome(true, "Flashlight ${if (on) "on" else "off"}.")
            }
            ActionType.VOLUME -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val dir = when (a.arg.lowercase()) {
                    "up" -> AudioManager.ADJUST_RAISE
                    "down" -> AudioManager.ADJUST_LOWER
                    "mute" -> AudioManager.ADJUST_MUTE
                    else -> return Outcome(false, "Volume: up, down or mute.")
                }
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                return Outcome(true, "Volume ${a.arg}.")
            }
            ActionType.GO_BACK, ActionType.GO_HOME, ActionType.SCROLL, ActionType.TAP_TEXT -> {
                val svc = JarvisAccessibilityService.instance
                    ?: return Outcome(false, "Accessibility service is not enabled. Control tab -> Open Accessibility settings.")
                return when (a.type) {
                    ActionType.GO_BACK -> Outcome(svc.back(), "Back")
                    ActionType.GO_HOME -> Outcome(svc.home(), "Home")
                    ActionType.SCROLL -> {
                        val ok = svc.scroll(a.arg.equals("down", true))
                        Outcome(ok, if (ok) "Scrolled ${a.arg}." else "No scrollable view found on screen.")
                    }
                    else -> {
                        val ok = svc.tapText(a.arg)
                        Outcome(ok, if (ok) "Tapped \"${a.arg}\"." else "Could not find a tappable \"${a.arg}\" on screen.")
                    }
                }
            }
            ActionType.MEDIA -> {
                val code = when (a.arg.lowercase()) {
                    "play", "pause", "resume", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> return Outcome(false, "Media: play, pause, next, previous.")
                }
                val am = ctx.getSystemService(AudioManager::class.java)
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                return Outcome(true, "Media key sent. I cannot verify that the player reacted.")
            }
            ActionType.DIAL -> {
                val (num, who) = resolve(a.arg) ?: return noNumber(a.arg)
                start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
                return Outcome(true, "Dialer opened for $who ($num). Press Call yourself.")
            }
            ActionType.SMS_DRAFT -> {
                val (num, who) = resolve(a.arg) ?: return noNumber(a.arg)
                start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", a.arg2))
                return Outcome(true, "SMS draft opened for $who ($num). Press Send yourself.")
            }
            else -> return Outcome(false, "Not a device action.")
        }
    }

    private fun noNumber(arg: String) = Outcome(
        false,
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            "Contacts permission not granted (Control tab). Or give a phone number directly."
        else "No contact or number found for '$arg'."
    )

    /** Returns (number, displayName) from a raw number or by contact-name lookup. */
    private fun resolve(arg: String): Pair<String, String>? {
        if (Regex("^[+0-9 ()-]{5,}$").matches(arg.trim())) return arg.trim() to arg.trim()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%${arg.trim()}%"), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) to c.getString(1) }
        return null
    }
}
