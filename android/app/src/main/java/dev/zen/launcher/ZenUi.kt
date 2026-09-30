@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.zen.launcher

import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val ZenSerif=FontFamily(Font(R.font.zen_serif))
@Composable fun ZenRoot(a:MainActivity) {
    val stored by a.zen().store.state.collectAsState()
    val storeError by a.zen().store.error.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var active by remember { mutableStateOf(false) }
    var sceneInteraction by remember { mutableStateOf<SceneInteraction?>(null) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        active=true
        try {
            coroutineScope {
                launch { while(true){a.weatherLocation.refresh();a.weather.fetch();delay(30_000)} }
                // Starting/pausing immediately restarts the ticker, even during its minute sleep.
                a.zen().store.state.map{it.timer.status=="running"}.distinctUntilChanged().collectLatest { running->
                    var lastSettlement=0L
                    while(true){
                        now=System.currentTimeMillis()
                        val elapsed=android.os.SystemClock.elapsedRealtime()
                        if(running&&elapsed-lastSettlement>=15_000){
                            lastSettlement=elapsed;a.perform({a.zen().platform.settleTimer()})
                        }
                        delay(EnergyPolicy.tickDelay(running,now))
                    }
                }
            }
        }
        finally { active=false;a.perform({a.zen().platform.settleTimer()}) }
    } }
    val boot=remember(a) { bootId(a) }
    val s=remember(stored,now) {FocusLedger.advance(stored,android.os.SystemClock.elapsedRealtime(),now,boot,currentZone())}
    val quiet=FocusFlow.quiet(s)
    var expandedHome by rememberSaveable(s.timer.session){mutableStateOf(false)}
    LaunchedEffect(s.timer.status,stored.timer.generation) {
        if(s.timer.status=="complete"&&stored.timer.status=="running")a.perform({a.zen().platform.settleTimer()})
    }
    val dark=isNight(s,now);val p=palette(s.settings.theme,dark)
    val colors=remember(dark,p){if(dark)darkColorScheme(primary=p.accent,onPrimary=p.sky,surface=p.paper,onSurface=p.ink,background=p.sky,onBackground=p.ink,secondary=p.muted)
        else lightColorScheme(primary=p.accent,onPrimary=p.paper,surface=p.paper,onSurface=p.ink,background=p.sky,onBackground=p.ink,secondary=p.muted)}
    LaunchedEffect(dark,p.paper) {
        // Keep icon appearance and the three-button scrim on the same app theme.
        val status=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        val navigation=if(dark)SystemBarStyle.dark(p.paper.toArgb())
            else SystemBarStyle.light(p.paper.toArgb(),p.paper.toArgb())
        a.enableEdgeToEdge(statusBarStyle=status,navigationBarStyle=navigation)
    }
    val themed=remember(colors,p){colors.copy(primaryContainer=lerp(p.paper,p.accent,.16f),onPrimaryContainer=p.ink,
        secondaryContainer=lerp(p.paper,p.accent,.12f),onSecondaryContainer=p.ink,
        tertiary=p.accent,onTertiary=p.paper,tertiaryContainer=lerp(p.paper,p.accent,.12f),onTertiaryContainer=p.ink,
        surfaceVariant=lerp(p.paper,p.accent,.08f),onSurfaceVariant=p.muted,
        outline=p.muted.copy(alpha=.5f),outlineVariant=p.muted.copy(alpha=.2f),surfaceTint=p.accent,
        surfaceContainerLowest=p.paper,surfaceContainerLow=lerp(p.paper,p.accent,.03f),
        surfaceContainer=lerp(p.paper,p.accent,.05f),surfaceContainerHigh=lerp(p.paper,p.accent,.08f),surfaceContainerHighest=lerp(p.paper,p.accent,.12f))}
    MaterialTheme(colorScheme=themed) {
        Box(Modifier.fillMaxSize().pointerInput(s.settings.motion,s.settings.energySaving,a.panel,quiet) {
            if(s.settings.motion && !s.settings.energySaving && a.panel.isEmpty()&&!quiet)awaitPointerEventScope {
                var start:Offset?=null
                var moved=false
                var startedAt=0L
                while(true){
                    val event=awaitPointerEvent(PointerEventPass.Final)
                    event.changes.forEach{change->
                        if(change.pressed&&!change.previousPressed){start=change.position;moved=event.changes.size>1;startedAt=System.nanoTime()}
                        val origin=start
                        if(origin!=null&&(change.position-origin).getDistance()>viewConfiguration.touchSlop)moved=true
                        if(!change.pressed&&change.previousPressed){
                            if(event.changes.size==1&&!moved&&System.nanoTime()-startedAt<500_000_000L&&size.width>0&&size.height>0)
                                sceneInteraction=SceneInteraction(change.position.x/size.width,change.position.y/size.height)
                            start=null
                        }
                    }
                }
            }
        }) {
            ZenHomeBackHandler(a)
            Landscape(stored,now,active && !quiet && a.panel.isEmpty(),if(quiet)null else sceneInteraction)
            Box(if(a.panel.isEmpty())Modifier else Modifier.clearAndSetSemantics {}) {
                if(quiet&&!expandedHome)QuietFocusHome(a,s,p,dark){expandedHome=true}
                else Home(a,s,now,dark,p,quiet){expandedHome=false}
            }
            if(a.panel.isNotEmpty()) ZenPanel(a,s.settings.motion,p.paper){Panels(a,stored,now/60_000*60_000)}
            if(active&&stored.timer.status=="complete"&&!stored.timer.completionAcknowledged) {
                val completedGeneration=stored.timer.generation
                AlertDialog(onDismissRequest={a.perform({a.zen().platform.acknowledgeTimer(completedGeneration)})},
                    title={Text(if(stored.timer.mode=="focus")tr(R.string.ui_66ee968257ec, "专注完成")else tr(R.string.ui_91dcd1703b98, "休息结束"),fontFamily=ZenSerif)},
                    text={Column{
                        Text(if(stored.timer.mode=="focus")tr(R.string.ui_df7640204fd4, "这一段已记入专注统计。歇一会儿吧。")else tr(R.string.ui_6bb239dcfc95, "准备好了，再开始专注。"))
                        if(stored.timer.mode=="focus"&&stored.timer.todoId!=null&&FocusFlow.rememberedTask(stored)?.id==stored.timer.todoId)
                            TextButton(onClick={a.perform({a.zen().platform.acknowledgeTimer(completedGeneration)},{a.panel="focus-note"})}){Text(tr(R.string.ui_dceac6bbdc4f, "记录下一步（可选）"))}
                    }},
                    confirmButton={TextButton(onClick={a.perform({a.zen().platform.startAfterCompletion(completedGeneration)})}){Text(if(stored.timer.mode=="focus")tr(R.string.ui_7d4beb773506, "开始休息")else tr(R.string.ui_033df8fb6e36, "开始专注"))}},
                    dismissButton={TextButton(onClick={a.perform({a.zen().platform.acknowledgeTimer(completedGeneration)})}){Text(tr(R.string.ui_56f252b08c5a, "关闭提醒"))}})
            }
            val error=a.message.ifEmpty{storeError}
            if(error.isNotEmpty())Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),action={TextButton(onClick={a.message="";a.zen().store.error.value=""}){Text(tr(R.string.ui_de32e20193ad, "知道了"))}}){Text(error)}
        }
    }
}

