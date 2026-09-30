package dev.zen.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

@Composable internal fun ThemePanel(a:MainActivity,s:AppState) {
    var candidate by remember{mutableStateOf<InstalledTheme?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var delete by remember{mutableStateOf(false)}
    var manage by remember{mutableStateOf(false)}
    val visible=remember{AtomicBoolean(true)}
    val importScope=rememberCoroutineScope()
    DisposableEffect(Unit){onDispose{visible.set(false)}}
    DisposableEffect(candidate?.id){val old=candidate;onDispose{old?.let{a.perform({ThemeCards.discard(it.id)})}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null&&!busy){
            busy=true;error=""
            importScope.launch{
                var result:InstalledTheme?=null
                try{
                    withContext(Dispatchers.IO){
                        result=runCatching{a.contentResolver.openInputStream(uri)?.use{ThemeCards.stage(it)}?:kotlin.error("file")}.getOrNull()
                    }
                    candidate=result
                    if(result==null)error=tr(R.string.card_invalid,"无法导入。请检查主题卡格式、图片大小和剩余空间（最多 20 个外部主题）。")
                }finally{
                    if(!isActive||!visible.get())withContext(NonCancellable+Dispatchers.IO){result?.let{ThemeCards.discard(it.id)}}
                    busy=false
                }
            }
        }
    }
    val selected=candidate
    if(selected==null && manage) {
        androidx.activity.compose.BackHandler{manage=false}
        PanelColumn(tr(R.string.card_manage,"管理已导入主题"),a,onBack={manage=false}){
            s.themeCards.forEach{entry->PanelLink(entry.card.name,if(s.settings.theme==entry.id)tr(R.string.card_current,"使用中")else""){candidate=entry;error=""}}
        }
    }else if(selected==null) {
        PanelColumn(tr(R.string.ui_788db1cfec2a,"主题"),a,pinned={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                TextButton(onClick={runCatching{picker.launch(arrayOf("*/*"))}.onFailure{error=tr(R.string.card_invalid,"无法导入。请检查主题卡格式、图片大小和剩余空间（最多 20 个外部主题）。")}},enabled=!busy,modifier=Modifier.testTag("theme-import")){Text(tr(R.string.card_import,"导入主题"))}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        }){
            ChoiceRow(themes.map{it.key to it.value},s.settings.theme){v->a.editState{it.copy(settings=it.settings.copy(theme=v))}}
            if(s.themeCards.isNotEmpty()){
                TextButton(onClick={manage=true},modifier=Modifier.testTag("theme-manage")){Text(tr(R.string.card_manage,"管理已导入主题"))}
            }
            ToggleRow(tr(R.string.ui_596d4e789906,"背景动画"),s.settings.motion){v->a.editState{it.copy(settings=it.settings.copy(motion=v))}}
            Text(tr(R.string.ui_af7d8af51c9f,"日夜模式"))
            ChoiceRow(listOf("auto" to tr(R.string.ui_7eb336e42cb5,"自动"),"day" to tr(R.string.ui_6fa7e15a01ab,"白天"),"night" to tr(R.string.ui_b77ea2b347b8,"夜晚")),s.settings.phase){v->a.editState{it.copy(settings=it.settings.copy(phase=v))}}
            HelpButton(tr(R.string.ui_788db1cfec2a,"主题"),tr(R.string.card_help,"导入 .zentheme 或 ZIP 主题卡，预览后应用。主题卡仅包含图片、文案和配置，不运行外部代码。动态效果遵循动画与省电设置；自动日夜根据当地日出日落切换，无数据时使用手机时间。"),compact=false)
        }
    }else{
        androidx.activity.compose.BackHandler{if(!busy){candidate=null;error=""}}
        var dark by remember(selected.id){mutableStateOf(false)}
        var weather by remember(selected.id){mutableStateOf("unknown")}
        var imageFailed by remember(selected.id,dark,weather){mutableStateOf(false)}
        val bitmap by produceState<android.graphics.Bitmap?>(null,selected.id,dark,weather){
            value=null
            value=withContext(Dispatchers.IO){runCatching{ThemeCards.bitmap(selected,dark,weather)}.getOrNull()}
            imageFailed=value==null
        }
        val installed=s.themeCards.any{it.id==selected.id}
        PanelColumn(selected.card.name,a,onBack={if(!busy){candidate=null;error=""}},pinned={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                Button(onClick={
                    busy=true;error="";var ok=false
                    a.perform({ok=a.zen().store.update{state->state.copy(themeCards=if(state.themeCards.any{it.id==selected.id})state.themeCards else state.themeCards+selected,settings=state.settings.copy(theme=selected.id))}}, {
                        busy=false;if(ok){candidate=null;a.panel=""}else error=tr(R.string.card_save_failed,"未能保存主题，请检查剩余空间后重试。")
                    })
                },enabled=!busy&&bitmap!=null,modifier=Modifier.testTag("theme-apply")){Text(tr(R.string.card_apply,"应用主题"))}
            }
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        }){
            ChoiceRow(listOf("day" to tr(R.string.ui_6fa7e15a01ab,"白天"),"night" to tr(R.string.ui_b77ea2b347b8,"夜晚")),if(dark)"night"else"day"){dark=it=="night"}
            ChoiceRow((listOf("unknown")+selected.card.variants.keys.map{it.substringAfter('/')}).distinct().map{it to (weatherNames[it]?:it)},weather){weather=it}
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).testTag("theme-preview")){
                bitmap?.let{Image(it.asImageBitmap(),contentDescription=selected.card.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                if(imageFailed)Text(tr(R.string.card_image_failed,"图片暂不可用，请重新导入。"),Modifier.padding(16.dp))
            }
            selected.card.quotePool(if(dark)"night"else"day",weather,"scene").firstOrNull()?.let{Text(it.text,fontSize=16.sp,fontFamily=ZenSerif)}
            Text(selected.card.author,fontSize=13.sp)
            HelpButton(tr(R.string.card_info,"主题信息"),selected.card.license+if(selected.card.source.isNotBlank())"\n\n"+selected.card.source else "")
            if(installed)TextButton(onClick={delete=true},enabled=!busy,modifier=Modifier.testTag("theme-delete")){Text(tr(R.string.card_delete,"删除主题"),color=MaterialTheme.colorScheme.error)}
        }
        if(delete)AlertDialog(onDismissRequest={if(!busy)delete=false},title={Text(tr(R.string.card_delete,"删除主题"))},text={Text(tr(R.string.card_delete_detail,"删除导入副本及其文案修改，原主题卡文件保留。正在使用时将切回水静。"))},confirmButton={TextButton(enabled=!busy,onClick={
            busy=true;var ok=false
            a.perform({
                ok=a.zen().store.update{state->state.copy(themeCards=state.themeCards.filterNot{it.id==selected.id},settings=if(state.settings.theme==selected.id)state.settings.copy(theme="water")else state.settings,quoteEdits=state.quoteEdits.filterNot{it.scope.startsWith(selected.id+"/")},copyIndices=state.copyIndices.filterKeys{!it.startsWith(selected.id+"/")})}
                if(ok)ThemeCards.discard(selected.id)
            },{busy=false;delete=false;if(ok){candidate=null;if(a.zen().store.state.value.themeCards.isEmpty())manage=false}else error=tr(R.string.card_save_failed,"未能保存主题，请检查剩余空间后重试。")})
        }){Text(tr(R.string.card_delete,"删除主题"))}},dismissButton={TextButton(enabled=!busy,onClick={delete=false}){Text(tr(R.string.ui_2cd0f3be8738,"取消"))}})
    }
}
