package dev.zen.launcher

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TimerAccess(val notifications:Boolean=false,val timerNotifications:Boolean=false,val exact:Boolean=false)

/** Read binder-backed permissions off the UI thread; refresh after returning from system settings. */
@Composable fun rememberTimerAccess(a:MainActivity):TimerAccess {
    val access by produceState(TimerAccess(),a,a.permissionsVersion) {
        value=withContext(Dispatchers.IO) {
            val p=a.zen().platform
            TimerAccess(p.hasNotifications(),p.hasTimerNotifications(),p.hasExact())
        }
    }
    return access
}