/** Only the new page exists: no duplicate controls, stale routes or delayed click handlers. */
@Composable internal fun PanelArrival(route:String,motion:Boolean,content:@Composable ()->Unit) {
    key(route) {
        val arrival=remember { Animatable(if(motion).86f else 1f) }
        LaunchedEffect(motion) { arrival.animateTo(1f,tween(if(motion)160 else 0)) }
        Box(Modifier.graphicsLayer {
            alpha=arrival.value
            translationY=(1f-arrival.value)/.14f*6.dp.toPx()
        }) { content() }
    }
}
@Composable private fun Home(a:MainActivity,s:AppState,now:Long,dark:Boolean,p:Palette,canCollapse:Boolean=false,onCollapse:()->Unit={}) {
    val date=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());val t=s.timer
    val progress=if(t.status=="idle")0f else 1f-t.remainingMs.toFloat()/t.durationMs
    val weather=if(WeatherRules.usable(s.weather,now))WeatherRules.category(s.weather.code,s.weather.wind)else"unknown"
    val key="${s.settings.theme}/${if(dark)"night" else "day"}/$weather/${s.settings.category}"
    val rows=rememberLibraryRows(a,s,s.settings.theme,if(dark)"night"else"day",weather,s.settings.category,date.monthValue)
    val pool=rows.map{it.text}
    val copy=pool.getOrNull(ThemeQuotes.index(s.copyIndices[key]?:0,pool.size)).orEmpty()
    val density=LocalDensity.current
    val access=rememberTimerAccess(a)
    var timerBusy by remember { mutableStateOf(false) }
    fun toggle(){if(timerBusy)return;if((!access.timerNotifications||!access.exact)&&t.status in listOf("idle","complete"))a.panel="timer" else {timerBusy=true;a.perform({runCatching{a.zen().platform.toggleTimer()}.onFailure{a.zen().store.error.value=tr(R.string.ui_6b614b4ff406, "计时操作未完成，请重试。")}},{timerBusy=false})}}
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Reserve enough space for the entire focus block before distributing the two weights.
        // Short screens scroll; buttons must never be measured into a clipped sliver.
        val bottomGap=if(!s.settings.collapseHomeApps&&s.settings.theme !in paintedThemes)44.dp else 0.dp
        val dockHeight=if(s.settings.collapseHomeApps)128.dp else 80.dp
        val height=maxOf(maxHeight.value,if(density.fontScale>1.1f)880f else 740f).dp-dockHeight-bottomGap
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max=480.dp).fillMaxWidth().height(height).padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(canCollapse)TextButton(onClick=onCollapse,modifier=Modifier.weight(1f).testTag("collapse-home")){Text(tr(R.string.ui_77b797e7b179, "收起桌面"),fontSize=12.sp,color=p.muted)}
                    else Text(tr(R.string.ui_c83f7e6e0056, "禅"),fontFamily=ZenSerif,fontSize=24.sp,color=p.muted,modifier=Modifier.weight(1f))
                    IconButton(onClick={a.panel="todo"},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_eab60d7fb33d, "每日待办")}){LineIcon("todo",p.muted,s.settings.theme)}
                    IconButton(onClick={a.panel="calendar"},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_305471d2e26d, "专注统计")}){LineIcon("stats",p.muted,s.settings.theme)}
                    IconButton(onClick={a.panel="search"},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_4c2f7d4f1ced, "查找应用")}){LineIcon("search",p.muted,s.settings.theme)}
                    IconButton(onClick={a.panel="settings"},modifier=Modifier.semantics{contentDescription=tr(R.string.ui_df3d58c7d84b, "设置")}){LineIcon("more",p.muted,s.settings.theme)}
                }
                Box(Modifier.fillMaxWidth().weight(.382f),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(date.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=62.sp,fontWeight=FontWeight.ExtraLight,letterSpacing=(-2).sp,color=p.ink,modifier=Modifier.testTag("home-clock"))
                        Spacer(Modifier.height(8.dp));Text(date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(UiLanguage.locale())),fontSize=11.sp,color=p.muted)
                    }
                }
                Column(Modifier.fillMaxWidth().weight(.618f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=if(s.settings.theme in paintedThemes)Arrangement.Top else Arrangement.Center) {
                    TextButton(onClick={a.panel="weather"},contentPadding=PaddingValues(4.dp)) {
                        val caption=if(s.weather.city==null)tr(R.string.ui_0e48706073f3, "开启所在地天气") else if(!WeatherRules.usable(s.weather,now))tr(R.string.ui_efda4ae708c1, "%1\$s · 天气待更新", cityDisplayName(s.weather.city)) else
                            "${cityDisplayName(s.weather.city)} · ${weatherNames[weather]} ${s.weather.temp?.toInt()}°${if(WeatherRules.stale(s.weather,now))tr(R.string.ui_d816dc83a4c4, " · 旧数据") else ""}"
                        Text(caption,fontSize=11.sp,color=p.muted,textAlign=TextAlign.Center)
                    }
                    if(t.taskTitle.isNotBlank()&&t.status!="idle")Text(t.taskTitle,fontFamily=ZenSerif,fontSize=17.sp,lineHeight=26.sp,textAlign=TextAlign.Center,color=p.ink,modifier=Modifier.padding(vertical=8.dp).testTag("focus-task"))
                    else Box(Modifier.widthIn(max=300.dp).fillMaxWidth().heightIn(min=66.dp).combinedClickable(onClickLabel=tr(R.string.ui_933eeb893dc0, "换一句"),onLongClickLabel=tr(R.string.ui_dc495413b964, "查看文案出处"),onLongClick={a.panel="copy-source"},onClick={
                        a.editState{it.copy(copyIndices=it.copyIndices+(key to ThemeQuotes.next(it.copyIndices[key]?:0,pool.size)))}})
                        .padding(vertical=8.dp),contentAlignment=Alignment.Center){Text(copy,fontFamily=ZenSerif,fontSize=15.sp,lineHeight=28.sp,letterSpacing=.8.sp,textAlign=TextAlign.Center,color=p.ink)}
                    Spacer(Modifier.height(12.dp))
                    val seconds=if(t.status=="idle")s.settings.focusDurationSeconds.toLong() else (t.remainingMs+999)/1000
                    Column(Modifier.fillMaxWidth().testTag("focus-glyph")
                        .combinedClickable(enabled=!timerBusy,onClick={toggle()},onLongClick={a.panel="timer"},onClickLabel=when(t.status){"running"->tr(R.string.ui_971f2f448772, "暂停计时");"paused"->tr(R.string.ui_c39c7232d41f, "继续计时");else->tr(R.string.ui_ba43dc839bd2, "开始计时")},onLongClickLabel=tr(R.string.ui_364c615ec875, "设置时长"))
                        .semantics(mergeDescendants=true){contentDescription=tr(R.string.ui_eb4b8424fbfe, "专注计时图形，轻点开始或暂停，长按展开")},horizontalAlignment=Alignment.CenterHorizontally){
                            TimerGlyph(s.settings.theme,dark,progress,Modifier.fillMaxWidth().height(64.dp))
                            Text(countdownText(seconds),fontFamily=ZenSerif,fontSize=17.sp,color=p.ink)
                            Text(when(t.status){"running"->if(t.mode=="focus")tr(R.string.ui_3653f8589001, "专注中 · 点击暂停") else tr(R.string.ui_51d04481821a, "休息中 · 点击暂停");"paused"->tr(R.string.ui_132f4a6e00d8, "已暂停 · 点击继续");"complete"->if(t.mode=="focus")tr(R.string.ui_1495e0fabd4b, "专注完成 · 开始休息") else tr(R.string.ui_622cdfa62da1, "休息结束 · 开始专注");else->tr(R.string.ui_033df8fb6e36, "开始专注")},fontSize=10.sp,color=p.muted,modifier=Modifier.padding(bottom=8.dp))
                    }
                    Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={a.panel="timer"}){Text(tr(R.string.ui_657d4c376afd, "调整时长"),fontSize=11.sp,color=p.muted)}
                        if(t.status!="idle")TextButton(onClick={a.perform({a.zen().platform.endTimer()})}){Text(tr(R.string.ui_c7b24e7997e9, "结束"),fontSize=11.sp,color=p.muted)}
                    }
                }
            }
        }
        HomeAppDock(a,s,p)
        Spacer(Modifier.height(bottomGap))
        }
    }
}

