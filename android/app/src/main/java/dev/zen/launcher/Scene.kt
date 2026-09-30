package dev.zen.launcher

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Picture
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

val themes get()=linkedMapOf("water" to tr(R.string.ui_ed3885c20243, "水静"),"forest" to tr(R.string.ui_139001abc6d9, "松风"),"dawn" to tr(R.string.ui_cda1b9aa3a17, "晨曦"),"dusk" to tr(R.string.ui_339ca0fd4ac1, "暮山"),"paper" to tr(R.string.ui_726b9af50bdb, "月白"),"night" to tr(R.string.ui_73750a6ae9af, "夜泊")).apply{ThemeCards.entries.forEach{put(it.id,it.card.name)}}
val weatherNames get()=mapOf("clear" to tr(R.string.ui_6379ede5c239, "晴"),"cloudy" to tr(R.string.ui_30c379777e5d, "多云"),"overcast" to tr(R.string.ui_78df214e37ef, "阴"),"fog" to tr(R.string.ui_853385be3d5a, "雾"),"drizzle" to tr(R.string.ui_ed58a6a69d95, "细雨"),"rain" to tr(R.string.ui_124d60580dfa, "雨"),"freezing" to tr(R.string.ui_86879622e5c9, "冻雨"),"snow" to tr(R.string.ui_53058fe2e03c, "雪"),"showers" to tr(R.string.ui_448c8292f481, "阵雨"),"snowshowers" to tr(R.string.ui_0a9c46115749, "阵雪"),"thunder" to tr(R.string.ui_562c94de2922, "雷雨"),"hail" to tr(R.string.ui_33ab42224a7c, "雷雨伴冰雹"),"wind" to tr(R.string.ui_be65bd5518ae, "风"),"unknown" to tr(R.string.ui_b75679ce0385, "天气未知"))
fun isNight(s:AppState,now:Long):Boolean {
    if(s.settings.phase!="auto") return s.settings.phase=="night"
    val w=s.weather
    val zone=runCatching { ZoneId.of(w.zone) }.getOrDefault(ZoneId.systemDefault())
    if(WeatherRules.usable(w,now) && w.sunrise>0 && Instant.ofEpochMilli(w.sunrise).atZone(zone).toLocalDate()==Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) return now<w.sunrise || now>=w.sunset
    return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour !in 6..17
}
data class Palette(val ink:Color,val muted:Color,val sky:Color,val ground:Color,val paper:Color,val accent:Color)
fun palette(theme:String,dark:Boolean):Palette {
    ThemeCards.colors(theme,dark)?.let{return it}
    if(theme in paintedThemes || theme in setOf("cloud","river")) {
        val ink=if(dark)Color(0xfff0ecdf) else Color(0xff293a36)
        val muted=if(dark)Color(0xffd0d8ce) else Color(0xff40554b)
        return Palette(ink,muted,if(dark)Color(0xff182d34) else Color(0xfff5f3e9),
            if(dark)Color(0xff14272c) else Color(0xffd8e0ce),if(dark)Color(0xff20353b) else Color(0xfff7f5ed),muted)
    }
    val hue=mapOf("water" to 194f,"forest" to 100f,"dawn" to 29f,"dusk" to 272f,"paper" to 48f,"night" to 214f)[theme] ?: 194f
    fun c(s:Float,l:Float)=Color.hsl(hue,s,l)
    return Palette(c(.20f,if(dark).87f else .26f),c(.14f,if(dark).70f else .40f),
        c(if(theme=="paper").09f else .25f,if(dark).15f else .97f),c(.25f,if(dark).09f else .88f),c(.20f,if(dark).18f else .97f),c(.26f,if(dark).74f else .38f))
}

