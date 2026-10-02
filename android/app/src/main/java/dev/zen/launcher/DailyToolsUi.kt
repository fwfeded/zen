@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.zen.launcher

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*

/** Publish UI success only after the existing atomic write succeeds. */
fun MainActivity.saveTools(change:(AppState)->AppState,after:(Boolean)->Unit={}){
    var saved=false;perform({saved=zen().store.update(change)},{after(saved)})
}

// Keep the removed record with this panel's saved state, including its identity and original fields.
// A successful undo clears it, so another configuration change cannot offer the same undo again.
private val RemovedRhythmSaver=Saver<Rhythm?,String>(
    save={it?.let{item->Json.encodeToString(item)}},
    restore={runCatching{Json.decodeFromString<Rhythm>(it)}.getOrNull()}
)
private val RemovedTodoSaver=Saver<Todo?,String>(
    save={it?.let{item->Json.encodeToString(item)}},
    restore={runCatching{Json.decodeFromString<Todo>(it)}.getOrNull()}
)

@Composable fun RhythmPanel(a:MainActivity,s:AppState){
    val access=rememberTimerAccess(a)
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun finishInput(){focusManager.clearFocus();keyboard?.hide()}
    var alertsExpanded by rememberSaveable { mutableStateOf(false) }
    val focus = rememberDurationDraft(s.settings.focusDurationSeconds)
    val rest = rememberDurationDraft(s.settings.restDurationSeconds)
    var manage by rememberSaveable{mutableStateOf(false)};var editing by rememberSaveable{mutableStateOf<String?>(null)}
    var removed by rememberSaveable(stateSaver=RemovedRhythmSaver){mutableStateOf<Rhythm?>(null)};var busy by remember{mutableStateOf(false)}
    var feedback by remember{mutableStateOf("")}
    var feedbackRevision by remember{mutableIntStateOf(0)}
    fun notice(value:String){feedback=value;feedbackRevision++}
    val f=focus.totalSeconds?:0;val r=rest.totalSeconds?:0
    fun valid():Boolean {if(!DailyTools.validSeconds(f,r)){notice(tr(R.string.ui_7151b27f8859, "请输入 1 秒至 24 小时的时长，最小精度为 1 秒。"));return false};return true}
    fun savePreset(){
        finishInput()
        if(!valid()||busy)return
        if(s.settings.rhythms.any{it.id!=editing&&it.focusDurationSeconds==f&&it.restDurationSeconds==r}){notice(tr(R.string.ui_f3d7273e5106, "这个组合已经保存。"));return}
        busy=true;val id=editing
        a.saveTools({DailyTools.saveRhythmSeconds(it,f,r,id)}){ok->busy=false;if(ok){editing=null;notice(tr(R.string.ui_58d42ada907f, "预设已保存。"))}}
    }
    PanelColumn(tr(R.string.ui_9a86da03a656, "专注计时"),a){
        val remembered=FocusFlow.rememberedTask(s)
        if(s.timer.taskTitle.isNotBlank()&&s.timer.status!="idle"){
            Text(tr(R.string.ui_182f1c7babe9, "当前待办：%1\$s", s.timer.taskTitle),fontSize=15.sp)
            val step=s.focusBookmark?.takeIf{it.todoId==s.timer.todoId}?.nextStep.orEmpty()
            if(step.isNotBlank())Text(tr(R.string.ui_fbf16f9a7efc, "下一步：%1\$s", step),fontSize=13.sp)
        }else if(remembered!=null&&s.timer.status=="idle"){
            Text(tr(R.string.ui_6fe8d7142936, "上次待办：%1\$s", remembered.text),fontSize=15.sp,modifier=Modifier.testTag("last-focus-task"))
            if(s.focusBookmark?.nextStep?.isNotBlank()==true)Text(tr(R.string.ui_fbf16f9a7efc, "下一步：%1\$s", s.focusBookmark.nextStep),fontSize=13.sp)
            TextButton(onClick={finishInput();if(valid())a.focusTodo(remembered.id,f to r)},enabled=!busy&&editing==null,modifier=Modifier.testTag("resume-last-task")){Text(tr(R.string.ui_5b46ee8a8715, "继续上次待办"))}
        }
        if(remembered!=null&&(s.timer.status=="idle"||s.timer.todoId==remembered.id))
            TextButton(onClick={a.panel="focus-note"},modifier=Modifier.testTag("edit-next-step")){Text(tr(R.string.ui_dceac6bbdc4f, "记录下一步（可选）"))}
        if(s.timer.status=="idle"&&remembered==null)TextButton(onClick={a.panel="todo"}){Text(tr(R.string.ui_d8db406845fa, "从待办开始"))}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(tr(R.string.ui_d58c88688e1a, "常用预设"),Modifier.weight(1f),fontSize=14.sp)
            TextButton(onClick={manage=!manage;editing=null}){Text(if(manage)tr(R.string.ui_667fa76ba346, "退出管理") else tr(R.string.ui_bb6d995724f4, "管理"))}
        }
        if(manage){
            Column(Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState())){
                s.settings.rhythms.forEach{rhythm->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(durationPairText(rhythm.focusDurationSeconds,rhythm.restDurationSeconds),Modifier.weight(1f))
                        TextButton(onClick={editing=rhythm.id;focus.set(rhythm.focusDurationSeconds);rest.set(rhythm.restDurationSeconds);notice("")},enabled=!busy){Text(tr(R.string.ui_37090f456516, "修改"))}
                        TextButton(onClick={busy=true;a.saveTools({it.copy(settings=it.settings.copy(rhythms=it.settings.rhythms.filterNot{p->p.id==rhythm.id}))}){ok->busy=false;if(ok){removed=rhythm;editing=null}}},enabled=!busy){Text(tr(R.string.ui_2f9daa828907, "删除"))}
                    }
                }
            }
        }else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            s.settings.rhythms.forEach{p->FilterChip(selected=f==p.focusDurationSeconds&&r==p.restDurationSeconds,onClick={focus.set(p.focusDurationSeconds);rest.set(p.restDurationSeconds);editing=null;notice("")},label={Text(durationPairText(p.focusDurationSeconds,p.restDurationSeconds),fontSize=12.sp)},shape=RoundedCornerShape(22.dp))}
        }
        if(s.settings.rhythms.isEmpty())Text(tr(R.string.ui_48acd33242c5, "可将当前时长保存为预设。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        if(removed!=null)TextButton(onClick={val p=removed;if(p!=null)a.saveTools({if(it.settings.rhythms.any{r->r.id==p.id||(r.focusDurationSeconds==p.focusDurationSeconds&&r.restDurationSeconds==p.restDurationSeconds)})it else it.copy(settings=it.settings.copy(rhythms=it.settings.rhythms+p))}){ok->if(ok&&removed?.id==p.id)removed=null}}){Text(tr(R.string.ui_48862baead8d, "已移除 · 撤销"))}
        if(editing!=null)Text(tr(R.string.ui_e7edbe8c5ae1, "修改预设"),fontSize=13.sp)
        DurationPairInput(focus,rest){notice("")}
        Row{
            if(editing!=null||s.settings.rhythms.none{it.focusDurationSeconds==f&&it.restDurationSeconds==r})
                TextButton(onClick={savePreset()},enabled=!busy,modifier=Modifier.testTag("add-rhythm")){Text(if(editing==null)tr(R.string.ui_d2aa9ac606d7, "保存为预设") else tr(R.string.ui_3ab57dd1e321, "保存预设"))}
            if(editing!=null)TextButton(onClick={editing=null;focus.set(s.settings.focusDurationSeconds);rest.set(s.settings.restDurationSeconds);notice("")}){Text(tr(R.string.ui_fada4dfc38e3, "取消修改"))}
        }
        DurationFeedback(feedback,feedbackRevision)
        Button(onClick={
            finishInput()
            val fresh=s.timer.status in listOf("idle","complete")
            if(!fresh||valid()){
                busy=true;var ok=true
                a.perform({if(fresh)ok=a.zen().store.update{DailyTools.applyRhythmSeconds(it,f,r)};if(ok)a.zen().platform.toggleTimer()},{busy=false;if(ok)a.panel=""})
            }
        },enabled=!busy&&editing==null,modifier=Modifier.fillMaxWidth().testTag("timer-primary")){
            Text(when(s.timer.status){"running"->tr(R.string.ui_971f2f448772, "暂停计时");"paused"->tr(R.string.ui_c39c7232d41f, "继续计时");"complete"->if(s.timer.mode=="focus")tr(R.string.ui_7d4beb773506, "开始休息") else tr(R.string.ui_033df8fb6e36, "开始专注");else->tr(R.string.ui_033df8fb6e36, "开始专注")})
        }
        if(!access.timerNotifications)Text(tr(R.string.ui_ef6cacc3f44d, "未开启专注计时通知，仅能在前台提示。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        val changed=f!=s.settings.focusDurationSeconds||r!=s.settings.restDurationSeconds
        if(changed&&editing==null){
            Text(if(s.timer.status in listOf("idle","complete"))tr(R.string.ui_3b3043c76443, "时长尚未保存；开始计时也会保存。")else tr(R.string.ui_da42b9bfc065, "时长尚未保存；保存后下一轮生效，当前计时不变。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
            OutlinedButton(onClick={finishInput();if(valid()){busy=true;a.saveTools({DailyTools.applyRhythmSeconds(it,f,r)}){ok->busy=false;if(ok)notice(tr(R.string.ui_e9b3d0014406, "已保存，下一轮生效。"))}}},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("save-rhythm")){Text(tr(R.string.ui_793dc584cf9d, "保存时长"))}
        }
        if(s.timer.status!="idle")TextButton(onClick={a.perform({a.zen().platform.endTimer()})}){Text(tr(R.string.ui_e597fd46fa53, "结束本轮"))}
        TextButton(onClick={alertsExpanded=!alertsExpanded},modifier=Modifier.testTag("timer-alert-options")) {
            Text(if(alertsExpanded)tr(R.string.ui_587904ac609d, "收起提醒设置") else tr(R.string.timer_alert_summary,"到点提醒 · %1\$s",access.alerts.label),fontSize=12.sp)
        }
        if(alertsExpanded) {
            if(!access.notifications)PanelLink(tr(R.string.ui_3834f9cf9f9b, "开启通知"),tr(R.string.ui_3aab3a9ae985, "后台到点提醒需要通知权限")){a.requestNotification()}
            else PanelLink(tr(R.string.timer_alert_settings,"声音与振动设置"),access.alerts.label){a.timerNotificationSettings()}
            TextButton(onClick={a.perform({a.zen().platform.testTimerAlert()})},enabled=access.timerNotifications,
                modifier=Modifier.testTag("test-timer-alert")){Text(tr(R.string.timer_test_button,"测试提醒"))}
            if(!access.exact)PanelLink(tr(R.string.ui_9727ec6c6503, "准点提醒"),tr(R.string.ui_da5edc06aafe, "未允许时后台提醒可能延迟")){a.exactPermission()}
        }
        ToggleRow(tr(R.string.ui_2db094c1df82, "专注时简化桌面"),s.settings.quietFocus){v->a.editState{it.copy(settings=it.settings.copy(quietFocus=v))}}

    }
}

@Composable fun TodoPanel(a:MainActivity,s:AppState,now:Long){
    val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    var selected by rememberSaveable(stateSaver=Saver<LocalDate,Long>(save={it.toEpochDay()},restore={LocalDate.ofEpochDay(it)})){mutableStateOf(today)}
    var text by rememberSaveable{mutableStateOf("")}
    var pending by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
    var removed by rememberSaveable(stateSaver=RemovedTodoSaver){mutableStateOf<Todo?>(null)}
    val tasks=s.todos.filter{it.date==selected.toString()}
    val older=s.todos.filter{!it.done&&it.date<today.toString()}.sortedBy{it.date}
    fun addTodo(){
        if(text.trim().isEmpty()||busy)return
        busy=true;val value=text;val day=selected.toString()
        a.saveTools({DailyTools.addTodo(it,day,value)}){ok->
            busy=false
            // The user can already be drafting the next task while this write finishes.
            if(ok&&text==value&&selected.toString()==day)text=""
        }
    }
    LazyPanel(tr(R.string.ui_eab60d7fb33d, "每日待办"),a,pinned={
            a.permissionsVersion
            ToggleRow(tr(R.string.overlay_title,"跨应用待办"),s.settings.todoOverlay&&TodoOverlayService.allowed(a),help=tr(R.string.overlay_help,"在其他应用上方显示今天的一项未完成待办。点击展开、拖动调整位置；锁屏隐藏，全部完成后自动隐藏。需要悬浮与通知权限，部分安全页面不允许显示。")){a.setTodoOverlay(it)}
            val overlayHidden by TodoOverlayService.hidden.collectAsState()
            if(s.settings.todoOverlay&&overlayHidden)TextButton(onClick={a.resumeTodoOverlay()}){Text(tr(R.string.overlay_restore,"恢复显示"))}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(onClick={selected=selected.minusDays(1)}){Text(tr(R.string.ui_8b0f9494ec48, "前一天"))}
                Text("${selected.monthValue}.${selected.dayOfMonth}${if(selected==today)tr(R.string.ui_4234c1b91640, " · 今天") else ""}",fontSize=14.sp)
                TextButton(onClick={selected=selected.plusDays(1)},enabled=selected<today){Text(tr(R.string.ui_af19ac0d4d33, "后一天"))}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(text,{if(it.length<=80)text=it},Modifier.weight(1f).testTag("todo-input"),placeholder={Text(tr(R.string.ui_1ffde44929e6, "输入待办事项"))},singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={addTodo()}))
                IconButton(onClick={addTodo()},enabled=text.trim().isNotEmpty()&&!busy,modifier=Modifier.testTag("todo-add").semantics{contentDescription=tr(R.string.ui_4ba5695319ee, "添加待办")}){LineIcon("plus",MaterialTheme.colorScheme.primary,s.settings.theme)}
            }
            Text("${tasks.count{it.done}} / ${tasks.size}",Modifier.padding(top=16.dp,bottom=8.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        }){
        items(tasks,key={it.id}){task->
            Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
                TaskCheck(task.done,s.settings.theme,onChange={done->a.editState{DailyTools.completeTodo(it,task.id,done)}},modifier=Modifier.testTag("todo-check-${task.id}").semantics{contentDescription="${if(task.done)tr(R.string.ui_e0534b8a4e46, "恢复")else tr(R.string.ui_c0b3fbff51cc, "完成")}：${task.text}"})
                Text(task.text,Modifier.weight(1f),fontSize=15.sp,lineHeight=24.sp,textDecoration=if(task.done)TextDecoration.LineThrough else TextDecoration.None,color=if(task.done)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                if(!task.done)TextButton(onClick={a.openTodoFocus(task.id,FocusFlow.taskAction(s,task.id))},modifier=Modifier.testTag("todo-focus-${task.id}")){
                    Text(FocusFlow.taskAction(s,task.id).label,fontSize=12.sp)
                }
                IconButton(onClick={a.saveTools({it.copy(todos=it.todos.filterNot{t->t.id==task.id})}){ok->if(ok)removed=task}},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_029659ac1dc2, "删除：%1\$s", task.text)}){LineIcon("close",MaterialTheme.colorScheme.secondary,s.settings.theme)}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
        }
        if(tasks.isEmpty())item{Text(tr(R.string.ui_16a0c084de30, "当天暂无待办"),fontFamily=ZenSerif,fontSize=15.sp,modifier=Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.secondary)}
        if(removed!=null)item{TextButton(onClick={val t=removed;if(t!=null)a.saveTools({if(it.todos.any{v->v.id==t.id})it else it.copy(todos=it.todos+t)}){ok->if(ok&&removed?.id==t.id)removed=null}}){Text(tr(R.string.ui_48862baead8d, "已移除 · 撤销"))}}
        if(older.isNotEmpty()&&selected==today)item{TextButton(onClick={pending=!pending}){Text(tr(R.string.ui_de1c767e372a, "%1\$s 之前未完成 · %2\$s", if(pending)"−" else "＋", older.size))}}
        if(pending&&selected==today)items(older,key={"pending-${it.id}"}){task->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(task.text,fontSize=14.sp);Text(task.date,fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)}
                TextButton(onClick={a.saveTools({DailyTools.moveTodo(it,task.id,today.toString())}){ok->if(ok)selected=today}}){Text(tr(R.string.ui_246d6e031bff, "移到今天"))}
            }
        }
    }
}

@Composable private fun TaskCheck(done:Boolean,theme:String,onChange:(Boolean)->Unit,modifier:Modifier=Modifier){
    val c=MaterialTheme.colorScheme
    Box(modifier.size(48.dp).clip(RoundedCornerShape(if(ThemeCards.base(theme)=="paper")8.dp else 24.dp)).toggleable(value=done,role=Role.Checkbox,onValueChange=onChange),contentAlignment=Alignment.Center){
        Canvas(Modifier.size(20.dp)){
            val corner=CornerRadius((if(ThemeCards.base(theme)=="paper")3.dp else 10.dp).toPx())
            if(done)drawRoundRect(c.primary.copy(alpha=.10f),cornerRadius=corner)
            drawRoundRect(if(done)c.primary.copy(alpha=.65f)else c.secondary.copy(alpha=.55f),cornerRadius=corner,style=Stroke(1.dp.toPx()))
            if(done){
                drawLine(c.primary,Offset(size.width*.26f,size.height*.52f),Offset(size.width*.43f,size.height*.67f),1.35.dp.toPx(),StrokeCap.Round)
                drawLine(c.primary,Offset(size.width*.43f,size.height*.67f),Offset(size.width*.75f,size.height*.34f),1.35.dp.toPx(),StrokeCap.Round)
            }
        }
    }
}
