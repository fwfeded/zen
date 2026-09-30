package dev.zen.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity:ComponentActivity() {
    override fun attachBaseContext(newBase:android.content.Context){UiLanguage.refresh(newBase);super.attachBaseContext(UiLanguage.wrap(newBase))}
    var homeAppsExpanded by mutableStateOf(false)
    var apps by mutableStateOf(emptyList<AppEntry>())
    private var panelNavigation by mutableStateOf(PanelNavigation())
    val hasParentPanel:Boolean get()=panelNavigation.path.size>1
    var panel:String
        get()=panelNavigation.current
        set(value){
            panelNavigation=panelNavigation.open(value)
            if(value.isEmpty())selected=null
        }
    var message by mutableStateOf("")
    var selected by mutableStateOf<AppEntry?>(null)
    var goalId by mutableStateOf("")
    var permissionsVersion by mutableIntStateOf(0)
    private val notificationPromptPreferences by lazy { getSharedPreferences("permission-prompts",MODE_PRIVATE) }
    private var notificationSettingsDialog:android.app.AlertDialog?=null
    private var pendingTodoOverlay=false
    private val overlayPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        permissionsVersion++
        if(pendingTodoOverlay&&TodoOverlayService.allowed(this))setTodoOverlay(true)
        else if(pendingTodoOverlay){pendingTodoOverlay=false;message=tr(R.string.overlay_denied,"未开启悬浮权限，仍可在禅内管理待办。")}
    }
    private val notificationRequest=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionsVersion++
        message=if(granted)tr(R.string.ui_14ce0b312312, "通知已开启") else tr(R.string.ui_1df1e3cfc9af, "通知未开启，仍可使用前台提示。")
        if(Build.VERSION.SDK_INT>=33&&NotificationPermissionFlow.offerSettingsAfterResult(granted,
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)))showNotificationSettingsHelp()
        if(pendingTodoOverlay){if(granted)setTodoOverlay(true)else pendingTodoOverlay=false}
    }
    private val weatherPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionsVersion++
        if(granted)weatherLocation.refresh(true) else message=tr(R.string.ui_79a0539d480d, "未允许定位。可稍后开启，其他功能仍可使用。")
    }
    private val weatherPrecisePermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsVersion++
        if(weatherLocation.hasPermission())weatherLocation.refresh(true)
        else message=tr(R.string.ui_79a0539d480d,"未允许定位。可稍后开启，其他功能仍可使用。")
    }
    private val roleRequest=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsVersion++ }
    val gateway by lazy { LauncherGateway(this) }
    val appUpdates by lazy { AppUpdates(this) }
    val weather by lazy { WeatherClient(this) }
    val weatherLocation by lazy { AutomaticWeatherLocation(this,weather) }
    val homeRecovery by lazy { HomeRecovery(this) }
    var homes by mutableStateOf(emptyList<HomeChoice>())
    private var catalogJob:Job?=null
    private var restoredComponent:String?=null
    fun perform(work:()->Unit,after:()->Unit={})=zen().perform(work,after)
    fun editState(change:(AppState)->AppState)=perform({zen().store.update(change)})
    fun setTodoOverlay(enabled:Boolean){
        if(!enabled){pendingTodoOverlay=false;editState{it.copy(settings=it.settings.copy(todoOverlay=false))};stopService(Intent(this,TodoOverlayService::class.java));return}
        pendingTodoOverlay=true
        if(!TodoOverlayService.allowed(this)){
            runCatching{overlayPermission.launch(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
                .onFailure{pendingTodoOverlay=false;message=tr(R.string.overlay_denied,"未开启悬浮权限，仍可在禅内管理待办。")}
            return
        }
        if(!zen().platform.hasNotifications()){requestNotification();return}
        pendingTodoOverlay=false
        perform({zen().store.update{it.copy(settings=it.settings.copy(todoOverlay=true))}},{
            if(zen().store.state.value.settings.todoOverlay&&!TodoOverlayService.start(this))message=tr(R.string.overlay_start_failed,"悬浮提醒未能启动，请重新开启。")
        })
    }
    fun resumeTodoOverlay(){
        runCatching{startService(Intent(this,TodoOverlayService::class.java).setAction(TodoOverlayService.SHOW))}
            .onFailure{message=tr(R.string.overlay_start_failed,"悬浮提醒未能启动，请重新开启。")}
    }
    private fun refreshApps(force:Boolean=false){
        if(catalogJob?.isActive==true||(!force&&apps.isNotEmpty()))return
        catalogJob=lifecycleScope.launch{
            apps=withContext(Dispatchers.IO){runCatching{gateway.apps()}.getOrDefault(emptyList())}
            restoredComponent?.let{component->selected=apps.find{it.component==component};restoredComponent=null}
        }
    }
    fun showHomeRecovery(){
        panel="home"
        lifecycleScope.launch{homes=withContext(Dispatchers.IO){homeRecovery.choices()}}
    }
    fun exitHome(choice:HomeChoice){runCatching{homeRecovery.exit(choice)}.onFailure{message=it.message?:tr(R.string.ui_985998595bee, "原桌面未能打开，禅仍可继续使用。")}}
    private val appChanges=object:android.content.pm.LauncherApps.Callback(){
        private fun refresh(){refreshApps(true)}
        override fun onPackageAdded(packageName:String,user:android.os.UserHandle){refresh()}
        override fun onPackageRemoved(packageName:String,user:android.os.UserHandle){refresh()}
        override fun onPackageChanged(packageName:String,user:android.os.UserHandle){refresh()}
        override fun onPackagesAvailable(packageNames:Array<out String>,user:android.os.UserHandle,replacing:Boolean){refresh()}
        override fun onPackagesUnavailable(packageNames:Array<out String>,user:android.os.UserHandle,replacing:Boolean){refresh()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Retire this app's former weather-widget host; the home now draws its own weather text.
        val migrations=getSharedPreferences("migrations",MODE_PRIVATE)
        if(!migrations.getBoolean("weather-widget-retired",false)) {
            runCatching{android.appwidget.AppWidgetHost(this,6300).deleteHost()}.onSuccess{
                getSharedPreferences("system-weather-widget",MODE_PRIVATE).edit().clear().apply()
                migrations.edit().putBoolean("weather-widget-retired",true).apply()
            }
        }
        enableEdgeToEdge()
        getSystemService(android.content.pm.LauncherApps::class.java).registerCallback(appChanges)
        setContent { ZenRoot(this) }
        if(savedInstanceState==null)handleIntent(intent) else {
            panelNavigation=PanelNavigation.restore(savedInstanceState.getStringArrayList("panelPath")
                ?:listOf(savedInstanceState.getString("panel","") ?: ""))
            goalId=savedInstanceState.getString("goalId","")
            restoredComponent=savedInstanceState.getString("selectedComponent")
            if(panel=="home")showHomeRecovery()
        }
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putString("panel",panel);outState.putString("goalId",goalId)
        outState.putStringArrayList("panelPath",ArrayList(panelNavigation.path))
        outState.putString("selectedComponent",selected?.component?:restoredComponent)
        super.onSaveInstanceState(outState)
    }
    fun backFromPanel(){
        panelNavigation=panelNavigation.back()
        if(panel.isEmpty())selected=null
    }
    fun openTodoFocus(id:String,action:TodoFocusAction){
        if(action==TodoFocusAction.VIEW)panel="timer"
        else focusTodo(id)
    }
    fun focusTodo(id:String,durations:Pair<Int,Int>?=null){
        var started=false
        perform({started=zen().platform.startTodoFocus(id,durations)},{
            if(started){panel="";if(!zen().platform.hasTimerNotifications())message=tr(R.string.ui_415146ebe85a, "已开始专注；通知未开启，仅能在前台提示。")}
            else {
                val state=zen().store.state.value
                message=when{
                    state.todos.none{it.id==id&&!it.done}->tr(R.string.ui_54a07987b917, "该待办已完成或删除。")
                    state.timer.mode=="rest"->tr(R.string.ui_e451aa83a6ad, "当前正在休息，请先结束本轮。")
                    else->tr(R.string.ui_555fd042783b, "请先结束当前计时，再开始其他待办。")
                }
                panel="timer"
            }
        })
    }
    override fun onResume() {
        super.onResume()
        refreshApps()
        permissionsVersion++
        weather.resume()
        weatherLocation.refresh()
        appUpdates.resume()
        if(pendingTodoOverlay&&TodoOverlayService.allowed(this)&&zen().platform.hasNotifications())setTodoOverlay(true)
        else if(zen().store.state.value.settings.todoOverlay&&TodoOverlayService.allowed(this))TodoOverlayService.start(this)
        perform({
        zen().platform.settleTimer()
        zen().platform.scheduleTimer()
        if(zen().store.state.value.goals.any { it.active } && !zen().platform.hasUsage()) zen().platform.stopMonitoring(tr(R.string.ui_7835310f8ea8, "使用情况授权已撤销，监测已暂停。"))
        else if(zen().store.state.value.goals.any { it.active }) {
            if(zen().platform.hasNotifications()) zen().platform.startMonitoring()
            else zen().platform.stopMonitoring(tr(R.string.ui_0fe795b29e2b, "通知权限已关闭，监测已暂停。"))
        }
        })
    }
    override fun onPause(){homeAppsExpanded=false;weatherLocation.pause();weather.pause();appUpdates.pause();super.onPause()}
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    override fun onDestroy() { notificationSettingsDialog?.dismiss();getSystemService(android.content.pm.LauncherApps::class.java).unregisterCallback(appChanges);weatherLocation.close();weather.close();appUpdates.close();super.onDestroy() }
    private fun handleIntent(intent:Intent) { homeAppsExpanded=false;panel=""; selected=null; intent.getStringExtra("goal")?.let { goalId=it; panel="goal-detail" };if(intent.getBooleanExtra("open-todos",false))panel="todo" }
    fun requestNotification() {
        val granted=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
        val requested=notificationPromptPreferences.getBoolean("notifications-requested",false)
        val canExplain=Build.VERSION.SDK_INT>=33&&shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if(Build.VERSION.SDK_INT>=33&&NotificationPermissionFlow.shouldRequestRuntime(Build.VERSION.SDK_INT,granted,requested,canExplain)) {
            notificationPromptPreferences.edit().putBoolean("notifications-requested",true).apply()
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else openNotificationSettings()
    }
    private fun openNotificationSettings(){
        runCatching{startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE,packageName))}
            .onFailure{external(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}
    }
    private fun showNotificationSettingsHelp(){
        if(isFinishing||isDestroyed||notificationSettingsDialog?.isShowing==true)return
        // Older builds have no request-history flag. If Android declines without a prompt,
        // keep an explicit, cancellable way forward instead of repeating a dead permission request.
        notificationSettingsDialog=android.app.AlertDialog.Builder(this)
            .setTitle(tr(R.string.ui_b033a8231037, "通知未开启"))
            .setMessage(tr(R.string.ui_7c6a0b2204ad, "可在系统设置中开启到点通知，也可以继续使用前台提示。"))
            .setPositiveButton(tr(R.string.ui_0407dbdaf0dd, "打开系统设置")){_,_->openNotificationSettings()}
            .setNegativeButton(tr(R.string.ui_a2666f394298, "暂不开启"),null)
            .setOnDismissListener{notificationSettingsDialog=null}
            .show()
    }
    fun requestWeatherLocation() {
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)weatherLocation.refresh(true)
        else if(notificationPromptPreferences.getBoolean("location-requested",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            external(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
        } else {
            notificationPromptPreferences.edit().putBoolean("location-requested",true).apply()
            weatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    fun openLocationSettings()=external(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun requestWeatherGps() {
        if(weatherLocation.hasPrecisePermission()){weatherLocation.refresh(true);return}
        if(notificationPromptPreferences.getBoolean("precise-location-requested",false)&&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            external(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
        }else {
            notificationPromptPreferences.edit().putBoolean("precise-location-requested",true).apply()
            weatherPrecisePermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    fun usagePermission() = external(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:$packageName")))
    fun exactPermission() {
        if(Build.VERSION.SDK_INT>=31) external(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
    }
    fun homeRole() {
        if(homes.isEmpty()){message=tr(R.string.ui_8ebdd588ac05, "尚未确认可返回的原桌面，暂不启用。请先在此页确认其它桌面已列出。");return}
        runCatching {
            val roles=getSystemService(RoleManager::class.java)
            homeRecovery.enable()
            if(roles.isRoleHeld(RoleManager.ROLE_HOME))message=tr(R.string.ui_bb436e9c4947, "禅已是默认桌面。")
            else if(roles.isRoleAvailable(RoleManager.ROLE_HOME))roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else external(Intent(AndroidSettings.ACTION_HOME_SETTINGS))
        }.onFailure { external(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
    }
    fun timerNotificationSettings() = external(Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE,packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID,zen().platform.timerChannelId()))
    fun external(intent:Intent) { runCatching { startActivity(intent) }.onFailure { message=tr(R.string.ui_1abef80c3874, "系统暂不支持此入口，请从系统设置中打开。") } }
    fun openApp(entry:AppEntry) {
        val s=zen().store.state.value
        if(entry.pkg !in s.settings.reminderPackages) { launch(entry); return }
        selected=entry
        val goal=s.goals.find { it.pkg==entry.pkg }
        if(goal==null) { panel="goal-new"; return }
        if(!zen().platform.hasUsage() || !zen().platform.hasNotifications()) { panel="goal-permissions"; return }
        var ready=false
        perform({
            if(!goal.active)zen().store.update { it.copy(goals=it.goals.map { g->if(g.id==goal.id) g.copy(active=true) else g },usage=UsageCursor(),monitorMessage="") }
            ready=zen().platform.startMonitoring()
        },{if(ready)launch(entry) else {message=tr(R.string.ui_ba3c58b82ede, "请先开启通知和使用情况访问。");panel="goal-permissions"}})
    }
    fun createGoal(text:String,minutes:Int) {
        val entry=selected ?: return
        val trimmed=text.trim()
        if(trimmed.isEmpty() || trimmed.length>60) { message=tr(R.string.ui_350e01e2aa36, "请填写 1–60 字的目标。"); return }
        if(!zen().platform.hasUsage() || !zen().platform.hasNotifications()) { message=tr(R.string.ui_f0bde726bf2e, "开启通知和使用情况访问后，再确认进入。"); return }
        val goal=Goal(pkg=entry.pkg,component=entry.component,label=entry.name,text=trimmed,thresholdMs=minutes*60_000L)
        var ready=false
        perform({
            if(zen().store.update { it.copy(goals=it.goals.filterNot { g->g.pkg==entry.pkg }+goal,monitorMessage="") }){
                ready=zen().platform.startMonitoring()
                if(!ready)zen().platform.endGoal(goal.id)
            }
        },{if(ready){if(!launch(entry))perform({zen().platform.endGoal(goal.id)})}else message=tr(R.string.ui_efa38c6c9a72, "监测未能启动或记录未保存，请重试。")})
    }
    fun launch(entry:AppEntry):Boolean {
        if(gateway.launch(entry)) { panel=""; selected=null; return true }
        message=tr(R.string.ui_057dc1402ac0, "此应用暂时无法打开，请检查是否已卸载或停用。")
        return false
    }
}
