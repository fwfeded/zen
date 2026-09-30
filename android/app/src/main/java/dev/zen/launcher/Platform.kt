package dev.zen.launcher

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import java.util.UUID

class Platform(private val context: Context, private val store: StateStore) {
    companion object { const val TIMER_CHANNEL = "timer-completion-v2" }
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val monitorIntent = Intent(context,UsageMonitorService::class.java)
    fun hasUsage(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    fun hasNotifications(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun hasExact() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun timerChannelId(): String {
        // Preserve an explicit user choice on the old channel, including a blocked channel.
        val legacy=notifications.getNotificationChannel("timer")
        val customized=legacy!=null&&(legacy.importance==NotificationManager.IMPORTANCE_NONE||legacy.hasUserSetImportance()||
            legacy.sound!=null||legacy.shouldVibrate()||(Build.VERSION.SDK_INT>=30&&legacy.hasUserSetSound()))
        return if(customized) "timer" else TIMER_CHANNEL
    }
    fun hasTimerNotifications() = hasNotifications() &&
        notifications.getNotificationChannel(timerChannelId())?.importance != NotificationManager.IMPORTANCE_NONE
    fun createChannels() {
        notifications.createNotificationChannel(NotificationChannel("monitor", tr(R.string.ui_5cb8c40e95fc, "应用目标监测"), NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null); setShowBadge(false) })
        listOf("goal" to tr(R.string.ui_916762880a4b, "目标提醒")).forEach { (id,name) ->
            notifications.createNotificationChannel(NotificationChannel(id,name,NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null,null); enableVibration(false); setShowBadge(false); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        // Android locks channel behavior after creation. The old release created a silent,
        // non-vibrating channel; a dedicated completion channel fixes its default behavior.
        notifications.createNotificationChannel(NotificationChannel(TIMER_CHANNEL,tr(R.string.ui_7313ff343cec, "专注计时到点"),NotificationManager.IMPORTANCE_HIGH).apply {
            description=tr(R.string.ui_3c9f155f5d3b, "专注与休息结束时提示；声音和振动可在系统中调整")
            setSound(null,null);enableVibration(true);vibrationPattern=longArrayOf(0,180,100,180)
            setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
    }
    fun activityIntent(goal: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (goal != null) intent.putExtra("goal",goal)
        return PendingIntent.getActivity(context, goal?.hashCode() ?: 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun action(action: String, id: String = ""): PendingIntent = PendingIntent.getBroadcast(context, (action+id).hashCode(),
        Intent(context,ReminderReceiver::class.java).setAction(action).putExtra("id",id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun base(channel: String, title: String, body: String) = Notification.Builder(context,channel)
        .setSmallIcon(R.drawable.ic_notification).setColor(Color.rgb(71,100,107)).setContentTitle(title).setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(activityIntent()).setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(tr(R.string.ui_a23bde5d7e57, "禅 · 计时提醒")).build())
    fun monitorNotification(): Notification = base("monitor",tr(R.string.ui_0ed41fd1bdec, "应用目标提醒正在运行"),tr(R.string.ui_ee7d6344433f, "只累计目标应用在前台且解锁的时间"))
        .setOngoing(true).addAction(Notification.Action.Builder(null,tr(R.string.ui_96b368343779, "暂停监测"),action("stop-monitor")).build()).build()
    fun goalNotification(g: Goal) {
        if (!hasNotifications()) return
        notifications.notify(g.id.hashCode(),base("goal",tr(R.string.ui_0e96f32ff995, "已达到应用提醒时长"),tr(R.string.ui_6956f944c683, "%1\$s · 已使用 %2\$s", g.text, durationText(g.usedMs)))
            .setContentIntent(activityIntent(g.id)).setAutoCancel(true)
            .addAction(Notification.Action.Builder(null,tr(R.string.ui_76134ed6ac5a, "查看目标"),activityIntent(g.id)).build())
            .addAction(Notification.Action.Builder(null,tr(R.string.ui_326a19bcba2e, "延后提醒"),action("snooze",g.id)).build())
            .addAction(Notification.Action.Builder(null,tr(R.string.ui_c479370b881f, "结束目标"),action("end-goal",g.id)).build()).build())
        vibrate()
    }
    private fun vibrate() {
        if (store.state.value.settings.vibration) context.getSystemService(android.os.Vibrator::class.java)
            .vibrate(android.os.VibrationEffect.createOneShot(100,android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
    fun cancelGoal(id: String) { notifications.cancel(id.hashCode()) }
    @Synchronized fun settleTimer() {
        store.update { FocusLedger.advance(it,SystemClock.elapsedRealtime(),System.currentTimeMillis(),bootId(context),currentZone()) }
        val t = store.state.value.timer
        if (t.status == "complete" && !t.completionNotified && !t.completionAcknowledged && hasTimerNotifications()) {
            notifications.notify(102,base(timerChannelId(),if(t.mode=="focus") tr(R.string.ui_66ee968257ec, "专注完成") else tr(R.string.ui_91dcd1703b98, "休息结束"),if(t.mode=="focus") tr(R.string.ui_a6043c8cc374, "这一段已记入专注统计，准备好后开始休息。") else tr(R.string.ui_cb4e5e1a7912, "准备好后，再开始专注。"))
                .setCategory(Notification.CATEGORY_REMINDER).setOnlyAlertOnce(true).setAutoCancel(true).build())
            store.update { if(it.timer.generation==t.generation) it.copy(timer=it.timer.copy(completionNotified=true)) else it }
        }
    }
    @Synchronized fun acknowledgeTimer(generation:String) {
        val timer=store.state.value.timer
        if(timer.generation!=generation||timer.status!="complete")return
        if(store.update { it.copy(timer=it.timer.copy(completionAcknowledged=true)) })notifications.cancel(102)
    }
    @Synchronized fun startAfterCompletion(generation:String) {
        val timer=store.state.value.timer
        if(timer.generation==generation&&timer.status=="complete")toggleTimer()
    }
    @Synchronized fun toggleTimer() {
        if (store.update { FocusLedger.toggle(it,SystemClock.elapsedRealtime(),System.currentTimeMillis(),bootId(context),currentZone()) }) scheduleTimer()
    }
    @Synchronized fun startTodoFocus(id:String,durations:Pair<Int,Int>?=null):Boolean {
        var started=false
        val saved=store.update {
            val next=FocusFlow.start(it,id,SystemClock.elapsedRealtime(),System.currentTimeMillis(),bootId(context),currentZone(),durations)
            started=next.todos.any{task->task.id==id&&!task.done}&&next.timer.status=="running"&&next.timer.mode=="focus"&&next.timer.todoId==id
            next
        }
        if(saved)scheduleTimer()
        return saved&&started
    }
    @Synchronized fun endTimer() {
        if (store.update { FocusLedger.end(it,SystemClock.elapsedRealtime(),System.currentTimeMillis(),bootId(context),currentZone()) }) scheduleTimer()
    }
    fun clearHistory() { store.update { FocusLedger.clear(it,SystemClock.elapsedRealtime(),System.currentTimeMillis(),bootId(context),currentZone()) } }
    @Synchronized fun scheduleTimer() {
        // One stable PendingIntent carries the latest generation; stale broadcasts are checked in the receiver.
        val old = PendingIntent.getBroadcast(context,102,Intent(context,ReminderReceiver::class.java).setAction("timer"),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (old != null) alarms.cancel(old)
        val t=store.state.value.timer
        if(t.status!="complete") notifications.cancel(102)
        if(t.status!="running") return
        val pending=PendingIntent.getBroadcast(context,102,Intent(context,ReminderReceiver::class.java).setAction("timer").putExtra("id",t.generation),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if(hasExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,t.deadlineMs,pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,t.deadlineMs,pending)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,t.deadlineMs,pending)
        }
    }
    fun stopMonitoring(message: String = tr(R.string.ui_bed5f46226f8, "监测已暂停。目标和累计用时已保留。")) {
        store.update { it.copy(goals=it.goals.map { g->g.copy(active=false) },monitorMessage=message,usage=UsageCursor()) }
        context.stopService(monitorIntent)
        notifications.cancel(101)
    }
    fun startMonitoring(): Boolean {
        if(!hasUsage() || !hasNotifications()) return false
        if(!store.update { s -> if(s.usage.wallMs==0L) s.copy(usage=UsageCursor(
            wallMs=System.currentTimeMillis(),boot=bootId(context),offsetMs=System.currentTimeMillis()-SystemClock.elapsedRealtime(),
            unlocked=!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked),monitorMessage="") else s.copy(monitorMessage="") }) return false
        return try { context.startForegroundService(monitorIntent); true }
        catch (_: Exception) { stopMonitoring(tr(R.string.ui_4b5df9d54f95, "系统暂时不允许启动监测，请保持禅在前台后重试。")); false }
    }
    fun endGoal(id: String) {
        store.update { it.copy(goals=it.goals.filterNot { g->g.id==id }) }
        cancelGoal(id)
        if(store.state.value.goals.none { it.active }) { context.stopService(monitorIntent); notifications.cancel(101) }
    }
    fun snooze(id: String) {
        store.update { s -> s.copy(goals=s.goals.map { g-> if(g.id==id && g.active) g.copy(thresholdMs=g.usedMs+s.settings.snoozeDurationSeconds*1_000L,notified=false) else g }) }
        cancelGoal(id)
    }
    fun clearAll() {
        stopMonitoring()
        endTimer()
        notifications.cancelAll()
        if(store.update { AppState() })ThemeCards.cleanUnused()
        context.filesDir.listFiles()?.filter { it.name.startsWith("zen-state-damaged-") }?.forEach { it.delete() }
    }
}

class ReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val app=context.zen(); val id=intent.getStringExtra("id") ?: ""
        when(intent.action) {
            "timer" -> if(app.store.state.value.timer.generation==id && app.store.state.value.timer.status=="running") app.platform.settleTimer()
            "snooze" -> app.platform.snooze(id)
            "end-goal" -> app.platform.endGoal(id)
            "stop-monitor" -> app.platform.stopMonitoring()
        }
    }
}
class RecoveryReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val app=context.zen()
        app.platform.settleTimer()
        app.platform.scheduleTimer()
        if(intent.action==Intent.ACTION_TIME_CHANGED) app.platform.stopMonitoring(tr(R.string.ui_d062b2861a40, "系统时间已调整，目标监测已暂停。请重新进入进行中的目标恢复。"))
    }
}
