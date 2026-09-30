package dev.zen.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag

@Composable fun PanelHeader(title:String,a:MainActivity,onBack:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        if(a.hasParentPanel||onBack!=null)IconButton(onClick={if(onBack!=null)onBack()else a.backFromPanel()},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_b6050ccef309, "返回上一页")}){LineIcon("back",MaterialTheme.colorScheme.secondary)}
        Text(title,fontFamily=ZenSerif,fontSize=22.sp,modifier=Modifier.weight(1f))
        TextButton(onClick={a.panel=""}){Text(tr(R.string.ui_3fd47edce45b, "关闭"))}
    }
}
@Composable fun PanelColumn(title:String,a:MainActivity,spacing:Dp=8.dp,onBack:(()->Unit)?=null,pinned:@Composable ColumnScope.()->Unit={},content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max=650.dp)) {
        PanelHeader(title,a,onBack)
        pinned()
        Column(Modifier.fillMaxWidth().weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(spacing)){content()}
    }
}
@Composable fun PanelLink(text:String,detail:String="",action:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action).heightIn(min=56.dp).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(text,fontSize=15.sp);if(detail.isNotEmpty())Text(detail,fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)}
        Text("›",fontSize=22.sp,color=MaterialTheme.colorScheme.secondary)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChoiceRow(values:List<Pair<String,String>>,current:String,onChoose:(String)->Unit){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){values.forEach{(key,label)->FilterChip(selected=current==key,onClick={onChoose(key)},label={Text(label)})}}
}
@Composable fun ToggleRow(label:String,checked:Boolean,help:String="",onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));if(help.isNotEmpty())HelpButton(label,help);Switch(checked,onCheckedChange=onChange)}}

/** Explanations are available on demand; status, errors and destructive confirmations stay visible. */
@Composable fun HelpButton(title:String,text:String,compact:Boolean=true){
    var open by remember{mutableStateOf(false)}
    if(compact)IconButton(onClick={open=true},modifier=Modifier.semantics{contentDescription=tr(R.string.help_for,"%1\$s说明",title)}){Text("ⓘ",fontSize=18.sp,color=MaterialTheme.colorScheme.secondary)}
    else TextButton(onClick={open=true}){Text(tr(R.string.usage_help,"使用说明"))}
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick={open=false}){Text(tr(R.string.ui_3fd47edce45b,"关闭"))}})
}

