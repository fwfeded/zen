package dev.zen.launcher

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*

@Composable fun QuietFocusHome(a:MainActivity,s:AppState,p:Palette,dark:Boolean,onExpand:()->Unit){
    var busy by remember{mutableStateOf(false)}
    var hint by rememberSaveable(s.timer.session){mutableStateOf(!s.settings.quietHintSeen)}
    LaunchedEffect(Unit){if(!s.settings.quietHintSeen)a.editState{it.copy(settings=it.settings.copy(quietHintSeen=true))}}
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("quiet-focus")){
        val height=maxHeight
        val clock=countdownText((s.timer.remainingMs+999)/1000)
        val available=with(LocalDensity.current){(minOf(maxWidth,480.dp)-56.dp).toPx()}
        val clockStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=48.sp,lineHeight=60.sp,fontWeight=FontWeight.Light)
        val measured=rememberTextMeasurer().measure(clock,clockStyle,softWrap=false,maxLines=1)
        val clockSize=48.sp*(available/measured.size.width.coerceAtLeast(1)).coerceAtMost(1f)*.98f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
            Column(Modifier.widthIn(max=480.dp).fillMaxWidth().heightIn(min=height).padding(horizontal=28.dp,vertical=12.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(tr(R.string.ui_1dca27a69ae1, "专注中"),Modifier.weight(1f),fontFamily=ZenSerif,color=p.muted)
                    TextButton(onClick=onExpand,modifier=Modifier.testTag("expand-home")){Text(tr(R.string.ui_cfee8211c7fa, "展开桌面"),color=p.muted)}
                }
                Column(Modifier.fillMaxWidth().padding(vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    if(s.timer.taskTitle.isNotBlank())Text(s.timer.taskTitle,fontFamily=ZenSerif,fontSize=20.sp,lineHeight=30.sp,
                        textAlign=TextAlign.Center,color=p.ink,modifier=Modifier.testTag("focus-task"))
                    val note=s.focusBookmark?.takeIf{it.todoId==s.timer.todoId}?.nextStep.orEmpty()
                    if(note.isNotBlank())Text(tr(R.string.ui_fbf16f9a7efc, "下一步：%1\$s", note),fontSize=13.sp,lineHeight=22.sp,textAlign=TextAlign.Center,color=p.muted,modifier=Modifier.padding(top=12.dp))
                    Spacer(Modifier.height(24.dp))
                    TimerGlyph(s.settings.theme,dark,1f-s.timer.remainingMs.toFloat()/s.timer.durationMs,Modifier.fillMaxWidth().height(64.dp))
                    Text(clock,fontSize=clockSize,lineHeight=clockSize*1.25f,maxLines=1,softWrap=false,fontWeight=FontWeight.Light,textAlign=TextAlign.Center,color=p.ink,modifier=Modifier.fillMaxWidth().testTag("quiet-countdown"))
                    Text(tr(R.string.ui_568c1e74fb06, "剩余时间"),fontSize=12.sp,color=p.muted)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick={busy=true;a.perform({a.zen().platform.toggleTimer()},{busy=false})},enabled=!busy,modifier=Modifier.testTag("quiet-pause")){Text(tr(R.string.ui_971f2f448772, "暂停计时"))}
                    TextButton(onClick={busy=true;a.perform({a.zen().platform.endTimer()},{busy=false})},enabled=!busy,modifier=Modifier.testTag("quiet-end")){Text(tr(R.string.ui_e597fd46fa53, "结束本轮"),color=p.muted)}
                }
                if(hint)Column(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(tr(R.string.ui_413dcff5ee1d, "专注时收起次要内容。点“展开桌面”使用其他功能，也可在专注计时设置中关闭。"),fontSize=12.sp,lineHeight=20.sp,color=p.muted,textAlign=TextAlign.Center)
                    TextButton(onClick={hint=false}){Text(tr(R.string.ui_de32e20193ad, "知道了"),color=p.muted)}
                }else Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable fun FocusNotePanel(a:MainActivity,s:AppState){
    val task=FocusFlow.rememberedTask(s)
    val id=task?.id
    var text by rememberSaveable(id){mutableStateOf(s.focusBookmark?.nextStep.orEmpty())}
    var busy by remember{mutableStateOf(false)}
    PanelColumn(tr(R.string.ui_c59e1fe8ed34, "记录下一步"),a){
        if(task==null){Text(tr(R.string.ui_be14b3528fcf, "没有可继续的待办。完成或删除的待办不会再次推荐。"));return@PanelColumn}
        Text(task.text,fontSize=16.sp)
        OutlinedTextField(text,{if(it.length<=120)text=it},Modifier.fillMaxWidth().testTag("next-step-input"),
            label={Text(tr(R.string.ui_1dde1d990ba1, "下一步（可选）"))},placeholder={Text(tr(R.string.ui_ef7be3e44fac, "例如：从第三道题继续"))},enabled=!busy)
        Text(tr(R.string.ui_ec84888e4eeb, "仅作为下次继续时的提示，不会自动完成待办。"),fontSize=12.sp)
        if(text.trim()!=s.focusBookmark?.nextStep.orEmpty()){
            Text(tr(R.string.ui_520b00e114f7, "尚未保存"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
            Button(onClick={busy=true;a.saveTools({FocusFlow.saveNote(it,task.id,text)}){ok->busy=false;if(ok)a.backFromPanel()}},
                enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("save-next-step")){Text(tr(R.string.ui_778c339528ef, "保存并返回"))}
        }
    }
}
