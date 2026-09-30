package dev.zen.launcher

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*
import kotlin.math.abs

class UsageMonitorService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job: Job?=null
    private var stopped=false
    private val screenReceiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            if(intent.action==Intent.ACTION_SCREEN_OFF) {
                scope.launch { sample(); zen().store.update { it.copy(usage=it.usage.copy(pkg="",component="",unlocked=false,screenOn=false)) } }
            } else if(intent.action==Intent.ACTION_USER_PRESENT || intent.action==Intent.ACTION_SCREEN_ON) { beginLoop() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        androidx.core.content.ContextCompat.registerReceiver(this,screenReceiver,IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) },androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int,startId: Int): Int {
        if(!zen().platform.hasUsage() || !zen().platform.hasNotifications() || zen().store.state.value.goals.none { it.active }) { stopSelf(); return START_NOT_STICKY }
        try {
            if(Build.VERSION.SDK_INT>=34) startForeground(101,zen().platform.monitorNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(101,zen().platform.monitorNotification())
        } catch (_: Exception) { zen().platform.stopMonitoring(tr(R.string.ui_6dae69f3d673, "系统拒绝了后台监测，请回到禅重试。")); return START_NOT_STICKY }
        stopped=false
        beginLoop()
        return START_STICKY
    }
    @Synchronized private fun beginLoop() {
        if(job?.isActive==true) return
        job=scope.launch {
            while(isActive && !stopped) {
                if(!zen().platform.hasUsage() || !zen().platform.hasNotifications()) { zen().platform.stopMonitoring(tr(R.string.ui_01203967f76c, "授权已撤销，真实用时监测已暂停。")); break }
                if(zen().store.state.value.goals.none { it.active }) { stopSelf(); break }
                sample()
                if(!getSystemService(PowerManager::class.java).isInteractive) break
                delay(2000)
            }
        }
    }
    @Synchronized internal fun sample() {
        val store=zen().store
        val now=System.currentTimeMillis()
        val elapsed=SystemClock.elapsedRealtime()
        val boot=bootId(this)
        val current=store.state.value.usage
        if(current.wallMs==0L || current.boot!=boot) {
            store.update { it.copy(usage=UsageCursor(wallMs=now,boot=boot,offsetMs=now-elapsed,
                unlocked=!getSystemService(KeyguardManager::class.java).isKeyguardLocked),monitorMessage="") }
            return
        }
        if(abs(current.offsetMs-(now-elapsed))>2500 || now<current.wallMs) { zen().platform.stopMonitoring(tr(R.string.ui_db2de5ef5e50, "系统时间发生变化，监测已暂停。请恢复进行中的目标。")); return }
        // A persisted foreground owner is not proof of continuous use across a long service outage.
        if(now-current.wallMs>120_000 && current.screenOn && current.unlocked) {
            zen().platform.stopMonitoring(tr(R.string.ui_c8dfe07af6eb, "监测曾中断，无法确认的时段未计入。请从进行中的目标恢复。"));return
        }
        val until=now-500 // Leave a short window for events that arrive slightly late.
        if(until<=current.wallMs) return
        try {
            val from=maxOf(current.wallMs,now-120_000)
            val stream=getSystemService(UsageStatsManager::class.java).queryEvents(from,until)
            val events=mutableListOf<UsageEvent>()
            val e=UsageEvents.Event()
            while(stream.hasNextEvent()) {
                stream.getNextEvent(e)
                val kind=when(e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> "resume"
                    UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED -> "pause"
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> "screen-off"
                    UsageEvents.Event.SCREEN_INTERACTIVE -> "screen-on"
                    UsageEvents.Event.KEYGUARD_SHOWN -> "lock"
                    UsageEvents.Event.KEYGUARD_HIDDEN -> "unlock"
                    else -> continue
                }
                events.add(UsageEvent(e.timeStamp,kind,e.packageName ?: "",e.className ?: ""))
            }
            if(from>current.wallMs && events.none{it.kind=="screen-on"||it.kind=="unlock"} &&
                getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                zen().platform.stopMonitoring(tr(R.string.ui_1d3db6b5e8e0, "监测曾中断，未补记未知时段。请从进行中的目标恢复。"));return
            }
            val boundary=if(from>current.wallMs)current.copy(wallMs=from,pkg="",component="")else current
            val result=UsageAccounting.consume(boundary,events,until)
            if(!store.update { s-> if(s.usage==current) UsageAccounting.apply(s,result) else s }) { zen().platform.stopMonitoring(tr(R.string.ui_43a61e730151, "计时记录未能保存，监测已暂停。")); return }
            store.state.value.goals.filter { it.active && !it.notified && it.usedMs>=it.thresholdMs }.forEach { g->
                if(store.update { s->s.copy(goals=s.goals.map { if(it.id==g.id) it.copy(notified=true) else it }) }) zen().platform.goalNotification(g)
            }
        } catch (_: Exception) { zen().platform.stopMonitoring(tr(R.string.ui_db4d06b9ca2b, "使用情况暂时无法读取，监测已暂停，没有补记未知时段。")) }
    }
    override fun onDestroy() {
        stopped=true
        scope.cancel()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
