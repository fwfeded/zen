package dev.zen.launcher

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Android owns the preference on API 33+; older devices use one private preference. */
internal object UiLanguage {
    @Volatile private var application:Context?=null
    @Volatile private var cached:Pair<String,Context>?=null
    @Volatile private var currentLocale:Locale=Locale.SIMPLIFIED_CHINESE
    fun initialize(context:Context){application=context.applicationContext;refresh(context)}
    fun refresh(context:Context){currentLocale=resolveLocale(context);cached=null}
    fun selected(context:Context):String = if(Build.VERSION.SDK_INT>=33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else context.getSharedPreferences("ui-language",Context.MODE_PRIVATE).getString("tag","").orEmpty()
    fun supported(locale:Locale):String = when(locale.language){
        "zh"->if(locale.script=="Hant"||locale.country in listOf("TW","HK","MO"))"zh-TW"else"zh-CN"
        else->"en"
    }
    fun locale():Locale=currentLocale
    private fun resolveLocale(context:Context):Locale {
        val chosen=selected(context)
        val original=if(chosen.isNotEmpty())Locale.forLanguageTag(chosen.substringBefore(','))
            else if(Build.VERSION.SDK_INT>=33)context.getSystemService(LocaleManager::class.java).systemLocales[0]
            else Resources.getSystem().configuration.locales[0]
        return Locale.forLanguageTag(supported(original))
    }
    fun wrap(context:Context):Context {
        val config=Configuration(context.resources.configuration)
        config.setLocales(LocaleList(resolveLocale(context)))
        return context.createConfigurationContext(config)
    }
    fun resources():Resources? {
        val app=application?:return null
        val tag=currentLocale.toLanguageTag()
        val existing=cached
        if(existing?.first==tag)return existing.second.resources
        return wrap(app).also{cached=tag to it}.resources
    }
    fun choose(activity:MainActivity,tag:String){
        require(tag in listOf("","en","zh-CN","zh-TW"))
        if(selected(activity)==tag)return
        cached=null
        if(Build.VERSION.SDK_INT>=33)activity.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(tag)
        else {
            activity.getSharedPreferences("ui-language",Context.MODE_PRIVATE).edit().putString("tag",tag).apply()
            refresh(activity)
            activity.recreate()
        }
    }
    fun selectionLabel(context:Context):String=when(selected(context).substringBefore(',')){
        ""->tr(R.string.ui_217cfe7db1e3,"跟随系统")
        "en"->"English"
        "zh-TW","zh-Hant"->"繁體中文"
        else->"简体中文"
    }
}

/** Static resource IDs, no runtime machine translation or user-content substitution. */
internal fun tr(id:Int,fallback:String,vararg args:Any?):String {
    val format=UiLanguage.resources()?.getString(id)?:fallback
    return if(args.isEmpty())format else String.format(UiLanguage.locale(),format,*args)
}
