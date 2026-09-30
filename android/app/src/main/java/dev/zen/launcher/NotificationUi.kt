package dev.zen.launcher

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*

@Composable fun LazyPanel(title:String,a:MainActivity,pinned:@Composable ColumnScope.()->Unit={},listState:LazyListState=rememberLazyListState(),content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxWidth().heightIn(max=650.dp)) {
        PanelHeader(title,a)
        pinned()
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("panel-list"),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)){content()}
    }
}
fun MainActivity.notificationAccess(){
    external(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
fun MainActivity.notificationSettings(pkg:String){external(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE,pkg))}

@Composable fun NotificationPanel(a:MainActivity,s:AppState){
    val notices by PriorityInbox.notices.collectAsState()
    val connected by PriorityInbox.connected.collectAsState()
    val access=remember(a.permissionsVersion){PriorityInbox.access(a)}
    val basic=remember(a.permissionsVersion){PriorityInbox.basic(a)}
    var query by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf(emptySet<String>())}
    var queue by remember{mutableStateOf(emptyList<AppEntry>())}
    var index by remember{mutableIntStateOf(0)}
    when(a.panel){
        "notifications"->LazyPanel(tr(R.string.ui_8ff968dd2fd6, "重点通知"),a){
            item{PanelLink(tr(R.string.ui_fae5742166c7, "选择重点应用"),tr(R.string.ui_320aa2633f7b,"已选 %1\$s 个",s.settings.priorityPackages.size)){a.panel="notification-apps"}}
            if(!access)item{
                Text(tr(R.string.ui_9c56403ddd0f, "开启通知使用权后，禅才能显示通知。内容仅临时留在内存中，不保存、不上传；系统可能隐藏验证码等敏感内容。"),fontSize=13.sp)
                Button(onClick={a.notificationAccess()}){Text(tr(R.string.ui_7e4319d1f13e, "开启通知使用权"))}
            }
            else if(!connected)item{Text(tr(R.string.ui_d98d7700119f, "通知服务尚未连接，可进入系统授权页重新连接。"),fontSize=13.sp);TextButton(onClick={a.notificationAccess()}){Text(tr(R.string.ui_fc49c161f2fb, "检查授权"))}}
            if(access&&connected&&notices.isEmpty())item{Text(tr(R.string.ui_560a6cacc553, "暂无重点通知"))}
            if(access)items(notices,key={it.key}){n->
                Column(Modifier.fillMaxWidth().clickable{
                    runCatching{if(n.open!=null)n.open.send() else a.apps.find{it.pkg==n.pkg}?.let{a.openApp(it)}?:error(tr(R.string.ui_953e2fd04e60, "应用无可用入口"))}
                        .onFailure{a.message=tr(R.string.ui_5c6d3fc5bc0c, "此通知已失效，请打开原应用查看。")}
                }.padding(vertical=12.dp)){
                    Text(a.apps.find{it.pkg==n.pkg}?.name?:n.pkg,fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)
                    Text(n.title,fontSize=16.sp);Text(n.text,fontSize=13.sp)
                }
            }
            item{HorizontalDivider();PanelLink(tr(R.string.ui_a2078faf74dc, "其他应用通知设置")){a.panel="notification-organize"}}
            if(access&&connected)item{PanelLink(tr(R.string.ui_2402ebbde83c, "通知读取权限")){a.notificationAccess()}}
            item{HelpButton(tr(R.string.ui_8ff968dd2fd6,"重点通知"),tr(R.string.ui_2ee697c4cf19,"电话、短信、来电和闹钟自动保留；其它应用由你选择。只筛选这里的展示，系统通知不变。"),compact=false)}
        }
        "notification-apps"->LazyPanel(tr(R.string.ui_fae5742166c7, "选择重点应用"),a,pinned={OutlinedTextField(query,{query=it},label={Text(tr(R.string.ui_06351ff75970, "搜索应用"))},singleLine=true,modifier=Modifier.fillMaxWidth())}){
            item{Text(tr(R.string.ui_d8fa432e545d, "电话、短信与闹钟已自动保留。这里的选择不会改变手机系统通知权限。"),fontSize=13.sp)}
            items(a.apps.filter{it.pkg !in basic&&it.name.contains(query,true)},key={it.pkg}){e->
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){
                    AppIcon(e,Modifier.size(28.dp));Spacer(Modifier.width(12.dp));Text(e.name,Modifier.weight(1f))
                    Checkbox(e.pkg in s.settings.priorityPackages,onCheckedChange={v->a.editState{it.copy(settings=it.settings.copy(priorityPackages=if(v)it.settings.priorityPackages+e.pkg else it.settings.priorityPackages-e.pkg))}})
                }
            }
        }
        "notification-organize"->LazyPanel(tr(R.string.ui_a2078faf74dc, "其他应用通知设置"),a,pinned={
            if(queue.isEmpty())Button(onClick={queue=a.apps.filter{it.pkg in selected};index=0;queue.firstOrNull()?.let{a.notificationSettings(it.pkg)}},enabled=selected.isNotEmpty()){Text(tr(R.string.ui_aa8ebe7d0677, "查看所选应用（%1\$s）", selected.size))}
        }){
            item{Text(tr(R.string.ui_056356433a8b, "禅没有批量撤销其它应用通知权限的系统权限。可选一批应用，逐个进入系统设置手动调整；返回后再点下一项。不会改动重点应用、电话和短信。"),fontSize=13.sp)}
            if(queue.isNotEmpty())item{
                val current=queue.getOrNull(index)
                if(current!=null){Text("${index+1} / ${queue.size} · ${current.name}")
                    Button(onClick={a.notificationSettings(current.pkg)}){Text(tr(R.string.ui_44f89434cc01, "打开此应用通知设置"))}
                    TextButton(onClick={index++;queue.getOrNull(index)?.let{a.notificationSettings(it.pkg)}}){Text(if(index+1<queue.size)tr(R.string.ui_15599b852f3d, "下一个应用")else tr(R.string.ui_7cdcb366c443, "完成查看"))}
                }else Text(tr(R.string.ui_a1cfc601ecb5, "已查看全部应用。通知开关以系统设置为准。"))
                TextButton(onClick={queue=emptyList();index=0}){Text(tr(R.string.ui_9b904c0e2faf, "重新选择"))}
            }else{
                items(a.apps.filter{it.pkg !in basic&&it.pkg !in s.settings.priorityPackages},key={it.pkg}){e->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(e.name,Modifier.weight(1f));Checkbox(e.pkg in selected,onCheckedChange={v->selected=if(v)selected+e.pkg else selected-e.pkg})}
                }
            }
        }
    }
}
