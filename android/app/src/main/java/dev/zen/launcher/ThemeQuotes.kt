package dev.zen.launcher

internal data class ThemeQuote(val text:String,val credit:String,val source:String)

/** Public-domain classical verse with source attribution. */
internal object ThemeQuotes {
    private val wander=ThemeQuote("行到水穷处，\n坐看云起时。","王维《终南别业》","https://zh.wikisource.org/wiki/終南別業")
    private val alone=ThemeQuote("兴来每独往，\n胜事空自知。","王维《终南别业》",wander.source)
    private val moon=ThemeQuote("明月松间照，\n清泉石上流。","王维《山居秋暝》","https://zh.wikisource.org/wiki/山居秋暝")
    private val river=ThemeQuote("野旷天低树，\n江清月近人。","孟浩然《宿建德江》","https://zh.wikisource.org/wiki/宿建德江")
    private val rain=ThemeQuote("山色空蒙雨亦奇。","苏轼《饮湖上初晴后雨》","https://zh.wikisource.org/wiki/飲湖上初晴後雨")
    private val sun=ThemeQuote("水光潋滟晴方好。",rain.credit,rain.source)
    private val snow=ThemeQuote("孤舟蓑笠翁，\n独钓寒江雪。","柳宗元《江雪》","https://zh.wikisource.org/wiki/江雪")
    private val snowMountains=ThemeQuote("千山鸟飞绝，\n万径人踪灭。",snow.credit,snow.source)
    fun pool(theme:String,dark:Boolean,weather:String,category:String="scene",month:Int=0):List<ThemeQuote> {
        val initial=when {
        theme !in setOf("cloud","river") -> emptyList()
        weather in setOf("snow","snowshowers") -> listOf(if(theme=="river")snow else snowMountains,wander)
        weather in setOf("rain","drizzle","showers","freezing","thunder","hail") -> listOf(rain,wander)
        dark && weather in setOf("clear","cloudy") -> listOf(if(theme=="river")river else moon,wander)
        !dark && weather=="clear" -> listOf(if(theme=="river")sun else wander,alone)
        else -> listOf(wander,alone)
        }
        if(theme !in setOf("cloud","river"))return initial
        val poems=PoetryLibrary.entries.filter{it.matches(theme,dark,weather,month)}
            .sortedBy{if(it.category==category)0 else 1}.map{it.quote}
        // Preserve the original two positions; add complete, attributed excerpts without duplicates.
        return (initial+poems).distinctBy{it.text.replace("\n","")}
    }
    fun index(saved:Int,size:Int)=if(size>0)Math.floorMod(saved,size) else 0
    fun next(saved:Int,size:Int)=if(size>0)(index(saved,size)+1)%size else 0
}