@Composable fun Panels(a:MainActivity,s:AppState,now:Long){
    fun settings(change:(Settings)->Settings){a.editState{it.copy(settings=change(it.settings))}}
    when(a.panel){
        "search"->SearchPanel(a)
        "settings"->PanelColumn(tr(R.string.ui_df3d58c7d84b, "设置"),a){
            PanelLink(tr(R.string.ui_b5d4ccb3e850, "桌面与退出")){a.showHomeRecovery()}
            PanelLink(tr(R.string.ui_8ff968dd2fd6, "重点通知")){a.panel="notifications"}
            PanelLink(tr(R.string.ui_02f0668b446f, "首页应用")){a.panel="favorites"}
            PanelLink(tr(R.string.ui_1832287c33c0, "应用提醒")){a.panel="reminder-settings"}
            HorizontalDivider()
            PanelLink(tr(R.string.ui_9a86da03a656, "专注计时"),tr(R.string.ui_b0689bd31d09, "%1\$s 专注 / %2\$s 休息", durationSecondsText(s.settings.focusDurationSeconds), durationSecondsText(s.settings.restDurationSeconds))){a.panel="timer"}
            PanelLink(tr(R.string.ui_305471d2e26d, "专注统计")){a.panel="calendar"}
            PanelLink(tr(R.string.ui_788db1cfec2a, "主题"),themes[s.settings.theme]?:""){a.panel="themes"}
            PanelLink(tr(R.string.ui_4d947325473f, "天气"),if(s.weather.city==null)""else cityDisplayName(s.weather.city)){a.panel="weather"}
            PanelLink(tr(R.string.ui_b0d7729ed342, "文案设置")){a.panel="preferences"}
            PanelLink(tr(R.string.ui_127527c89c51, "语言 / Language"),UiLanguage.selectionLabel(a)){a.panel="language"}
            ToggleRow(tr(R.string.energy_saving,"省电模式"),s.settings.energySaving,help=tr(R.string.energy_saving_detail,"暂停背景动画，自动定位间隔改为 15 分钟。计时与提醒照常；也会跟随系统省电模式。")){v->settings{it.copy(energySaving=v)}}
            HorizontalDivider()
            PanelLink(tr(R.string.ui_27c47c1d0630, "权限与数据")){a.panel="permissions"}
            val updateState by a.appUpdates.state.collectAsState()
            PanelLink(tr(R.string.ui_42e40f432b6d, "版本更新"),updateState.release?.let{tr(R.string.ui_0711e635bd8d,"新版本 %1\$s",it.versionName)}?:BuildConfig.VERSION_NAME){a.panel="updates"}
            Text(tr(R.string.ui_d06be788976e, "禅 %1\$s · 保持此刻", BuildConfig.VERSION_NAME),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        }
        "language"->LanguagePanel(a)
        "home"->PanelColumn(tr(R.string.ui_b5d4ccb3e850, "桌面与退出"),a){
            a.permissionsVersion
            val isHome=a.homeRecovery.isDefaultHome()
            Text(if(isHome)tr(R.string.ui_04e91ce265cb, "禅已是默认桌面")else tr(R.string.ui_81a47704c4e4, "禅尚未设为默认桌面"),color=MaterialTheme.colorScheme.primary)
            Text(tr(R.string.ui_adc01e0aee86, "退出后，禅不再作为默认桌面。应用和专注记录保留，需要时仍可从原桌面打开。"))
            if(a.homes.isEmpty())Text(tr(R.string.ui_8924d89f657a, "暂未发现其它桌面。请打开系统设置检查原桌面是否启用；不会停用禅。"))
            a.homes.forEach{home->Button(onClick={a.exitHome(home)},modifier=Modifier.fillMaxWidth()){Text(tr(R.string.ui_f7a47735110b, "退出并返回「%1\$s」", home.label))}}
            HorizontalDivider()
            PanelLink(tr(R.string.ui_ef0bd48eb030, "系统默认桌面设置"),tr(R.string.ui_6ae1247bcbae, "如果出现空白，请返回并使用上方直接退出")){a.external(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))}
            PanelLink(tr(R.string.ui_68ea5dd4d7af, "系统设置"),tr(R.string.ui_589b52730428, "查找默认应用或桌面应用")){a.external(Intent(android.provider.Settings.ACTION_SETTINGS))}
            if(!isHome)PanelLink(tr(R.string.ui_adda8f0b4583, "启用禅作为默认桌面"),tr(R.string.ui_5f798ac6d4cf, "由系统确认后生效")){a.homeRole()}
        }
        "notifications","notification-apps","notification-organize"->NotificationPanel(a,s)
        "reminder-settings"->PanelColumn(tr(R.string.ui_1832287c33c0, "应用提醒"),a){
            PanelLink(tr(R.string.ui_641d22a588c0, "提醒应用"),tr(R.string.ui_320aa2633f7b, "已选 %1\$s 个", s.settings.reminderPackages.size)){a.panel="reminders"}
            PanelLink(tr(R.string.ui_43fc32e85fb2, "进行中的目标"),if(s.goals.isEmpty())tr(R.string.ui_97d8e3142640, "暂无目标") else tr(R.string.ui_10600168d18f, "%1\$s 个目标", s.goals.size)){a.panel="goals"}
            ToggleRow(tr(R.string.ui_1255d4306802, "提醒时振动"),s.settings.vibration){v->settings{it.copy(vibration=v)}}
            SnoozeSettings(a,s)
        }
        "favorites","reminders"->AppManagement(a,s,a.panel=="favorites")
        "goal-new","goal-permissions"->GoalInput(a)
        "goals"->PanelColumn(tr(R.string.ui_43fc32e85fb2, "进行中的目标"),a){
            if(s.monitorMessage.isNotEmpty())Text(s.monitorMessage,fontSize=12.sp)
            if(s.goals.isEmpty())Text(tr(R.string.ui_d87ddd7a8b17, "从提醒名单中的应用开始一个目标。"))
            s.goals.forEach{g->PanelLink(g.label,"${g.text} · ${durationText(g.usedMs)}${if(!g.active)tr(R.string.ui_c7ca66931ca9, " · 已暂停") else ""}"){a.goalId=g.id;a.panel="goal-detail"}}
            if(s.goals.any{it.active})TextButton(onClick={a.perform({a.zen().platform.stopMonitoring()})}){Text(tr(R.string.ui_13b7f4ce96d5, "暂停全部监测"))}
        }
        "goal-detail"->GoalDetail(a,s)
        "timer"->RhythmPanel(a,s)
        "focus-note"->FocusNotePanel(a,s)
        "calendar"->FocusStatistics(a,s,now)
        "todo"->TodoPanel(a,s,now)
        "themes"->ThemePanel(a,s)
        "quote-library"->QuoteLibraryPanel(a,s,now)
        "preferences"->PanelColumn(tr(R.string.ui_b0d7729ed342, "文案设置"),a){
            Text(tr(R.string.ui_fd198ea57b00, "首页文案分类"));ChoiceRow(listOf("scene" to tr(R.string.ui_0f70eef46ff0, "风景"),"focus" to tr(R.string.ui_41d42ade41e8, "专注"),"rest" to tr(R.string.ui_fff33732fc97, "休息")),s.settings.category){v->settings{it.copy(category=v)}}
            PanelLink(tr(R.string.ui_74b0faa91cb2, "文案管理")){a.panel="quote-library"}
            PanelLink(tr(R.string.ui_b45e6cd14c36, "当前文案出处")){a.panel="copy-source"}
            HelpButton(tr(R.string.ui_b0d7729ed342,"文案设置"),tr(R.string.ui_ba9a2c79a403,"轻点短句换一句，长按查看出处。")+"\n\n"+tr(R.string.ui_b4442f4b2f60,"按主题、日夜和天气查看、增删与编辑")+if(s.settings.theme in listOf("cloud","river"))"\n\n"+tr(R.string.ui_a45d7eb6d216,"优先显示所选分类的诗句，保留原文。")else "",compact=false)
        }
        "copy-source"->PanelColumn(tr(R.string.ui_2ac1fb15aa49, "文案出处"),a){
            val weather=if(WeatherRules.usable(s.weather,now))WeatherRules.category(s.weather.code,s.weather.wind)else"unknown"
            val dark=isNight(s,now)
            val key=quoteScope(s.settings.theme,if(dark)"night" else "day",weather,s.settings.category)
            val month=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).monthValue
            val pool=rememberLibraryRows(a,s,s.settings.theme,if(dark)"night"else"day",weather,s.settings.category,month)
            val quote=pool.getOrNull(ThemeQuotes.index(s.copyIndices[key]?:0,pool.size))
            if(quote==null)Text(tr(R.string.ui_961cd0bafa7a, "当前条件下没有文案，可在文案管理中添加。")) else {
                Text(quote.text,fontFamily=ZenSerif,fontSize=18.sp)
                Text(quote.credit,fontSize=13.sp)
                if(quote.source.isNotBlank())PanelLink(tr(R.string.ui_3995d7ef3e99, "阅读来源")){a.external(Intent(Intent.ACTION_VIEW,Uri.parse(quote.source)))}
            }
        }
        "weather"->WeatherPanel(a,s,now)
        "updates"->UpdatePanel(a,a.appUpdates)
        "permissions"->PanelColumn(tr(R.string.ui_27c47c1d0630, "权限与数据"),a){
            a.permissionsVersion
            PanelLink(tr(R.string.ui_4b6ded8a7f18, "通知"),if(a.zen().platform.hasNotifications())tr(R.string.ui_8a4ef3e48e4e, "已开启") else tr(R.string.ui_3cffa9757b69, "未开启")){a.requestNotification()}
            PanelLink(tr(R.string.ui_06291f3bbc4e, "使用情况访问"),if(a.zen().platform.hasUsage())tr(R.string.ui_75d889ac43a2, "已允许") else tr(R.string.ui_64aaa401e7bd, "未允许；不会用经过时间代替")){a.usagePermission()}
            PanelLink(tr(R.string.ui_9727ec6c6503, "准点提醒"),if(a.zen().platform.hasExact())tr(R.string.ui_75d889ac43a2, "已允许") else tr(R.string.ui_e15979355cdb, "可能延迟")){a.exactPermission()}
            HelpButton(tr(R.string.ui_27c47c1d0630,"权限与数据"),tr(R.string.ui_edbb1619616e,"仅跟踪你创建目标的应用。锁屏、切换应用暂停；分屏/画中画暂不保证精确归属。系统强行停止后需重新打开禅。"),compact=false)
            HorizontalDivider();PanelLink(tr(R.string.ui_54f99d345ec7, "清除专注记录"),tr(R.string.ui_9332c79978e7, "保留设置、目标和当前计时")){a.panel="clear-history"}
            PanelLink(tr(R.string.ui_8afb50666138, "清除全部本地数据")){a.panel="clear-all"};PanelLink(tr(R.string.ui_1c2322ea9670, "数据与开源说明")){a.panel="about"}
        }
        "clear-history","clear-all"->PanelColumn(tr(R.string.ui_a5da1dd8fd92, "确认清除"),a){
            val all=a.panel=="clear-all"
            Text(if(all)tr(R.string.ui_784cbd2c428e, "删除本机所有设置、待办、进行中的目标和专注记录，并停止计时。此操作无法撤销。") else tr(R.string.ui_2f7ac53a1fda, "删除本机专注统计记录。当前专注计时继续运行，只记录此后新增的专注时间。此操作无法撤销。"))
            Button(onClick={a.perform({if(all){a.weather.remove();a.zen().platform.clearAll()}else a.zen().platform.clearHistory()},{a.panel="settings"})}){Text(tr(R.string.ui_a5da1dd8fd92, "确认清除"))}
            TextButton(onClick={a.panel="permissions"}){Text(tr(R.string.ui_2cd0f3be8738, "取消"))}
        }
        "about"->PanelColumn(tr(R.string.ui_9f66d9efbce4, "关于禅"),a){
            Text(tr(R.string.ui_409fee323548, "%1\$s · 个人测试版\n\n目标、待办、时长预设、应用名单和每日专注记录仅保存在本机，不上传，不接入分析 SDK。授权定位后，天气服务会收到大致位置坐标及网络请求信息；仅在使用禅时更新，不后台持续定位。备用城市模式会发送所选城市的坐标，不接收目标和计时记录。\n\n没有账号、日程读取或云同步。专注统计仅代表计时器运行时间。", BuildConfig.VERSION_NAME))
            PanelLink(tr(R.string.ui_2c8305e859aa, "天气：Open-Meteo"),tr(R.string.ui_c63b379ba366, "CC BY 4.0 数据署名 · 免费接口仅用于非商业测试")){a.external(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))}
            Text(tr(R.string.ui_ff7aff38ec00, "字体：Noto Serif CJK SC · SIL Open Font License 1.1"),fontSize=13.sp)
            Text(remember{a.resources.openRawResource(R.raw.font_license).bufferedReader().use{it.readText()}},fontSize=11.sp)
        }
    }
}

