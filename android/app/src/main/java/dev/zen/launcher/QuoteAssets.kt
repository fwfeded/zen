package dev.zen.launcher

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import androidx.compose.ui.platform.LocalConfiguration

internal object QuoteAssets {
    private val mutex=Mutex()
    private val cached=mutableMapOf<String,JsonObject>()
    suspend fun load(context:Context,language:String=UiLanguage.supported(UiLanguage.locale())):JsonObject=withContext(Dispatchers.IO){mutex.withLock {
        val file=when(language){"en"->"copy-en.json";"zh-TW"->"copy-zh-TW.json";else->"copy.json"}
        cached.getOrPut(file){Json.parseToJsonElement(context.assets.open(file).bufferedReader().use{it.readText()}).jsonObject}
    }}
    fun builtins(assets:JsonObject?,theme:String,phase:String,weather:String,category:String,month:Int):List<ThemeQuote> {
        val external=ThemeCards.quotes(theme,phase,weather,category,month)
        if(!external.isNullOrEmpty())return external
        val resolved=ThemeCards.base(theme)
        val sourced=ThemeQuotes.pool(resolved,phase=="night",weather,category,month)
        return if(sourced.isNotEmpty())sourced else assets?.get("$resolved/$phase/$weather/$category")?.jsonArray?.map{ThemeQuote(it.jsonPrimitive.content,tr(R.string.ui_ae010b82c76e, "禅原创短句"),"")}.orEmpty()
    }
}
/** Keep legacy Simplified Chinese edits/positions; other built-in languages have their own edits.
 * Imported cards contain author/user content and keep their original text in every UI language. */
internal fun quoteScope(theme:String,phase:String,weather:String,category:String,language:String=UiLanguage.supported(UiLanguage.locale())):String {
    val base="$theme/$phase/$weather/$category"
    return if(language=="zh-CN"||theme.startsWith("card-"))base else "$base@$language"
}
@Composable internal fun rememberQuoteAssets(context:Context):JsonObject? {
    LocalConfiguration.current
    val language=UiLanguage.supported(UiLanguage.locale())
    val assets by produceState<Pair<String,JsonObject>?>(null,context.applicationContext,language){value=language to QuoteAssets.load(context,language)}
    return assets?.takeIf{it.first==language}?.second
}
@Composable internal fun rememberLibraryRows(context:Context,s:AppState,theme:String,phase:String,weather:String,category:String,month:Int,includeDeleted:Boolean=false):List<LibraryQuote> {
    val assets=rememberQuoteAssets(context)
    val scope=quoteScope(theme,phase,weather,category)
    return remember(assets,scope,month,s.quoteEdits,s.themeCards,includeDeleted){
        QuoteLibrary.rows(scope,QuoteAssets.builtins(assets,theme,phase,weather,category,month),s.quoteEdits,includeDeleted)
    }
}
