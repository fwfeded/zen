package dev.zen.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WeatherClient(private val context:Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val version=AtomicInteger(0)
    private var refresh:Job?=null
    private var requestKey=""
    private var cityLookup:Job?=null
    private var cityAttemptAt=0L
    private var cityAttemptKey=""
    @Volatile private var foreground=true
    private val _refreshing=MutableStateFlow(false)
    val refreshing=_refreshing.asStateFlow()
    private val store get()=context.zen().store
    val current:Weather get()=store.state.value.weather

    private fun automaticAllowed():Boolean =
        (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)&&
        runCatching{LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))}.getOrDefault(false)

    private suspend fun request(url:String):JsonObject = suspendCancellableCoroutine { continuation->
        if(!foreground){continuation.cancel();return@suspendCancellableCoroutine}
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=8_000;connection.readTimeout=8_000
        connection.setRequestProperty("User-Agent","Zen-Android/${BuildConfig.VERSION_NAME} (personal prototype)")
        val work=scope.launch {
            try {
                if(!foreground)throw CancellationException("Weather is paused")
                check(connection.responseCode==200){tr(R.string.ui_ba53861997bf, "天气服务暂不可用")}
                val data=Json.parseToJsonElement(connection.inputStream.bufferedReader().use{it.readText()}).jsonObject
                if(continuation.isActive){if(isActive&&foreground)continuation.resume(data)else continuation.cancel()}
            }catch(e:Exception){if(continuation.isActive){if(isActive&&foreground)continuation.resumeWithException(e)else continuation.cancel()}}
            finally{connection.disconnect()}
        }
        continuation.invokeOnCancellation{work.cancel();scope.launch(NonCancellable){runCatching{connection.disconnect()}}}
        work.invokeOnCompletion{if(it is CancellationException)continuation.cancel(it)}
    }

    suspend fun search(query:String):List<City> = withContext(Dispatchers.IO) {
        if(query.trim().length<2)return@withContext emptyList()
        val data=request("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(),"UTF-8")}&count=10&language=${UiLanguage.locale().language}&format=json")
        data["results"]?.jsonArray?.map { item->val o=item.jsonObject
            City(o.getValue("id").jsonPrimitive.content,o.getValue("name").jsonPrimitive.content,
                listOfNotNull(o["admin1"]?.jsonPrimitive?.content,o["country"]?.jsonPrimitive?.content).distinct().joinToString(" · "),
                o.getValue("latitude").jsonPrimitive.double,o.getValue("longitude").jsonPrimitive.double,
                o["timezone"]?.jsonPrimitive?.content?:"auto")
        }?:emptyList()
    }

    @Synchronized private fun invalidate(){version.incrementAndGet();refresh?.cancel();refresh=null;requestKey="";_refreshing.value=false}

    /** Mutating selectors run on the application's IO queue, not in composition. */
    fun select(city:City){
        invalidate()
        if(store.update{it.copy(weather=Weather(city=city,mode="manual"))})fetch(true)
    }
    fun useAutomatic(){
        if(current.mode=="auto")return
        invalidate()
        store.update{it.copy(weather=Weather(mode="auto"))}
    }
    fun remove(){invalidate();store.update{it.copy(weather=Weather())}}

    /** A late location result cannot overwrite the user's manual-city choice. */
    fun updateLocation(point:WeatherCoordinates):Boolean {
        if(!AutomaticWeatherPolicy.accepts(point))return false
        var accepted=false
        val saved=store.update { state->
            val w=state.weather
            if(w.mode!="auto")state else {
                accepted=true
                val previous=w.city
                val next=if(previous!=null&&AutomaticWeatherPolicy.sameArea(previous,point))
                    w.copy(city=previous.copy(id="current-location"),locationAt=point.recordedAt)
                else Weather(city=City("current-location","城市待识别",tr(R.string.ui_6e58178eca63, "大致位置"),point.latitude,point.longitude),mode="auto",locationAt=point.recordedAt)
                state.copy(weather=next)
            }
        }
        if(accepted&&saved)resolveCity()
        return accepted&&saved
    }

    /** City lookup runs independently; slow or missing geocoders never hold up weather. */
    @Synchronized private fun resolveCity(){
        val city=current.city?:return
        if(!foreground||!automaticAllowed()||current.mode!="auto"||city.name !in listOf("当前位置","城市待识别"))return
        val key="${city.lat}/${city.lon}"
        val now=System.currentTimeMillis()
        if(cityLookup?.isActive==true||cityAttemptKey==key&&now-cityAttemptAt in 0..60_000)return
        cityAttemptKey=key;cityAttemptAt=now
        cityLookup=scope.launch {
            val resolved=CityNames.lookup(context,city)?:return@launch
            if(isActive&&foreground&&automaticAllowed())store.update { state->
                if(state.weather.mode=="auto"&&CityNames.sameLocation(state.weather.city,city))
                    state.copy(weather=state.weather.copy(city=resolved)) else state
            }
        }
    }

    @Synchronized fun fetch(force:Boolean=false){
        if(!foreground)return
        val w=current;val city=w.city?:return
        // Cached coordinates remain local when permission or the phone's location switch is off.
        if(w.mode=="auto"&&(w.locationAt<=0||!automaticAllowed())){invalidate();return}
        if(w.mode=="auto")resolveCity()
        val key="${w.mode}/${city.id}/${city.lat}/${city.lon}"
        if(refresh?.isActive==true&&requestKey==key)return
        val now=System.currentTimeMillis()
        if(!WeatherRules.shouldRefresh(w,now,force))return
        val token=version.incrementAndGet()
        refresh?.cancel();requestKey=key;_refreshing.value=true
        refresh=scope.launch {
            try {
                if(w.mode=="auto"&&!automaticAllowed())return@launch
                val raw=request("https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}&current=temperature_2m,weather_code,wind_speed_10m&daily=sunrise,sunset&timezone=auto&timeformat=unixtime&forecast_days=1")
                val weather=WeatherResponse.parse(raw,w,System.currentTimeMillis())
                if(isActive&&version.get()==token&&(w.mode!="auto"||automaticAllowed()))store.update{
                    if(CityNames.sameLocation(it.weather.city,city)&&it.weather.mode==w.mode)it.copy(weather=weather.copy(city=it.weather.city,locationAt=it.weather.locationAt))else it
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){
                if(isActive&&version.get()==token&&(w.mode!="auto"||automaticAllowed()))store.update{
                    if(!CityNames.sameLocation(it.weather.city,city)||it.weather.mode!=w.mode)it else {
                        val failures=(it.weather.failureCount+1).coerceAtMost(10)
                        val attemptedAt=System.currentTimeMillis()
                        it.copy(weather=it.weather.copy(error=if(it.weather.dataAt>0)tr(R.string.ui_fad245c93d98, "更新失败，保留上次数据。")else tr(R.string.ui_1c43b9c53d18, "暂时无法获取天气，请联网后重试。"),
                            lastAttemptAt=attemptedAt,nextRetryAt=attemptedAt+WeatherRules.retryDelay(failures),failureCount=failures))
                    }
                }
            }finally{if(version.get()==token)_refreshing.value=false}
        }
    }
    @Synchronized fun pause(){foreground=false;invalidate();scope.coroutineContext.cancelChildren()}
    @Synchronized fun resume(){foreground=true}
    fun close(){pause();scope.cancel()}
}
