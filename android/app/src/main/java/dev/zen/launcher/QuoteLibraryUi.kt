package dev.zen.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import java.time.LocalDate

private val quoteCategories get()=listOf("scene" to tr(R.string.ui_0f70eef46ff0, "风景"),"focus" to tr(R.string.ui_41d42ade41e8, "专注"),"rest" to tr(R.string.ui_fff33732fc97, "休息"))

@Composable private fun QuoteFilter(label:String,value:String,choices:List<Pair<String,String>>,tag:String,modifier:Modifier=Modifier,onChange:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box(modifier){
        TextButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().testTag(tag),contentPadding=PaddingValues(horizontal=4.dp)){
            Text("${if(label.isBlank())""else"$label · "}${choices.find{it.first==value}?.second.orEmpty()} ▾",fontSize=12.sp)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}){choices.forEach{(id,name)->
            DropdownMenuItem(text={Text(name)},onClick={expanded=false;onChange(id)},modifier=Modifier.testTag("$tag-$id"))
        }}
    }
}

@Composable fun QuoteLibraryPanel(a:MainActivity,s:AppState,now:Long){
    var theme by rememberSaveable{mutableStateOf(s.settings.theme)}
    var phase by rememberSaveable{mutableStateOf(if(isNight(s,now))"night"else"day")}
    var weather by rememberSaveable{mutableStateOf(if(WeatherRules.usable(s.weather,now))WeatherRules.category(s.weather.code,s.weather.wind)else"unknown")}
    var category by rememberSaveable{mutableStateOf(s.settings.category)}
    var month by rememberSaveable{mutableIntStateOf(LocalDate.now().monthValue)}
    var deleted by rememberSaveable{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf<String?>(null)}
    var draft by rememberSaveable{mutableStateOf("")}
    var original by rememberSaveable{mutableStateOf("")}
    var discard by remember{mutableStateOf(false)}
    val scope=quoteScope(theme,phase,weather,category)
    val seasonal=remember(theme,s.themeCards){theme in listOf("cloud","river")||s.themeCards.find{it.id==theme}?.card?.quotes?.any{it.months.isNotEmpty()}==true}
    val rows=rememberLibraryRows(a,s,theme,phase,weather,category,month,includeDeleted=true)
    val visible=remember(rows,deleted){rows.filter{it.deleted==deleted}}
    val listState=rememberLazyListState()
    var savedId by remember{mutableStateOf<String?>(null)}
    var undoScope by remember{mutableStateOf("")}
    var undoId by remember{mutableStateOf<String?>(null)}
    var undoEdit by remember{mutableStateOf<QuoteEdit?>(null)}
    LaunchedEffect(scope,month,deleted){listState.scrollToItem(0)}
    LaunchedEffect(savedId,visible){
        val index=visible.indexOfFirst{it.id==savedId}
        if(savedId!=null&&index>=0){listState.scrollToItem(index+1);savedId=null}
    }
    fun begin(row:LibraryQuote?){editing=row?.id?:"new";draft=row?.text.orEmpty();original=draft}
    fun dismiss(){if(busy)return;if(draft!=original)discard=true else editing=null}
    LazyPanel(tr(R.string.ui_74b0faa91cb2, "文案管理"),a,listState=listState,pinned={
        Row(Modifier.fillMaxWidth()){
            QuoteFilter(tr(R.string.ui_788db1cfec2a, "主题"),theme,themes.map{it.key to it.value},"quote-theme",Modifier.weight(1f)){theme=it}
            QuoteFilter(tr(R.string.ui_ac3ff4261fc5, "时段"),phase,listOf("day" to tr(R.string.ui_6fa7e15a01ab, "白天"),"night" to tr(R.string.ui_b77ea2b347b8, "夜晚")),"quote-phase",Modifier.weight(1f)){phase=it}
        }
        Row(Modifier.fillMaxWidth()){
            QuoteFilter(tr(R.string.ui_4d947325473f, "天气"),weather,weatherNames.map{it.key to it.value},"quote-weather",Modifier.weight(1f)){weather=it}
            QuoteFilter(tr(R.string.ui_515559957fd3, "分类"),category,quoteCategories,"quote-category",Modifier.weight(1f)){category=it}
            if(seasonal)QuoteFilter("",month.toString(),(1..12).map{it.toString() to tr(R.string.ui_21b472a93a13, "%1\$s月", it)},"quote-month",Modifier.weight(.8f)){month=it.toInt()}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={deleted=!deleted},modifier=Modifier.weight(1f).testTag("quote-deleted-toggle")){Text(if(deleted)tr(R.string.ui_e6dc7b44b00b, "已删除 %1\$s · 查看在用", visible.size)else tr(R.string.ui_677f31d275b4, "在用 %1\$s · 查看已删除", visible.size),fontSize=12.sp)}
            TextButton(onClick={begin(null)},enabled=!busy,modifier=Modifier.testTag("quote-add")){Text(tr(R.string.ui_7a8a11ead507, "添加"))}
        }
        if(undoId!=null)TextButton(onClick={
            val key=undoId;val previous=undoEdit;val oldScope=undoScope
            busy=true;a.saveTools({state->state.copy(quoteEdits=state.quoteEdits.filterNot{it.id==key&&it.scope==oldScope}+listOfNotNull(previous))}){ok->busy=false;if(ok){undoId=null;if(oldScope==scope)savedId=key}}
        },enabled=!busy,modifier=Modifier.testTag("quote-undo")){Text(tr(R.string.ui_4f70fe468770, "已删除 · 撤销"))}
        HorizontalDivider()
    }){
        item{Text(tr(R.string.ui_7b3768d5d4e5, "只修改当前主题、时段、天气与分类下的文案。"),fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)}
        if(visible.isEmpty())item{Text(if(deleted)tr(R.string.ui_7bbf189a0432, "没有已删除的文案")else tr(R.string.ui_7d58c467bb2a, "此条件下暂无文案，可自行添加。"),modifier=Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.secondary)}
        items(visible,key={it.id}){row->
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("quote-row-${row.id}")){
                Text(row.text,fontFamily=ZenSerif,fontSize=16.sp,lineHeight=26.sp)
                Text(row.credit,fontSize=11.sp,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.padding(top=6.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    if(row.deleted||row.edited)TextButton(onClick={busy=true;a.saveTools({QuoteLibrary.restore(it,scope,row)}){busy=false}},enabled=!busy){Text(if(row.custom)tr(R.string.ui_e0534b8a4e46, "恢复")else tr(R.string.ui_3bfddecb8ca8, "恢复原文"),fontSize=12.sp)}
                    if(!row.deleted){
                        TextButton(onClick={begin(row)},enabled=!busy){Text(tr(R.string.ui_051836569928, "编辑"),fontSize=12.sp)}
                        TextButton(onClick={
                            val previous=s.quoteEdits.find{it.scope==scope&&it.id==row.id};val oldScope=scope
                            busy=true;a.saveTools({QuoteLibrary.delete(it,oldScope,row)}){ok->busy=false;if(ok){undoId=row.id;undoScope=oldScope;undoEdit=previous}}
                        },enabled=!busy){Text(tr(R.string.ui_2f9daa828907, "删除"),fontSize=12.sp)}
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if(editing!=null)AlertDialog(onDismissRequest={dismiss()},title={Text(if(editing=="new")tr(R.string.ui_3f6c5e49838b, "添加文案")else tr(R.string.ui_763deb6b70d8, "编辑文案"))},text={
        Column(Modifier.verticalScroll(rememberScrollState())){
            Text("${themes[theme]} · ${if(phase=="night")tr(R.string.ui_b77ea2b347b8, "夜晚")else tr(R.string.ui_6fa7e15a01ab, "白天")} · ${weatherNames[weather]} · ${quoteCategories.find{it.first==category}?.second}",fontSize=12.sp)
            OutlinedTextField(draft,{if(it.length<=160)draft=it},enabled=!busy,label={Text(tr(R.string.ui_7c84b9773c6f, "文案内容"))},supportingText={Text("${draft.length}/160")},modifier=Modifier.fillMaxWidth().testTag("quote-draft"),minLines=2,maxLines=5)
            Text(tr(R.string.ui_db38c0ef74bd, "修改后的内容标记为“用户编辑”，不再当作原作引文。"),fontSize=11.sp)
        }
    },confirmButton={TextButton(onClick={
        val row=rows.find{it.id==editing};val value=draft;busy=true
        var id:String?=null
        a.saveTools({QuoteLibrary.save(it,scope,value,row).also{next->id=next.quoteEdits.last().id}}){ok->busy=false;if(ok){editing=null;deleted=false;savedId=id}}
    },enabled=!busy&&draft.isNotBlank(),modifier=Modifier.testTag("quote-save")){Text(tr(R.string.ui_a3030bf8f16d, "保存"))}},dismissButton={TextButton(onClick={dismiss()},enabled=!busy){Text(tr(R.string.ui_2cd0f3be8738, "取消"))}})
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text(tr(R.string.ui_01aeaa37eede, "放弃未保存的修改？"))},confirmButton={TextButton(onClick={discard=false;editing=null}){Text(tr(R.string.ui_9b7824cefa1e, "放弃修改"))}},dismissButton={TextButton(onClick={discard=false}){Text(tr(R.string.ui_fd4b9e3b6c68, "继续编辑"))}})
}
