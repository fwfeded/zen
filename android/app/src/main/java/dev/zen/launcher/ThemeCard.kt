package dev.zen.launcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Versioned data only: no scripts, remote URLs, SVG or executable resources. */
@Serializable data class CardColors(
    val ink:String, val muted:String, val sky:String, val ground:String, val paper:String, val accent:String
)
@Serializable data class CardQuote(
    val text:String, val credit:String="", val phase:String="any", val weather:String="any", val category:String="any", val source:String="",
    val months:Set<Int> = emptySet(), val priority:Int=0
)
@Serializable data class ThemeCard(
    val version:Int=1, val name:String, val author:String, val license:String, val source:String="",
    val day:String, val night:String, val variants:Map<String,String> = emptyMap(),
    val base:String="water", val effect:String="water", val speed:Float=1f, val strength:Float=1f,
    val dayColors:CardColors?=null, val nightColors:CardColors?=null, val quotes:List<CardQuote> = emptyList(),
    val glyph:String="base", val veil:String="none", val nightOverlay:String="", val quoteMode:String="specific"
) {
    fun image(dark:Boolean,weather:String)=variants["${if(dark)"night"else"day"}/$weather"]?:if(dark)night else day
    fun quotePool(phase:String,weather:String,category:String,month:Int=0):List<CardQuote> {
        val matching=quotes.filter{(it.phase=="any"||it.phase==phase)&&(it.weather=="any"||it.weather==weather)&&(quoteMode=="ordered"||it.category=="any"||it.category==category)&&(it.months.isEmpty()||month in it.months)}
        if(quoteMode=="ordered")return matching.sortedWith(compareByDescending<CardQuote>{it.priority}.thenBy{if(it.category==category)0 else 1}).distinctBy{it.text.replace("\n","")}
        // Exact matches take precedence, then common copy. Never substitute contradictory weather copy.
        val score:(CardQuote)->Int={listOf(it.phase,it.weather,it.category).count{v->v!="any"}}
        val best=matching.maxOfOrNull(score)?:return emptyList()
        return matching.filter{score(it)==best}.distinctBy{it.text}
    }
}
@Serializable data class InstalledTheme(val id:String,val card:ThemeCard)

internal object ThemeCardFormat {
    val builtins=setOf("water","forest","dawn","dusk","paper","night")
    val bases=setOf("water","forest","dawn","dusk","paper","night","cloud","river")
    val weather=setOf("clear","cloudy","overcast","fog","drizzle","rain","freezing","snow","showers","snowshowers","thunder","hail","wind","unknown")
    const val MAX_ARCHIVE=20*1024*1024
    const val MAX_EXPANDED=40*1024*1024
    const val MAX_IMAGE=8*1024*1024
    private val imageName=Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}\\.(png|jpg|jpeg|webp)")
    fun validate(c:ThemeCard) {
        require(c.version in 1..3){"version"}
        require(c.version>=3||(c.nightOverlay.isEmpty()&&c.veil!="ink"&&c.quoteMode=="specific"&&c.quotes.all{it.months.isEmpty()&&it.priority==0})){"version"}
        require(c.version>=2||(c.glyph=="base"&&c.veil=="none"&&c.effect!="fireflies"&&c.quotes.all{it.source.isEmpty()})){"version"}
        require(c.name.isNotBlank()&&c.name.length<=32&&c.author.isNotBlank()&&c.author.length<=80){"metadata"}
        require(c.license.isNotBlank()&&c.license.length<=500&&c.source.length<=500){"metadata"}
        require(c.base in bases&&c.effect in bases+setOf("none","fireflies")){"effect"}
        require(c.glyph in setOf("base","wind-chime")&&c.veil in setOf("none","reading","ink")){"appearance"}
        require(c.nightOverlay.isEmpty()||imageName.matches(c.nightOverlay)){"overlay"}
        require(c.quoteMode in setOf("specific","ordered")){"quotes"}
        require(c.speed.isFinite()&&c.speed in .25f..2f&&c.strength.isFinite()&&c.strength in 0f..1f){"effect"}
        require(imageName.matches(c.day)&&imageName.matches(c.night)){"image"}
        require(c.variants.size<=28&&c.variants.all{(key,value)->key.split('/').let{it.size==2&&it[0] in setOf("day","night")&&it[1] in weather}&&imageName.matches(value)}){"variants"}
        listOfNotNull(c.dayColors,c.nightColors).forEach{p->require(listOf(p.ink,p.muted,p.sky,p.ground,p.paper,p.accent).all{Regex("#[0-9a-fA-F]{6}").matches(it)}){"colors"}}
        require(c.quotes.size<=500){"quotes"}
        require(c.quotes.all{it.months.all{m->m in 1..12}&&it.priority in 0..100}){"quotes"}
        require(c.quotes.all{it.text.isNotBlank()&&it.text.length<=160&&it.credit.length<=160&&it.phase in setOf("any","day","night")&&it.weather in weather+"any"&&it.category in setOf("any","scene","focus","rest")}){"quotes"}
        require(c.quotes.all{q->q.source.isEmpty()||(q.source.length<=500&&runCatching{java.net.URI(q.source).let{it.scheme=="https"&&!it.host.isNullOrBlank()&&it.userInfo==null}}.getOrDefault(false))}){"source"}
    }
    /** Bounded reads, including archives with forged/missing size headers. Paths are never extracted. */
    fun read(input:InputStream):Pair<ThemeCard,Map<String,ByteArray>> {
        val compressed=object:java.io.FilterInputStream(input){
            var total=0L
            override fun read():Int=super.read().also{if(it>=0){total++;require(total<=MAX_ARCHIVE){"size"}}}
            override fun read(b:ByteArray,off:Int,len:Int):Int=`in`.read(b,off,len).also{if(it>0){total+=it;require(total<=MAX_ARCHIVE){"size"}}}
        }
        val files=linkedMapOf<String,ByteArray>();val names=mutableSetOf<String>();var total=0L
        ZipInputStream(compressed).use{zip->
            while(true){
                val entry=zip.nextEntry?:break
                require(files.size<32&&!entry.isDirectory&&(entry.name=="theme.json"||imageName.matches(entry.name))){"entry"}
                require(names.add(entry.name.lowercase(java.util.Locale.ROOT))){"duplicate"}
                val limit=if(entry.name=="theme.json")256*1024 else MAX_IMAGE
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=zip.read(buffer);if(n<0)break;total+=n;require(total<=MAX_EXPANDED&&out.size()+n<=limit){"size"};out.write(buffer,0,n)}
                files[entry.name]=out.toByteArray();zip.closeEntry()
            }
        }
        val manifest=files.remove("theme.json")?:error("manifest")
        val card=Json.decodeFromString<ThemeCard>(manifest.toString(Charsets.UTF_8));validate(card)
        val references=(listOf(card.day,card.night)+card.variants.values+listOf(card.nightOverlay).filter{it.isNotEmpty()}).toSet()
        require(files.keys==references){"images"}
        return card to files
    }
    fun migratedTheme(selected:String,cards:List<InstalledTheme>) = selected.takeIf{it in builtins||cards.any{c->c.id==it}}?:"water"
}