/** Renderer deliberately supports only the path/circle/ellipse/group subset in our bundled art. */
object SceneArt {
    private data class Style(val fill:Int?=android.graphics.Color.BLACK,val stroke:Int?=null,val alpha:Float=1f,val width:Float=1f)
    private val cache=android.util.LruCache<String,Picture>(24)
    @Synchronized fun picture(context:Context,file:String,ink:Color,width:Int=480,height:Int=900):Picture {
        val key="$file/${ink.toArgb()}"
        cache.get(key)?.let { return it }
        val xml=if(file.startsWith("motif:")) {
            val json=Json.parseToJsonElement(context.assets.open("motifs.json").bufferedReader().use { it.readText() }).jsonObject
            json[file.removePrefix("motif:")]!!.jsonPrimitive.content
        } else context.assets.open(file).bufferedReader().use { it.readText() }
        val picture=Picture(); val canvas=picture.beginRecording(width,height)
        val parser=Xml.newPullParser(); parser.setInput(xml.reader())
        val styles=ArrayDeque<Style>(); styles.addLast(Style())
        fun color(value:String?,inherited:Int?):Int? = when(value) {
            null -> inherited
            "none" -> null
            "currentColor" -> ink.toArgb()
            else -> android.graphics.Color.parseColor(value)
        }
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG) {
                val parent=styles.last(); fun attr(name:String)=parser.getAttributeValue(null,name)
                val clazz=attr("class") ?: ""
                val classAlpha=when(clazz) { "progress-track"->.19f; "progress-arc"->0f; else->1f }
                val explicitOpacity=Regex("opacity:([.0-9]+)").find(attr("style") ?: "")?.groupValues?.get(1)?.toFloatOrNull()
                val style=Style(color(attr("fill"),parent.fill),color(attr("stroke"),if(clazz in listOf("center-symbol","progress-track")) ink.toArgb() else parent.stroke),
                    parent.alpha*(explicitOpacity ?: attr("opacity")?.toFloatOrNull() ?: 1f)*classAlpha,attr("stroke-width")?.toFloatOrNull() ?: parent.width)
                styles.addLast(style); canvas.save()
                Regex("(translate|scale)\\(([^)]+)\\)").findAll(attr("transform") ?: "").forEach { transform ->
                    val values=transform.groupValues[2].trim().split(Regex("[ ,]+")).map { it.toFloat() }
                    if(transform.groupValues[1]=="translate") canvas.translate(values[0],values.getOrElse(1){0f}) else canvas.scale(values[0],values.getOrElse(1){values[0]})
                }
                val path=when(parser.name) {
                    "path" -> PathParser().parsePathString(attr("d")!!).toPath().asAndroidPath()
                    "circle" -> android.graphics.Path().apply { addCircle(attr("cx")?.toFloat() ?: 0f,attr("cy")?.toFloat() ?: 0f,attr("r")!!.toFloat(),android.graphics.Path.Direction.CW) }
                    "ellipse" -> android.graphics.Path().apply { val x=attr("cx")!!.toFloat();val y=attr("cy")!!.toFloat();val rx=attr("rx")!!.toFloat();val ry=attr("ry")!!.toFloat();addOval(x-rx,y-ry,x+rx,y+ry,android.graphics.Path.Direction.CW) }
                    else -> null
                }
                if(path!=null) {
                    fun paint(c:Int,stroke:Boolean)=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=c;alpha=(android.graphics.Color.alpha(c)*style.alpha).toInt().coerceIn(0,255)
                        this.style=if(stroke)Paint.Style.STROKE else Paint.Style.FILL
                        strokeWidth=style.width;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
                    }
                    style.fill?.let { canvas.drawPath(path,paint(it,false)) }
                    style.stroke?.let { canvas.drawPath(path,paint(it,true)) }
                }
            } else if(parser.eventType==XmlPullParser.END_TAG) { canvas.restore();styles.removeLast() }
            parser.next()
        }
        picture.endRecording();cache.put(key,picture);return picture
    }
}

/** Asset parsing and path recording must not block taps or sheet transitions. */
@Composable private fun rememberScenePicture(file:String,ink:Color,width:Int=480,height:Int=900):Picture? {
    val context=LocalContext.current.applicationContext
    // A new key gets its own null state: never flash the previous theme's artwork.
    return key(file,ink,width,height) {
        val picture by produceState<Picture?>(null) {
            value=withContext(Dispatchers.Default){SceneArt.picture(context,file,ink,width,height)}
        }
        picture
    }
}

private data class SceneBackdrop(val theme:String,val dark:Boolean,val weather:String,val palette:Palette,val picture:Picture?=null,val painting:android.graphics.Bitmap?=null,val clouds:android.graphics.Bitmap?=null)

/** Keep the complete previous scene until the next asset is ready; never expose a blank frame. */
@Composable private fun rememberBackdrop(theme:String,dark:Boolean,weather:String):SceneBackdrop? {
    val context=LocalContext.current.applicationContext
    val backdrop by produceState<SceneBackdrop?>(null,theme,dark,weather) {
        val colors=palette(theme,dark)
        value=withContext(Dispatchers.IO) {
            val card=ThemeCards.find(theme)
            if(card!=null) SceneBackdrop(theme,dark,weather,colors,painting=runCatching{ThemeCards.bitmap(card,dark,weather)}.getOrNull(),
                clouds=if(dark)runCatching{ThemeCards.overlay(card)}.getOrNull() else null)
            else SceneBackdrop(theme,dark,weather,colors,
                picture=SceneArt.picture(context,"scenes/${ThemeCards.base(theme)}-${if(dark)"night" else "day"}-$weather.svg",colors.ink))
        }
    }
    return backdrop
}

