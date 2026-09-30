package dev.zen.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.InputStream
import java.util.UUID

internal object ThemeCards {
    @Volatile var entries:List<InstalledTheme> = emptyList();private set
    private lateinit var root:File
    private val cache=object:android.util.LruCache<String,Bitmap>(24*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    fun initialize(context:Context){root=File(context.filesDir,"theme-cards").apply{mkdirs()}}
    fun sync(cards:List<InstalledTheme>){entries=cards}
    fun find(id:String)=entries.find{it.id==id}
    fun base(id:String)=find(id)?.card?.base?:id.takeIf{it in ThemeCardFormat.bases}?:"water"
    fun glyph(id:String)=find(id)?.card?.glyph?.takeUnless{it=="base"}?:base(id)
    private fun folder(id:String):File {
        require(Regex("card-[a-f0-9-]{36}").matches(id))
        return File(root,id)
    }
    fun available(theme:InstalledTheme)=runCatching{
        ThemeCardFormat.validate(theme.card)
        (listOf(theme.card.day,theme.card.night)+theme.card.variants.values+listOf(theme.card.nightOverlay).filter{it.isNotEmpty()}).all{File(folder(theme.id),it).isFile}
    }.getOrDefault(false)
    @Synchronized fun stage(input:InputStream):InstalledTheme {
        val (card,images)=ThemeCardFormat.read(input)
        // Reimporting the identical card reuses its ID, including associated quote edits.
        // Compare both manifest and bytes: a same-named card with new artwork remains distinct.
        entries.firstOrNull{entry->entry.card==card && runCatching{
            images.all{(name,bytes)->val file=File(folder(entry.id),name);file.length()==bytes.size.toLong()&&file.readBytes().contentEquals(bytes)}
        }.getOrDefault(false)}?.let{return it}
        check(entries.size<20){"limit"}
        // Inspect and decode each image before any directory is committed. A valid extension is insufficient.
        images.values.forEach{bytes->
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth in 1..4096&&bounds.outHeight in 1..4096&&bounds.outWidth.toLong()*bounds.outHeight<=4_000_000){"dimensions"}
            require(bounds.outMimeType in setOf("image/png","image/jpeg","image/webp")){"image"}
            val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("image")
            decoded.recycle()
        }
        val installed=InstalledTheme("card-${UUID.randomUUID()}",card);val dir=folder(installed.id)
        check(dir.mkdirs())
        try{images.forEach{(name,bytes)->File(dir,name).writeBytes(bytes)}}catch(e:Exception){dir.deleteRecursively();throw e}
        return installed
    }
    @Synchronized fun bitmap(theme:InstalledTheme,dark:Boolean,weather:String):Bitmap {
        return imageBitmap(theme,theme.card.image(dark,weather))
    }
    @Synchronized fun overlay(theme:InstalledTheme):Bitmap?=theme.card.nightOverlay.takeIf{it.isNotEmpty()}?.let{imageBitmap(theme,it)}
    private fun imageBitmap(theme:InstalledTheme,file:String):Bitmap {
        val key="${theme.id}/$file"
        cache.get(key)?.let{return it}
        val result=BitmapFactory.decodeFile(File(folder(theme.id),file).path)?:error("image")
        cache.put(key,result);return result
    }
    fun colors(id:String,dark:Boolean):Palette?=find(id)?.card?.let{card->
        val p=(if(dark)card.nightColors else card.dayColors)?:return@let palette(card.base,dark)
        fun c(s:String)=Color(android.graphics.Color.parseColor(s))
        Palette(c(p.ink),c(p.muted),c(p.sky),c(p.ground),c(p.paper),c(p.accent))
    }
    fun quotes(id:String,phase:String,weather:String,category:String,month:Int=0):List<ThemeQuote>?=find(id)?.card?.let{card->
        card.quotePool(phase,weather,category,month).map{ThemeQuote(it.text,it.credit.ifBlank{card.author},it.source)}
    }
    /** Only unreferenced app-owned card directories are removed; user-selected source files are untouched. */
    @Synchronized fun discard(id:String){if(entries.none{it.id==id}){folder(id).deleteRecursively();cache.evictAll()}}
    @Synchronized fun cleanUnused(){root.listFiles()?.filter{it.isDirectory&&Regex("card-[a-f0-9-]{36}").matches(it.name)&&entries.none{c->c.id==it.name}}?.forEach{it.deleteRecursively()};cache.evictAll()}
}