/** Fixed in the safe area; opening the apps never changes the space used by home content. */
@Composable private fun HomeAppDock(a:MainActivity,s:AppState,p:Palette) {
    val turn by animateFloatAsState(if(a.homeAppsExpanded)180f else 0f,
        animationSpec=tween(if(s.settings.motion)180 else 0),label="dock-turn")
    Column(Modifier.widthIn(max=480.dp).fillMaxWidth().padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().heightIn(min=80.dp).testTag("home-apps-slot"),contentAlignment=Alignment.Center) {
            if(!s.settings.collapseHomeApps||a.homeAppsExpanded)PanelArrival("home-apps",s.settings.motion) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    if(s.settings.favorites.isEmpty())TextButton(onClick={a.panel="favorites"}){Text(tr(R.string.ui_a95011c4231d,"＋ 添加首页应用"),fontSize=13.sp,color=p.muted)}
                    else s.settings.favorites.forEach{pkg->val entry=a.apps.find{it.pkg==pkg}
                        Column(Modifier.weight(1f).heightIn(min=72.dp).combinedClickable(onClick={if(entry!=null)a.openApp(entry)else{a.message=tr(R.string.ui_f9869b0403ce,"此应用已不可用，可从管理中移除。");a.panel="favorites"}},onLongClick={a.panel="favorites"}).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            if(entry!=null)AppIcon(entry,Modifier.size(28.dp))else Text("○",fontSize=24.sp,color=p.muted)
                            Spacer(Modifier.height(10.dp));Text(entry?.name?:tr(R.string.ui_8cc1f144517d,"已不可用"),fontSize=11.sp,textAlign=TextAlign.Center,color=p.ink,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if(s.settings.collapseHomeApps)IconButton(onClick={a.homeAppsExpanded=!a.homeAppsExpanded},modifier=Modifier.size(48.dp).testTag("home-apps-toggle").semantics{
            contentDescription=if(a.homeAppsExpanded)tr(R.string.ui_6d92d91777ed,"收起应用")else tr(R.string.dock_expand,"展开应用")
            stateDescription=if(a.homeAppsExpanded)tr(R.string.ui_df647b73b990,"已展开")else tr(R.string.ui_ee60469308d3,"已折叠")
        }){
            Canvas(Modifier.size(18.dp).graphicsLayer{rotationZ=turn}.testTag("home-apps-dots")){
                val step=3.dp.toPx()
                for(x in listOf(-1,1))for(y in listOf(-1,1))drawCircle(p.muted,1.3.dp.toPx(),center+Offset(x*step,y*step),style=Stroke(1.dp.toPx()))
            }
        }
    }
}
/** One semantic family; softness follows the scene without changing recognition. */
@Composable fun LineIcon(kind:String,color:Color,theme:String="water") { Canvas(Modifier.size(22.dp)) {
    val unit=size.width/24f;val stroke=1.35.dp.toPx()
    fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x*unit,y*unit),Offset(x2*unit,y2*unit),stroke,StrokeCap.Round)
    val radius=when(ThemeCards.base(theme)){"paper"->1.5f;"forest"->2.5f;"dawn"->5f;"dusk"->3f;else->4f}*unit
    fun path(data:String,alpha:Float=1f){
        val shape=PathParser().parsePathString(data).toPath()
        scale(unit,unit,pivot=Offset.Zero){drawPath(shape,color.copy(alpha=color.alpha*alpha),style=Stroke(width=1.3f,cap=StrokeCap.Round,join=StrokeJoin.Round))}
    }
    when(kind){
        "search"->{drawCircle(color,6.2f*unit,Offset(10.3f*unit,10.3f*unit),style=Stroke(stroke));line(15f,15f,20f,20f)}
        "todo","notice"->{
            drawRoundRect(color,Offset(5*unit,3.5f*unit),androidx.compose.ui.geometry.Size(14*unit,17*unit),androidx.compose.ui.geometry.CornerRadius(radius),style=Stroke(stroke))
            path("M8 9l1.4 1.4L12 7.8M14 9h2M8 15h8")
        }
        "stats"->{
            path("M5 16.5v-4a1.5 1.5 0 0 1 3 0v4M10.5 16.5v-10a1.5 1.5 0 0 1 3 0v10M16 16.5v-6.5a1.5 1.5 0 0 1 3 0v6.5")
            path(when(ThemeCards.base(theme)){"water"->"M4 20Q8 19 12 20T20 20";"forest"->"M4 20Q12 19.5 20 20";"dawn"->"M4 20Q12 18.8 20 20";"dusk"->"M4 20Q9 20 12 19.3Q16 19.8 20 20";"paper"->"M4 20h16";else->"M4 20Q12 20.8 20 20"},.5f)
        }
        "close"->{line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
        "back"->{line(14f,6f,8f,12f);line(8f,12f,14f,18f)}
        "next"->{line(10f,6f,16f,12f);line(16f,12f,10f,18f)}
        "plus"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
        else->repeat(3){drawCircle(color,1.1.dp.toPx(),Offset((5+it*7)*unit,12*unit))}
    }
} }
@Composable fun AppIcon(entry:AppEntry,modifier:Modifier=Modifier){val bitmap=remember(entry.pkg,entry.icon){entry.icon.toBitmap(72,72).asImageBitmap()};Image(bitmap,contentDescription=null,modifier=modifier)}
