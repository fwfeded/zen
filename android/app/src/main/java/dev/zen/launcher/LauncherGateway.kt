package dev.zen.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process

data class AppEntry(val pkg:String,val component:String,val name:String,val icon:Drawable)
class LauncherGateway(private val context:Context) {
    private val launcher=context.getSystemService(LauncherApps::class.java)
    fun apps():List<AppEntry> = launcher.getActivityList(null,Process.myUserHandle())
        .filter { it.componentName.packageName!=context.packageName }
        .distinctBy { it.componentName.packageName }
        .map { AppEntry(it.componentName.packageName,it.componentName.flattenToString(),it.label.toString(),it.getIcon(0)) }
        .sortedBy { it.name.lowercase() }
    fun launch(entry:AppEntry):Boolean = runCatching {
        launcher.startMainActivity(ComponentName.unflattenFromString(entry.component),Process.myUserHandle(),null,null)
    }.isSuccess
}
