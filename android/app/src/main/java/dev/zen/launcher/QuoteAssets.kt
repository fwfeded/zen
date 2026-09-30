package dev.zen.launcher

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

internal object QuoteAssets {
    private val mutex=Mutex()
    private var cached:JsonObject?=null
    suspend fun load(context:Context):JsonObject=withContext(Dispatchers.IO){mutex.withLock {
        cached?:Json.parseToJsonElement(context.assets.open("copy.json").bufferedReader().use{it.readText()}).jsonObject.also{cached=it}
    }}
    fun builtins(assets:JsonObject?,theme:String,phase:String,weather:String,category:String,month:Int):List<ThemeQuote> {
        val external=ThemeCards.quotes(theme,phase,weather,category,month)
        if(!external.isNullOrEmpty())return external
        val resolved=ThemeCards.base(theme)
        val sourced=ThemeQuotes.pool(resolved,phase=="night",weather,category,month)
        return if(sourced.isNotEmpty())sourced else assets?.get("$resolved/$phase/$weather/$category")?.jsonArray?.map{ThemeQuote(it.jsonPrimitive.content,tr(R.string.ui_ae010b82c76e, "禅原创短句"),"")}.orEmpty()
    }
}
@Composable internal fun rememberQuoteAssets(context:Context):JsonObject? {
    val assets by produceState<JsonObject?>(null,context.applicationContext){value=QuoteAssets.load(context)}
    return assets
}
@Composable internal fun rememberLibraryRows(context:Context,s:AppState,theme:String,phase:String,weather:String,category:String,month:Int,includeDeleted:Boolean=false):List<LibraryQuote> {
    val assets=rememberQuoteAssets(context)
    return remember(assets,theme,phase,weather,category,month,s.quoteEdits,includeDeleted){
        QuoteLibrary.rows("$theme/$phase/$weather/$category",QuoteAssets.builtins(assets,theme,phase,weather,category,month),s.quoteEdits,includeDeleted)
    }
}
