package dev.zen.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.*

internal val paintedThemes get() = ThemeCards.entries.map{it.id}.toSet()

/** Immutable bundled paintings. Decode off the UI thread; never decode in a draw callback. */
internal object PaintedArt {
    private val cache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized fun load(context: Context, file: String): Bitmap {
        cache.get(file)?.let { return it }
        val bitmap = context.assets.open("paintings/$file").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })) { "Invalid painting: $file" }
        }
        cache.put(file, bitmap)
        // Evicted bitmaps may still be displayed during a crossfade; do not recycle them.
        return bitmap
    }
    fun file(theme: String, dark: Boolean) = "$theme-${if(dark) "night" else "day"}.png"
}

internal fun DrawScope.drawPainting(bitmap: Bitmap, theme: String, dark: Boolean, weather: String, readingVeil:Boolean=theme=="engawa",inkVeil:Boolean=theme in setOf("cloud","river")) {
    drawIntoCanvas { canvas ->
        val scale = max(size.width / bitmap.width, size.height / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        canvas.nativeCanvas.drawBitmap(bitmap, null, RectF((size.width-w)/2,(size.height-h)/2,(size.width+w)/2,(size.height+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    if(readingVeil) {
        val veil = if(dark) Color(0xff132c32) else Color(0xfffaf7e9)
        // A soft local veil preserves the cat and painted garden outside the reading area.
        withTransform({scale(1f,.65f,pivot=Offset(size.width*.5f,size.height*.51f))}) {
            drawCircle(Brush.radialGradient(listOf(veil.copy(alpha=if(dark).64f else .86f),veil.copy(alpha=if(dark).35f else .66f),Color.Transparent),
                center=Offset(size.width*.5f,size.height*.51f),radius=size.width*.64f),size.width*.64f,Offset(size.width*.5f,size.height*.51f))
        }
        drawRect(Brush.verticalGradient(listOf(veil.copy(alpha=.32f),Color.Transparent,Color.Transparent,veil.copy(alpha=.32f))))
    }
    if(dark && inkVeil) {
        drawRect(Brush.verticalGradient(0f to Color.Transparent,.40f to Color.Transparent,
            .66f to Color(0xff101b20).copy(alpha=.32f),1f to Color(0xff101b20).copy(alpha=.46f)))
    }
    val opacity=when(weather){"fog"->.27f;"overcast","thunder","hail"->.17f;"rain","freezing","showers","drizzle"->.10f;"snow","snowshowers"->.16f;else->0f}
    if(opacity>0f) drawRect((if(dark) Color(0xff182b32) else Color(0xffe0e4df)).copy(alpha=opacity))
}

/** Cloud coordinates follow the same center-crop as the moon in the river painting. */
internal fun DrawScope.drawMoonClouds(clouds: Bitmap, painting: Bitmap, seconds: Float) {
    val scale=max(size.width/painting.width,size.height/painting.height)
    val w=painting.width*scale;val h=painting.height*scale
    val x=(size.width-w)/2;val y=(size.height-h)/2
    drawIntoCanvas { canvas ->
        val native=canvas.nativeCanvas
        native.save();native.clipRect(x,y+h*.01f,x+w,y+h*.25f)
        repeat(2){layer ->
            val phase=(1f-cos(seconds*PI.toFloat()/(if(layer==0)38f else 61f)))/2f
            val width=w*(if(layer==0)1.65f else 1.5f)
            val left=x+w*(if(layer==0)-.35f+.21f*phase else -.08f-.21f*phase)
            val top=y+h*(if(layer==0).019f-.016f*phase else .022f+.016f*phase)
            val paint=Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha=((if(layer==0).64f+.26f*phase else .16f+.14f*phase)*255).toInt()
                colorFilter=android.graphics.ColorMatrixColorFilter(floatArrayOf(.65f,0f,0f,0f,0f,0f,.65f,0f,0f,0f,0f,0f,.65f,0f,0f,0f,0f,0f,1f,0f))
            }
            native.drawBitmap(clouds,null,RectF(left,top,left+width,top+width*clouds.height/clouds.width),paint)
        }
        native.restore()
    }
}