@Composable fun SearchPanel(a:MainActivity){
    var query by remember{mutableStateOf("")}
    LazyPanel(tr(R.string.ui_4c2f7d4f1ced, "查找应用"),a,pinned={OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(tr(R.string.ui_3202fa3359cf, "输入应用名称"))},singleLine=true)}){
        if(query.isNotBlank()){
            val results=a.apps.filter{it.name.contains(query.trim(),true)}
            if(results.isEmpty())item{Text(tr(R.string.ui_dfebb40ebaab, "没有找到匹配的应用"))}
            items(results,key={it.pkg}){e->Row(Modifier.fillMaxWidth().clickable{a.openApp(e)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){AppIcon(e,Modifier.size(32.dp));Spacer(Modifier.width(16.dp));Text(e.name)}}
        }
    }
}
@Composable fun AppManagement(a:MainActivity,s:AppState,favorites:Boolean){
    var query by remember{mutableStateOf("")}
    LazyPanel(if(favorites)tr(R.string.ui_02f0668b446f, "首页应用") else tr(R.string.ui_641d22a588c0, "提醒应用"),a,pinned={
        if(favorites)Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){
            Text(tr(R.string.ui_6e953c2f1169, "默认折叠应用栏"),Modifier.weight(1f))
            Switch(s.settings.collapseHomeApps,onCheckedChange={enabled->
                a.homeAppsExpanded=false
                a.editState{it.copy(settings=it.settings.copy(collapseHomeApps=enabled))}
            },modifier=Modifier.testTag("collapse-apps-setting").semantics{contentDescription=tr(R.string.ui_6e953c2f1169,"默认折叠应用栏")})
        }
        if(favorites)Column {
            Text(tr(R.string.home_icon_size,"图标大小"),fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(28 to tr(R.string.home_icon_small,"小"),36 to tr(R.string.home_icon_medium,"中"),44 to tr(R.string.home_icon_large,"大")).forEach{(size,label)->
                    FilterChip(selected=homeIconSize(s.settings)==size,onClick={a.editState{it.copy(settings=it.settings.copy(homeIconDp=size))}},label={Text(label)},modifier=Modifier.weight(1f).testTag("app-size-$size"))
                }
            }
            Row(Modifier.fillMaxWidth().height(56.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                s.settings.favorites.mapNotNull{pkg->a.apps.find{it.pkg==pkg}}.forEach{AppIcon(it,Modifier.size(homeIconSize(s.settings).dp))}
            }
        }
        HelpButton(if(favorites)tr(R.string.ui_02f0668b446f,"首页应用")else tr(R.string.ui_641d22a588c0,"提醒应用"),if(favorites)tr(R.string.ui_9ab59eb32fbd,"保留最多四个入口，其他应用仍可通过查找打开。")else tr(R.string.ui_dc8f5a18c088,"打开所选应用时先设置使用目标。只累计实际使用时间，与专注计时分开计算。"),compact=false)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(tr(R.string.ui_a49136ee37c6, "搜索已安装应用"))},singleLine=true)
        }){
        if(favorites)item{Column{
        s.settings.favorites.forEachIndexed{index,pkg->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(a.apps.find{it.pkg==pkg}?.name?:tr(R.string.ui_8cc1f144517d, "已不可用"),Modifier.weight(1f))
            TextButton(onClick={a.editState{state->val list=state.settings.favorites.toMutableList();if(index>0)java.util.Collections.swap(list,index,index-1);state.copy(settings=state.settings.copy(favorites=list))}},enabled=index>0){Text(tr(R.string.ui_f853a70b1204, "上移"))}
            TextButton(onClick={a.editState{it.copy(settings=it.settings.copy(favorites=it.settings.favorites-pkg))}}){Text(tr(R.string.ui_6135d4159e89, "移除"))}
        }}
        }}
        items(a.apps.filter{it.name.contains(query.trim(),true)&&(!favorites||it.pkg !in s.settings.favorites)},key={it.pkg}){entry->
            val checked=if(favorites)entry.pkg in s.settings.favorites else entry.pkg in s.settings.reminderPackages
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("app-choice-${entry.pkg}"),verticalAlignment=Alignment.CenterVertically){
                AppIcon(entry,Modifier.size(28.dp));Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){Text(entry.name);if(a.apps.count{it.name==entry.name}>1)Text(entry.pkg,fontSize=10.sp)}
                Checkbox(checked,onCheckedChange={v->
                    if(favorites&&v&&s.settings.favorites.size>=4)a.message=tr(R.string.ui_5705b6c3d2e5, "首页最多保留四个应用。")
                    else a.editState{state->if(favorites&&v&&state.settings.favorites.size>=4)state else state.copy(settings=if(favorites)state.settings.copy(favorites=if(v)(state.settings.favorites+entry.pkg).distinct() else state.settings.favorites-entry.pkg)
                        else state.settings.copy(reminderPackages=if(v)state.settings.reminderPackages+entry.pkg else state.settings.reminderPackages-entry.pkg))}
                })
            }
        }
    }
}
@Composable fun GoalInput(a:MainActivity){
    a.permissionsVersion
    var text by rememberSaveable{mutableStateOf("")};var minutes by rememberSaveable{mutableIntStateOf(10)}
    PanelColumn(tr(R.string.ui_b88719b8d536, "设置应用提醒"),a){
        Text(a.selected?.name?:tr(R.string.ui_5305a37f317c, "目标应用"),color=MaterialTheme.colorScheme.secondary)
        if(a.panel=="goal-new"){
            OutlinedTextField(text,{if(it.length<=60)text=it},Modifier.fillMaxWidth(),label={Text(tr(R.string.ui_02ae3fed8b9b, "例如：看完收藏的那节课程"))},supportingText={Text("${text.length}/60")})
            Text(tr(R.string.ui_e0cab65734f5, "首次提醒时间（累计使用）"),fontSize=13.sp)
            ChoiceRow(listOf(5,10,15,30).map{it.toString() to tr(R.string.ui_3892529bc623, "%1\$s 分钟", it)},minutes.toString()){minutes=it.toInt()}
        }
        if(!a.zen().platform.hasUsage())PanelLink(tr(R.string.ui_fc86468672c6, "允许使用情况访问"),tr(R.string.ui_c3dc778f2503, "切出与锁屏暂停，需要系统授权")){a.usagePermission()}
        if(!a.zen().platform.hasNotifications())PanelLink(tr(R.string.ui_3834f9cf9f9b, "开启通知"),tr(R.string.ui_9dc8c514eeb4, "后台提醒会通过系统通知送达")){a.requestNotification()}
        Text(tr(R.string.ui_26081dbc5f71, "仅累计这个应用实际在前台的时间。运行时有一条可停止的监测通知。"),fontSize=12.sp)
        Button(onClick={if(a.panel=="goal-new")a.createGoal(text,minutes)else a.selected?.let{a.openApp(it)}},enabled=(a.panel!="goal-new"||text.isNotBlank())&&a.zen().platform.hasUsage()&&a.zen().platform.hasNotifications()){Text(if(a.panel=="goal-new")tr(R.string.ui_0676c4de51ec, "创建提醒并打开")else tr(R.string.ui_e3b8a076e71b, "继续提醒并打开"))}
        TextButton(onClick={a.selected?.let{a.launch(it)}}){Text(tr(R.string.ui_5e22c59d8c70, "直接打开，不提醒"))}
    }
}
@Composable fun GoalDetail(a:MainActivity,s:AppState){
    val g=s.goals.find{it.id==a.goalId};var edit by remember{mutableStateOf(false)};var text by remember(g?.id){mutableStateOf(g?.text?:"")}
    var saving by remember{mutableStateOf(false)}
    PanelColumn(g?.label?:tr(R.string.ui_67cd8a241d3a, "目标已结束"),a){
        if(g==null){Text(tr(R.string.ui_f560e5ac9aca, "该任务已结束或移除。"));return@PanelColumn}
        Text(g.text,fontFamily=ZenSerif,fontSize=19.sp);Text(tr(R.string.ui_b5ae6154a7ee, "已使用 %1\$s · %2\$s", durationText(g.usedMs), if(g.active)tr(R.string.ui_1ab99b6e6e11, "监测中") else tr(R.string.ui_eb0c326b60ae, "已暂停")))
        if(s.monitorMessage.isNotEmpty())Text(s.monitorMessage,fontSize=12.sp)
        Text(tr(R.string.ui_4e830155f4ea, "下次提醒：累计 %1\$s%2\$s", durationText(g.thresholdMs), if(g.notified)tr(R.string.ui_0d508a90b58e, "（已提醒，可延后再次提醒）") else ""),fontSize=12.sp)
        Button(onClick={a.apps.find{it.pkg==g.pkg}?.let{a.openApp(it)}?:run{a.message=tr(R.string.ui_691738cee78f, "此应用已不可用。")}}){Text(if(g.active)tr(R.string.ui_a3bba31ccfb9, "返回应用") else tr(R.string.ui_024fb39818de, "恢复并进入应用"))}
        if(g.active)TextButton(onClick={a.perform({a.zen().platform.snooze(g.id)})}){Text(tr(R.string.ui_b74894890a15, "延后提醒 · %1\$s", durationSecondsText(s.settings.snoozeDurationSeconds)))}
        if(!edit)TextButton(onClick={edit=true}){Text(tr(R.string.ui_626241323b3b, "修改目标"))}
        if(edit){OutlinedTextField(text,{if(it.length<=60)text=it},Modifier.fillMaxWidth(),enabled=!saving);TextButton(onClick={saving=true;val value=text.trim();a.saveTools({s2->s2.copy(goals=s2.goals.map{if(it.id==g.id)it.copy(text=value)else it})}){ok->saving=false;if(ok)edit=false}},enabled=text.isNotBlank()&&!saving){Text(tr(R.string.ui_6fc46974c237, "保存目标"))}}
        if(edit)TextButton(onClick={text=g.text;edit=false},enabled=!saving){Text(tr(R.string.ui_fada4dfc38e3, "取消修改"))}
        TextButton(onClick={a.perform({a.zen().platform.endGoal(g.id)},{a.backFromPanel()})}){Text(tr(R.string.ui_c479370b881f, "结束目标"))}
    }
}

