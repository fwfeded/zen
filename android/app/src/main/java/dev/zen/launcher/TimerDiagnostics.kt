package dev.zen.launcher

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, local delivery metadata only: never task text, location or other apps. */
object TimerDiagnostics {
    @Synchronized fun record(context:Context,event:String,detail:String="") {
        runCatching {
            val prefs=context.getSharedPreferences("timer-diagnostics",Context.MODE_PRIVATE)
            val old=JSONArray(prefs.getString("events","[]"))
            val next=JSONArray()
            for(i in (old.length()-39).coerceAtLeast(0) until old.length())next.put(old.get(i))
            next.put(JSONObject().put("time",System.currentTimeMillis()).put("elapsed",SystemClock.elapsedRealtime())
                .put("event",event).put("detail",detail.take(250)))
            prefs.edit().putString("events",next.toString()).apply()
            android.util.Log.i("ZenTimer", "$event $detail")
        }
    }
    fun report(context:Context):String {
        val p=context.zen().platform
        val manager=context.getSystemService(NotificationManager::class.java)
        val audio=context.getSystemService(AudioManager::class.java)
        val channel=manager.getNotificationChannel(p.timerChannelId())
        val timer=context.zen().store.state.value.timer
        return JSONObject().apply {
            put("version",BuildConfig.VERSION_NAME)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("sdk",Build.VERSION.SDK_INT)
            put("notificationsAllowed",p.hasNotifications());put("timerChannelEnabled",p.hasTimerNotifications())
            put("exactAllowed",p.hasExact());put("channel",p.timerChannelId())
            put("importance",channel?.importance);put("soundConfigured",channel?.sound!=null)
            put("vibrationConfigured",channel?.shouldVibrate())
            put("defaultNotificationTonePresent",android.media.RingtoneManager.getActualDefaultRingtoneUri(context,android.media.RingtoneManager.TYPE_NOTIFICATION)!=null)
            put("ringerMode",audio.ringerMode);put("notificationVolume",audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            put("alarmVolume",audio.getStreamVolume(AudioManager.STREAM_ALARM))
            put("interruptionFilter",manager.currentInterruptionFilter)
            put("timerStatus",timer.status);put("completionPosted",timer.completionNotified)
            put("remainingMs",(timer.deadlineMs-SystemClock.elapsedRealtime()).coerceAtLeast(0))
            put("batteryRestricted",context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)
            put("batteryOptimizationExempt",context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
            put("activeTimerNotifications",JSONArray(manager.activeNotifications.filter{it.id in listOf(102,103)}.map{it.id}))
            put("events",JSONArray(context.getSharedPreferences("timer-diagnostics",Context.MODE_PRIVATE).getString("events","[]")))
        }.toString(2)
    }
}
