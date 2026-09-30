package dev.zen.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/** One foreground approximate-location request at a time; no Google Play services dependency. */
class AutomaticWeatherLocation(context:Context,private val weather:WeatherClient,
                               private val providerNames:(()->List<String>)?=null) {
    private val context=context.applicationContext
    private val manager=this.context.getSystemService(LocationManager::class.java)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var request:Job?=null
    private var lastAttemptElapsed=0L
    private var lastSucceeded=false
    private var generation=0
    private val _status=MutableStateFlow("")
    val status=_status.asStateFlow()
    private val _locating=MutableStateFlow(false)
    val locating=_locating.asStateFlow()
    private val _needsGpsPermission=MutableStateFlow(false)
    val needsGpsPermission=_needsGpsPermission.asStateFlow()
    fun hasPrecisePermission()=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED

    fun hasPermission()=context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun isLocationEnabled()=runCatching{LocationManagerCompat.isLocationEnabled(manager)}.getOrDefault(false)

    fun refresh(force:Boolean=false){
        if(weather.current.mode!="auto"){_status.value="";weather.fetch(force);return}
        if(request?.isActive==true)return
        if(!hasPermission()){_status.value=tr(R.string.ui_453b88ad913b, "允许大致位置后，自动获取当地天气。");return}
        if(!isLocationEnabled()){_status.value=tr(R.string.ui_dda5752d703f, "手机定位未开启。");return}
        val saving=EnergyPolicy.saving(context.zen().store.state.value.settings.energySaving,
            context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode)
        val current=weather.current
        if(EnergyPolicy.reuseLocation(saving,force,current.city!=null,current.locationAt,System.currentTimeMillis())){
            weather.fetch();return
        }
        val elapsed=SystemClock.elapsedRealtime()
        val since=elapsed-lastAttemptElapsed
        if(!force&&lastAttemptElapsed>0&&since in 0L until EnergyPolicy.locationInterval(saving,lastSucceeded)){
            if(lastSucceeded)weather.fetch()
            return
        }
        lastAttemptElapsed=elapsed;lastSucceeded=false
        val token=++generation
        _locating.value=true;_needsGpsPermission.value=false;_status.value=tr(R.string.ui_7685bfc807b0, "正在获取大致位置…")
        request=scope.launch {
            var usedCache=false
            try {
                val providers=runCatching{providerNames?.invoke()?:manager.getProviders(true)}.getOrDefault(emptyList())
                val candidates=listOf("fused",LocationManager.NETWORK_PROVIDER)
                    .filter{it in providers}.toMutableList()
                if(hasPrecisePermission()&&
                    LocationManager.GPS_PROVIDER in providers)candidates.add(LocationManager.GPS_PROVIDER)
                val cached=withContext(Dispatchers.IO){
                    candidates.mapNotNull{provider->runCatching{manager.getLastKnownLocation(provider)}.getOrNull()}
                        .map(::coordinates).filter(AutomaticWeatherPolicy::accepts).minByOrNull{it.ageMs}
                }
                if(cached!=null&&weather.current.mode=="auto"){
                    usedCache=withContext(Dispatchers.IO){weather.updateLocation(cached)}
                    if(usedCache){
                        weather.fetch(force)
                        // A recent, validated system fix already identifies the local weather area.
                        // Explicit refresh can still request a new fix immediately.
                        if(!force){lastSucceeded=true;_status.value="";return@launch}
                    }
                }
                // Race providers: an unresponsive fused provider must not delay a working GPS/network fix.
                // GPS cold starts need a longer bounded window than the former six seconds.
                val found=WeatherLocationRace.first(candidates,if(hasPrecisePermission())30_000 else 12_000){provider->
                    currentLocation(provider)?.let(::coordinates)
                }
                if(!isActive||weather.current.mode!="auto")return@launch
                val fresh=found
                if(fresh!=null){
                    lastSucceeded=withContext(Dispatchers.IO){weather.updateLocation(fresh)}
                    if(lastSucceeded){_status.value="";weather.fetch(force)}
                    else _status.value=tr(R.string.ui_e6d6c7d48cc9, "位置尚未保存，请稍后重试。")
                }else if(usedCache){lastSucceeded=true;_status.value=tr(R.string.ui_2affbd9c7e04, "使用手机最近的大致位置。")}
                else {
                    _needsGpsPermission.value=!hasPrecisePermission()
                    _status.value=if(!hasPrecisePermission())tr(R.string.weather_gps_offer,"未获取到大致位置，可尝试 GPS 定位。")
                        else tr(R.string.weather_gps_timeout,"暂未获取到位置。请靠近窗边后重试，或选择备用城市。")
                }
            }catch(e:CancellationException){throw e}
            catch(_:SecurityException){_status.value=tr(R.string.ui_a571c8000938, "定位权限已关闭，可重新允许大致位置。")}
            catch(_:Exception){_status.value=tr(R.string.ui_7b8766796234, "暂未获取到位置，请稍后重试。")}
            finally{if(generation==token)_locating.value=false}
        }
    }

    private suspend fun currentLocation(provider:String):Location?=suspendCancellableCoroutine { continuation->
        val cancellation=CancellationSignal()
        continuation.invokeOnCancellation{cancellation.cancel()}
        try {
            LocationManagerCompat.getCurrentLocation(manager,provider,cancellation,context.mainExecutor){location->
                if(continuation.isActive)continuation.resume(location)
            }
        }catch(_:SecurityException){if(continuation.isActive)continuation.resume(null)}
        catch(_:IllegalArgumentException){if(continuation.isActive)continuation.resume(null)}
    }

    private fun coordinates(location:Location):WeatherCoordinates {
        val age=if(location.elapsedRealtimeNanos>0)(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000
            else System.currentTimeMillis()-location.time
        // Weather needs an area, not a precise address. Round GPS coordinates before storing/sending.
        return WeatherCoordinates(kotlin.math.round(location.latitude*100)/100,kotlin.math.round(location.longitude*100)/100,location.time,age,
            if(location.hasAccuracy())location.accuracy else Float.POSITIVE_INFINITY)
    }
    fun pause(){
        val interrupted=request?.isActive==true
        generation++;request?.cancel();request=null;_locating.value=false
        if(interrupted){lastAttemptElapsed=0;_status.value=""}
    }
    fun close(){pause();scope.cancel()}
}