@Composable private fun SnoozeSettings(a:MainActivity,s:AppState){
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val draft=rememberDurationDraft(s.settings.snoozeDurationSeconds)
    var feedback by rememberSaveable{mutableStateOf("")}
    var feedbackRevision by remember{mutableIntStateOf(0)}
    fun notice(value:String){feedback=value;feedbackRevision++}
    var busy by remember{mutableStateOf(false)}
    DurationInput(tr(R.string.ui_4a6bb4346859, "延后提醒时长"),"snooze",draft){notice("")}
    Text(tr(R.string.ui_c23d36770401, "保存后，在下次点“延后提醒”时生效。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
    DurationFeedback(feedback,feedbackRevision)
    OutlinedButton(onClick={
        focusManager.clearFocus();keyboard?.hide()
        val seconds=draft.totalSeconds
        if(seconds==null)notice(tr(R.string.ui_7151b27f8859, "请输入 1 秒至 24 小时的时长，最小精度为 1 秒。"))
        else {busy=true;a.saveTools({DailyTools.applySnoozeSeconds(it,seconds)}){ok->busy=false;if(ok)notice(tr(R.string.ui_a55e3c1f09c7, "延后间隔已保存。"))}}
    },enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("save-snooze")){Text(tr(R.string.ui_586fef2b9741, "保存提醒时长"))}
}