@Composable fun Landscape(s:AppState,now:Long,active:Boolean,interaction:SceneInteraction?=null) {
    val dark=isNight(s,now);val p=palette(s.settings.theme,dark)
    val weather=if(WeatherRules.usable(s.weather,now))WeatherRules.category(s.weather.code,s.weather.wind) else "unknown"
    val context=LocalContext.current
    val backdrop=rememberBackdrop(s.settings.theme,dark,weather)
    val power=context.getSystemService(android.os.PowerManager::class.java)
    var systemSaving by remember { mutableStateOf(power.isPowerSaveMode) }
    var animatorsEnabled by remember { mutableStateOf(android.animation.ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context,active) {
        val receiver=object:android.content.BroadcastReceiver(){
            override fun onReceive(c:Context,i:android.content.Intent){systemSaving=power.isPowerSaveMode}
        }
        val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())){
            override fun onChange(selfChange:Boolean){animatorsEnabled=android.animation.ValueAnimator.areAnimatorsEnabled()}
        }
        if(active){
            androidx.core.content.ContextCompat.registerReceiver(context,receiver,
                android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
            systemSaving=power.isPowerSaveMode;animatorsEnabled=android.animation.ValueAnimator.areAnimatorsEnabled()
        }
        onDispose{if(active){context.unregisterReceiver(receiver);context.contentResolver.unregisterContentObserver(observer)}}
    }
    val card=ThemeCards.find(s.settings.theme)?.card
    val moving=card?.effect!="none" && card?.strength!=0f && SceneMotion.isEnabled(active,s.settings.motion,EnergyPolicy.saving(s.settings.energySaving,systemSaving),animatorsEnabled)
    var clock by remember { mutableStateOf(SceneMotionClock()) }
    LaunchedEffect(moving) {
        clock=clock.advance(System.nanoTime(),false)
        if(moving) while(true) {
            clock=clock.advance(System.nanoTime(),true)
            delay(33)
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.sky,p.ground)))) {
      Crossfade(targetState=backdrop,animationSpec=tween(if(s.settings.motion)240 else 0),label="scene-change") { scene ->
       if(scene!=null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scene.palette.sky,scene.palette.ground)))) {
      // Separate display lists: animation invalidates only the lightweight foreground.
      Canvas(Modifier.fillMaxSize().graphicsLayer().testTag("backdrop-${scene.theme}-${scene.dark}")) {
        if(scene.painting!=null) drawPainting(scene.painting,scene.theme,scene.dark,scene.weather,ThemeCards.find(scene.theme)?.card?.veil=="reading",ThemeCards.find(scene.theme)?.card?.veil=="ink")
        else {
            drawIntoCanvas { c->val native=c.nativeCanvas;native.save();val scale=max(size.width/480,size.height/900);native.translate((size.width-480*scale)/2,(size.height-900*scale)/2);native.scale(scale,scale);scene.picture?.let{native.drawPicture(it)};native.restore() }
            drawRect(Brush.radialGradient(listOf(scene.palette.sky.copy(alpha=.15f),Color.Transparent),center=Offset(size.width/2,size.height*.40f),radius=size.width*.7f))
        }
      }
      Canvas(Modifier.fillMaxSize().graphicsLayer().testTag(if(moving)"scene-motion-on" else "scene-motion-off")) {
        if(scene.clouds!=null && scene.painting!=null) drawMoonClouds(scene.clouds,scene.painting,clock.seconds.toFloat())
        if (moving) {
            val touchAge=interaction?.let { ((clock.lastNanos ?: it.createdAtNanos)-it.createdAtNanos)/1_000_000_000f }
                ?: Float.POSITIVE_INFINITY
            val effect=ThemeCards.find(scene.theme)?.card
            SceneMotion.frame(effect?.effect?:scene.theme,scene.dark,scene.weather,clock.seconds.toFloat()*(effect?.speed?:1f),interaction,touchAge).forEach { mark ->
                drawSceneMark(mark.copy(alpha=mark.alpha*(effect?.strength?:1f)),scene.palette)
            }
        }
      }
       }
      }
    }
}

