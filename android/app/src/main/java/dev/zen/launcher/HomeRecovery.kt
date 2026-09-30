package dev.zen.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class HomeChoice(val component:ComponentName,val label:String)
class HomeRecovery(private val context:Context){
    private val pm=context.packageManager
    private val own=ComponentName(context,"${context.packageName}.HomeEntry")
    fun choices():List<HomeChoice> = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)
        .filter{it.activityInfo.packageName!=context.packageName&&it.activityInfo.exported&&it.activityInfo.enabled
            &&!it.activityInfo.name.endsWith(".FallbackHome")&&!it.activityInfo.name.endsWith(".ResolverActivity")}
        .map{HomeChoice(ComponentName(it.activityInfo.packageName,it.activityInfo.name),it.loadLabel(pm).toString())}
        .filter{it.label.isNotBlank()}.distinctBy{it.component}
    fun enabled()=pm.getComponentEnabledSetting(own)!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    fun isDefaultHome():Boolean {
        if(!enabled())return false
        // Being an enabled HOME candidate is not the same as holding the user's default role.
        // Some vendor builds do not expose a working role service; only then ask the resolver.
        val roleHeld=runCatching {
            context.getSystemService(RoleManager::class.java)?.let { roles ->
                if(roles.isRoleAvailable(RoleManager.ROLE_HOME))roles.isRoleHeld(RoleManager.ROLE_HOME) else null
            }
        }.getOrNull()
        return roleHeld ?: runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName==context.packageName
        }.getOrDefault(false)
    }
    fun enable(){pm.setComponentEnabledSetting(own,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)}
    fun exit(choice:HomeChoice){
        require(choices().any{it.component==choice.component}){tr(R.string.ui_fad0fd2bcec1, "原桌面已不可用，未更改禅的桌面资格。")}
        // Confirm another real HOME can launch before removing only our HOME alias.
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(choice.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        pm.setComponentEnabledSetting(own,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)
        // MainActivity's ordinary launcher icon stays enabled. Only explicit opt-in can re-enable HOME.
    }
}
