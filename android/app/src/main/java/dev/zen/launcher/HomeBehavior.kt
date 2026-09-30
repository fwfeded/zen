package dev.zen.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Back on a confirmed default HOME has nowhere further to navigate. */
@Composable
fun ZenHomeBackHandler(activity:MainActivity) {
    val isDefault=remember(activity,activity.permissionsVersion) { activity.homeRecovery.isDefaultHome() }
    // A sheet owns its own Back handling. Ordinary app mode keeps Android's normal exit.
    // This does not intercept Home, Recents, opening apps, or the explicit recovery action.
    BackHandler(enabled=isDefault&&activity.panel.isEmpty()) { }
}
