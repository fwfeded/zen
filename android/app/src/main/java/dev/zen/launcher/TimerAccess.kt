package dev.zen.launcher

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TimerAlertStatus(val enabled:Boolean=false,val sound:Boolean=false,val vibration:Boolean=false,
    val dnd:Boolean=false,val muted:Boolean=false) {
    val needsAttention:Boolean get()=!enabled||(!sound&&!vibration)||dnd||(sound&&muted)
    val label:String get()=when {
        !enabled->tr(R.string.timer_alert_blocked,"通知未开启")
        dnd->tr(R.string.timer_alert_dnd,"勿扰模式可能抑制提醒")
        sound&&muted->tr(R.string.timer_alert_muted,"静音或通知音量为零")
        sound&&vibration->tr(R.string.timer_alert_both,"声音与振动")
        sound->tr(R.string.timer_alert_sound,"仅声音")
        vibration->tr(R.string.timer_alert_vibrate,"仅振动")
        else->tr(R.string.timer_alert_silent,"无声音和振动")
    }
}
data class TimerAccess(val notifications:Boolean=false,val timerNotifications:Boolean=false,val exact:Boolean=false,
    val alerts:TimerAlertStatus=TimerAlertStatus())

/** Read binder-backed permissions off the UI thread; refresh after returning from system settings. */
@Composable fun rememberTimerAccess(a:MainActivity):TimerAccess {
    val access by produceState(TimerAccess(),a,a.permissionsVersion) {
        value=withContext(Dispatchers.IO) {
            val p=a.zen().platform
            TimerAccess(p.hasNotifications(),p.hasTimerNotifications(),p.hasExact(),p.timerAlertStatus())
        }
    }
    return access
}
