package dev.zen.launcher

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PriorityNotice(val key:String,val pkg:String,val title:String,val text:String,val time:Long,val open:PendingIntent?)
object PriorityInbox{
    val notices=MutableStateFlow<List<PriorityNotice>>(emptyList())
    val connected=MutableStateFlow(false)
    fun access(context:Context)=context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context,PriorityNotificationService::class.java))
    fun basic(context:Context)=setOfNotNull(context.packageName,
        runCatching{context.getSystemService(TelecomManager::class.java).defaultDialerPackage}.getOrNull(),
        runCatching{Telephony.Sms.getDefaultSmsPackage(context)}.getOrNull())
}

/** Read-only inbox. No cancellation/snooze API is called, including for excluded packages. */
class PriorityNotificationService:NotificationListenerService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var watching:Job?=null
    override fun onListenerConnected(){
        PriorityInbox.connected.value=true
        watching?.cancel()
        watching=scope.launch{zen().store.state.map{it.settings.priorityPackages}.distinctUntilChanged().collect{refresh()}}
    }
    override fun onNotificationPosted(sbn:StatusBarNotification?){refresh()}
    override fun onNotificationRemoved(sbn:StatusBarNotification?){refresh()}
    private fun refresh(){
        if(!PriorityInbox.connected.value)return
        val selected=zen().store.state.value.settings.priorityPackages
        val basic=PriorityInbox.basic(this)
        PriorityInbox.notices.value=runCatching{activeNotifications.orEmpty().asSequence()
            .filter{NotificationSelection.include(it.packageName,it.notification.category,basic,selected)}
            .sortedByDescending{it.postTime}.take(30).map{n->
                val extras=n.notification.extras
                PriorityNotice(n.key,n.packageName,extras.getCharSequence("android.title")?.toString()?.take(120)?:tr(R.string.ui_2865b76c7d69, "新通知"),
                    (extras.getCharSequence("android.bigText")?:extras.getCharSequence("android.text"))?.toString()?.take(400)?:tr(R.string.ui_b34e3d56f571, "打开应用查看"),n.postTime,n.notification.contentIntent)
            }.toList()}.getOrDefault(emptyList())
    }
    override fun onListenerDisconnected(){watching?.cancel();PriorityInbox.connected.value=false;PriorityInbox.notices.value=emptyList()}
    override fun onDestroy(){scope.cancel();PriorityInbox.connected.value=false;PriorityInbox.notices.value=emptyList();super.onDestroy()}
}
