package dev.zen.launcher

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

/** User-enabled overlay. No app inspection, wake lock, periodic polling, or task text in notification. */
class TodoOverlayService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy{getSharedPreferences("todo-overlay",MODE_PRIVATE)}
    private val wm by lazy{getSystemService(WindowManager::class.java)}
    private var root:LinearLayout?=null
    private var expanded=false
    private var compact=false
    private var hiddenUntil=0L
    private var observing:Job?=null
    private var screenOff=false
    private var current:Snapshot?=null
    private data class Snapshot(val enabled:Boolean,val tasks:List<Todo>,val pinned:String,val theme:String,val dark:Boolean)
    private val boundary=Runnable{refresh();scheduleBoundary()}
    private val showAgain=Runnable{hiddenUntil=0;prefs.edit().remove("hiddenUntil").apply();refresh()}
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
        screenOff=i.action==Intent.ACTION_SCREEN_OFF
        if(screenOff){expanded=false;removeWindow()}else{refresh();scheduleBoundary()}
    }}
    companion object{
        const val CHANNEL="todo-overlay"
        const val ID=104
        const val STOP="overlay-stop"
        const val SHOW="overlay-show"
        val hidden=MutableStateFlow(false)
        fun allowed(c:Context)=android.provider.Settings.canDrawOverlays(c)
        fun start(c:Context):Boolean=runCatching{
            ContextCompat.startForegroundService(c,Intent(c,TodoOverlayService::class.java));true
        }.getOrDefault(false)
    }
    override fun onCreate(){
        super.onCreate()
        hiddenUntil=prefs.getLong("hiddenUntil",0)
        compact=prefs.getBoolean("compact",false)
        ContextCompat.registerReceiver(this,receiver,IntentFilter().apply{
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){disable();return START_NOT_STICKY}
        if(!zen().store.state.value.settings.todoOverlay||!allowed(this)||!zen().platform.hasNotifications()){
            stopSelf();return START_NOT_STICKY
        }
        if(intent?.action==SHOW){hiddenUntil=0;prefs.edit().remove("hiddenUntil").apply();handler.removeCallbacks(showAgain)}
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,tr(R.string.overlay_title,"跨应用待办"),NotificationManager.IMPORTANCE_LOW).apply{
            setSound(null,null);enableVibration(false);setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID,notification())
        }catch(_:Exception){stopSelf();return START_NOT_STICKY}
        if(observing==null)observing=scope.launch{
            zen().store.state.map{snapshot(it)}.distinctUntilChanged().collect{current=it;render()}
        }
        refresh();scheduleBoundary()
        handler.removeCallbacks(showAgain)
        if(hiddenUntil>System.currentTimeMillis())handler.postDelayed(showAgain,hiddenUntil-System.currentTimeMillis())
        return START_STICKY
    }
    private fun notification():Notification{
        val open=PendingIntent.getActivity(this,104,Intent(this,MainActivity::class.java).putExtra("open-todos",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(value:String)=PendingIntent.getService(this,value.hashCode(),Intent(this,TodoOverlayService::class.java).setAction(value),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_zen).setContentTitle(tr(R.string.overlay_title,"跨应用待办"))
            .setContentText(tr(R.string.overlay_notification,"点击管理待办，可随时关闭悬浮提醒"))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null,tr(R.string.overlay_restore,"恢复显示"),action(SHOW)).build())
            .addAction(Notification.Action.Builder(null,tr(R.string.overlay_disable,"关闭悬浮"),action(STOP)).build()).build()
    }
    private fun snapshot(s:AppState)=Snapshot(s.settings.todoOverlay,TodoOverlayPolicy.pending(s.todos,LocalDate.now().toString()),s.settings.overlayPinnedId,s.settings.theme,isNight(s,System.currentTimeMillis()))
    private fun refresh(){current=snapshot(zen().store.state.value);render()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun removeWindow(){root?.let{runCatching{wm.removeViewImmediate(it)}};root=null}
    private fun render(){
        val s=current?:return
        hidden.value=hiddenUntil>System.currentTimeMillis()
        if(!s.enabled||!allowed(this)||!zen().platform.hasNotifications()){removeWindow();stopSelf();return}
        val locked=screenOff||!getSystemService(PowerManager::class.java).isInteractive||getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if(!TodoOverlayPolicy.visible(s.enabled,true,locked,s.tasks.size,hiddenUntil,System.currentTimeMillis())){removeWindow();return}
        removeWindow()
        val p=palette(s.theme,s.dark);val ink=p.ink.toArgb()
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(if(compact)2 else 12),dp(4),dp(if(compact)2 else 12),dp(4));elevation=dp(3).toFloat()
            background=GradientDrawable().apply{setColor(p.paper.toArgb());cornerRadius=dp(if(compact)16 else if(expanded)20 else 24).toFloat();setStroke(dp(1),p.muted.copy(alpha=.2f).toArgb())}
        }
        fun label(text:String,size:Float=13f)=TextView(this).apply{this.text=text;textSize=size;setTextColor(ink);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),0)}
        fun button(text:String,action:()->Unit)=label(text,12f).apply{
            minHeight=dp(48);gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setOnClickListener{action()}
            contentDescription=text
        }
        val header=label(if(compact)"○ ${s.tasks.size}"else "○  ${TodoOverlayPolicy.selected(s.tasks,s.pinned)?.text.orEmpty()}  · ${s.tasks.size}").apply{
            setSingleLine();minHeight=dp(44);ellipsize=TextUtils.TruncateAt.END
            if(compact)gravity=Gravity.CENTER
            contentDescription=if(compact)tr(R.string.overlay_badge_description,"未完成 %1\$s 项待办。点击恢复短签，拖动调整位置。",s.tasks.size)
                else if(expanded)tr(R.string.overlay_chip_expanded,"待办：%1\$s；未完成 %2\$s 项。点击收起，拖动调整位置。",TodoOverlayPolicy.selected(s.tasks,s.pinned)?.text.orEmpty(),s.tasks.size)
                else tr(R.string.overlay_chip_description,"待办：%1\$s；未完成 %2\$s 项。点击展开，拖动调整位置。",TodoOverlayPolicy.selected(s.tasks,s.pinned)?.text.orEmpty(),s.tasks.size)
            isClickable=true;isFocusable=true;setOnClickListener{
                if(compact){compact=false;expanded=false;prefs.edit().putBoolean("compact",false).apply()}else expanded=!expanded
                render()
            }
        }
        val headerRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        headerRow.addView(header,LinearLayout.LayoutParams(0,-2,1f))
        if(!compact)headerRow.addView(button("−"){
            compact=true;expanded=false;prefs.edit().putBoolean("compact",true).apply();render()
        }.apply{contentDescription=tr(R.string.overlay_compact,"收为数量标");textSize=20f},LinearLayout.LayoutParams(dp(44),dp(44)))
        panel.addView(headerRow)
        if(expanded){
            val scroll=object:ScrollView(this){override fun onMeasure(widthSpec:Int,heightSpec:Int){
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(dp(180),View.MeasureSpec.AT_MOST))
            }}
            val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            s.tasks.forEach{task->
                val check=CheckBox(this).apply{
                    text=task.text;textSize=14f;setTextColor(ink);minHeight=dp(48);setPadding(0,dp(4),0,dp(4))
                    buttonTintList=android.content.res.ColorStateList.valueOf(p.accent.toArgb())
                    setOnCheckedChangeListener{_,done->if(done){isEnabled=false;zen().perform({zen().store.update{DailyTools.completeTodo(it,task.id,true)}},{
                        if(zen().store.state.value.todos.any{it.id==task.id&&!it.done}){refresh();Toast.makeText(this@TodoOverlayService,zen().store.error.value,Toast.LENGTH_LONG).show()}
                    })}}
                };list.addView(check)
            }
            scroll.addView(list);panel.addView(scroll,LinearLayout.LayoutParams(-1,-2))
            val actions=LinearLayout(this)
            actions.addView(button(tr(R.string.overlay_next,"切换置顶")){
                val at=s.tasks.indexOfFirst{it.id==s.pinned}.coerceAtLeast(0)
                zen().perform({zen().store.update{it.copy(settings=it.settings.copy(overlayPinnedId=s.tasks[(at+1)%s.tasks.size].id))}})
            },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(button(tr(R.string.overlay_hide,"隐藏 30 分钟")){
                expanded=false;hiddenUntil=System.currentTimeMillis()+1_800_000;prefs.edit().putLong("hiddenUntil",hiddenUntil).apply()
                hidden.value=true;removeWindow();handler.removeCallbacks(showAgain);handler.postDelayed(showAgain,1_800_000)
            },LinearLayout.LayoutParams(0,-2,1f))
            panel.addView(actions)
            panel.addView(button(tr(R.string.overlay_disable,"关闭悬浮")){disable()})
        }
        val width=resources.displayMetrics.widthPixels;val height=resources.displayMetrics.heightPixels
        val horizontal=if(width>height)"land" else "port"
        val panelWidth=minOf(dp(if(compact)48 else if(expanded)288 else 212),width-dp(24))
        val positionKey=if(compact)"compact-$horizontal"else horizontal
        val maxY=(height-dp(if(expanded)400 else 110)).coerceAtLeast(dp(8))
        val params=WindowManager.LayoutParams(panelWidth,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{
            gravity=Gravity.TOP or Gravity.LEFT
            x=(prefs.getFloat("x-$positionKey",if(compact)1f else .5f)*(width-panelWidth)).toInt().coerceIn(0,(width-panelWidth).coerceAtLeast(0))
            y=(prefs.getFloat("y-$positionKey",prefs.getFloat("y-$horizontal",.04f))*height).toInt().coerceIn(dp(8),maxY)
        }
        var downX=0f;var downY=0f;var startX=0;var startY=0;var dragged=false
        val slop=ViewConfiguration.get(this).scaledTouchSlop
        header.setOnTouchListener{v,event->
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY;startX=params.x;startY=params.y;dragged=false;true}
                MotionEvent.ACTION_MOVE->{
                    val dx=event.rawX-downX;val dy=event.rawY-downY
                    if(kotlin.math.abs(dx)+kotlin.math.abs(dy)>slop)dragged=true
                    if(dragged){params.x=(startX+dx).toInt().coerceIn(0,(width-panelWidth).coerceAtLeast(0));params.y=(startY+dy).toInt().coerceIn(dp(8),maxY);runCatching{wm.updateViewLayout(panel,params)}};true
                }
                MotionEvent.ACTION_UP->{
                    if(!dragged)v.performClick()else{
                        prefs.edit().putFloat("x-$positionKey",params.x.toFloat()/(width-panelWidth).coerceAtLeast(1)).putFloat("y-$positionKey",params.y.toFloat()/height).apply()
                        // Notify accessibility clients of the new hit target after a window-only move.
                        v.post{v.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)}
                    };true
                }
                MotionEvent.ACTION_CANCEL->true
                else->false
            }
        }
        try{wm.addView(panel,params);root=panel}catch(_:Exception){root=null;stopSelf()}
    }
    private fun disable(){
        removeWindow()
        zen().perform({zen().store.update{it.copy(settings=it.settings.copy(todoOverlay=false))}},{
            if(zen().store.state.value.settings.todoOverlay){refresh();Toast.makeText(this,zen().store.error.value,Toast.LENGTH_LONG).show()}else stopSelf()
        })
    }
    private fun scheduleBoundary(){
        handler.removeCallbacks(boundary)
        val now=System.currentTimeMillis();val zone=ZoneId.systemDefault();val today=LocalDate.now(zone)
        val w=zen().store.state.value.weather
        val candidates=listOf(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),today.atTime(6,0).atZone(zone).toInstant().toEpochMilli(),today.atTime(18,0).atZone(zone).toInstant().toEpochMilli(),w.sunrise,w.sunset)
        handler.postDelayed(boundary,(candidates.filter{it>now}.minOrNull()?:now+86_400_000)-now+50)
    }
    override fun onConfigurationChanged(config:android.content.res.Configuration){super.onConfigurationChanged(config);UiLanguage.refresh(this);refresh()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);observing?.cancel();scope.cancel();removeWindow();hidden.value=false;unregisterReceiver(receiver);super.onDestroy()}
    override fun onBind(intent:Intent?)=null
}