/** Drawing consumes the tested frame geometry; no second, untested set of animation formulas. */
private fun DrawScope.drawSceneMark(mark:SceneMark,p:Palette) {
    val tone=when(mark.tone) {
        SceneTone.ACCENT->p.accent;SceneTone.INK->p.ink;SceneTone.MUTED->p.muted;SceneTone.LIGHT->Color(0xffeee5bc)
    }
    val color=tone.copy(alpha=mark.alpha.coerceIn(0f,1f))
    val origin=Offset(mark.x*size.width,mark.y*size.height)
    val width=mark.width*size.width;val height=mark.height*size.height
    val stroke=mark.stroke*size.width/480
    when(mark.kind) {
        SceneMarkKind.MIST->{
            // An elliptical radial falloff has no hard silhouette at the sides of the mist.
            withTransform({
                translate(origin.x+width/2,origin.y)
                scale(1f,2*height/width,pivot=Offset.Zero)
            }) {
                drawCircle(Brush.radialGradient(listOf(color,color.copy(alpha=color.alpha*.45f),Color.Transparent),
                    center=Offset.Zero,radius=width/2),width/2,Offset.Zero)
            }
        }
        SceneMarkKind.INK->rotate(mark.rotation,origin) {
            // Tapered brush stroke, rather than a leaf or a geometric ring.
            val path=Path().apply {
                moveTo(origin.x-width*.5f,origin.y+height*.25f)
                cubicTo(origin.x-width*.2f,origin.y-height,origin.x+width*.28f,origin.y-height*.35f,origin.x+width*.5f,origin.y)
                cubicTo(origin.x+width*.08f,origin.y-height*.08f,origin.x-width*.26f,origin.y+height*.4f,origin.x-width*.5f,origin.y+height*.25f)
                close()
            }
            drawPath(path,color)
        }
        SceneMarkKind.GLINT->{
            val path=Path().apply {
                moveTo(origin.x,origin.y-height/2)
                quadraticBezierTo(origin.x+width*.1f,origin.y-height*.08f,origin.x+width/2,origin.y)
                quadraticBezierTo(origin.x+width*.1f,origin.y+height*.08f,origin.x,origin.y+height/2)
                quadraticBezierTo(origin.x-width*.1f,origin.y+height*.08f,origin.x-width/2,origin.y)
                quadraticBezierTo(origin.x-width*.1f,origin.y-height*.08f,origin.x,origin.y-height/2)
                close()
            }
            drawPath(path,color)
        }
        SceneMarkKind.RING->drawOval(color,origin,androidx.compose.ui.geometry.Size(width,height),style=Stroke(stroke))
        SceneMarkKind.DOT->drawCircle(color,width,origin)
        SceneMarkKind.LINE->drawLine(color,origin,origin+Offset(width,height),stroke,cap=StrokeCap.Round)
        SceneMarkKind.GLOW->drawCircle(Brush.radialGradient(listOf(color,Color.Transparent),center=origin,radius=width),width,origin)
        SceneMarkKind.WAVE->{
            val path=Path().apply {
                moveTo(origin.x,origin.y)
                cubicTo(origin.x+width*.3f,origin.y-height,origin.x+width*.7f,origin.y+height,origin.x+width,origin.y)
            }
            drawPath(path,color,style=Stroke(stroke,cap=StrokeCap.Round))
        }
        SceneMarkKind.LEAF->rotate(mark.rotation,origin) {
            val path=Path().apply {
                moveTo(origin.x-width/2,origin.y)
                cubicTo(origin.x-width*.2f,origin.y-height,origin.x+width*.3f,origin.y-height*.3f,origin.x+width/2,origin.y)
                cubicTo(origin.x+width*.1f,origin.y+height*.8f,origin.x-width*.3f,origin.y+height*.7f,origin.x-width/2,origin.y)
                close()
            }
            drawPath(path,color)
        }
        SceneMarkKind.BIRD->{
            val path=Path().apply {
                moveTo(origin.x,origin.y)
                quadraticBezierTo(origin.x+width*.25f,origin.y-height,origin.x+width/2,origin.y)
                quadraticBezierTo(origin.x+width*.75f,origin.y-height,origin.x+width,origin.y)
            }
            drawPath(path,color,style=Stroke(stroke,cap=StrokeCap.Round))
        }
    }
}
@Composable fun TimerGlyph(theme:String,dark:Boolean,progress:Float,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val ink=palette(theme,dark).ink
    val picture=rememberScenePicture("motif:${ThemeCards.glyph(theme)}/${if(dark)"night" else "day"}",ink,320,64)
    val measures=remember { listOf("M130 36Q72 31 18 36","M190 36Q248 31 302 36").map { android.graphics.PathMeasure(PathParser().parsePathString(it).toPath().asAndroidPath(),false) } }
    val paint=remember(ink) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink.toArgb();alpha=200;style=Paint.Style.STROKE;strokeWidth=1.1f;strokeCap=Paint.Cap.ROUND } }
    Canvas(modifier) {
        val scale=min(size.width/320,size.height/64)
        drawIntoCanvas { c -> val native=c.nativeCanvas;native.save();native.translate((size.width-320*scale)/2,(size.height-64*scale)/2);native.scale(scale,scale);picture?.let{native.drawPicture(it)}
            measures.forEach { measure ->
                val segment=android.graphics.Path()
                measure.getSegment(0f,measure.length*progress.coerceIn(0f,1f),segment,true)
                native.drawPath(segment,paint)
            };native.restore()
        }
    }
}
