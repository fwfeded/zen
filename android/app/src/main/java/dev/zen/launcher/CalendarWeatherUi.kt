package dev.zen.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun WeatherPanel(a:MainActivity,s:AppState,now:Long){
    a.permissionsVersion
    val locating by a.weatherLocation.locating.collectAsState()
    val refreshing by a.weather.refreshing.collectAsState()
    val locationStatus by a.weatherLocation.status.collectAsState()
    val needsGpsPermission by a.weatherLocation.needsGpsPermission.collectAsState()
    val automatic=s.weather.mode!="manual"
    var query by rememberSaveable{mutableStateOf("")}
    var results by remember{mutableStateOf(emptyList<City>())}
    var cityExpanded by rememberSaveable{mutableStateOf(false)}
    var searching by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var requestId by remember{mutableIntStateOf(0)}
    fun currentLocation(){a.perform({a.weather.useAutomatic()},{a.requestWeatherLocation()})}
    fun search(){
        val id=++requestId;job?.cancel();searching=true;error="";results=emptyList()
        job=scope.launch{
            try{val list=a.weather.search(query);if(id==requestId){results=list;if(list.isEmpty())error=tr(R.string.ui_27472471ca00, "未找到城市，可试试拼音或英文。")}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){if(id==requestId)error=tr(R.string.ui_6f3d85a50a3c, "暂时无法查找城市，请联网后重试。")}
            finally{if(id==requestId)searching=false}
        }
    }
    fun stamp(time:Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    PanelColumn(tr(R.string.ui_4d947325473f, "天气"),a){
        Text(cityDisplayName(s.weather.city),style=MaterialTheme.typography.titleMedium)
        if(automatic&&s.weather.city?.name in listOf("当前位置","城市待识别"))Text(tr(R.string.ui_ef8c3fed4113, "天气按定位更新；城市名称暂未识别，会自动重试。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        if(WeatherRules.usable(s.weather,now)){
            Text("${weatherNames[WeatherRules.category(s.weather.code,s.weather.wind)]} · ${s.weather.temp?.toInt()}°",fontSize=24.sp)
            if(WeatherRules.stale(s.weather,now))Text(tr(R.string.ui_7231f31eab97, "数据较旧"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        }else Text(if(locating)tr(R.string.ui_7685bfc807b0, "正在获取大致位置…")else if(refreshing)tr(R.string.ui_a72a005bae66, "正在获取天气…")else tr(R.string.ui_c508266c8711, "天气尚未更新"),fontSize=14.sp)
        if(s.weather.dataAt>0)Text(tr(R.string.ui_e68eeb97e097, "数据 %1\$s · 获取 %2\$s", stamp(s.weather.dataAt), stamp(s.weather.fetchedAt)),fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)
        if(automatic&&locationStatus.isNotBlank()&&!locating)Text(locationStatus,fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        if(s.weather.error.isNotBlank())Text(s.weather.error,fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        when {
            automatic&&!a.weatherLocation.hasPermission()->Button(onClick={currentLocation()},modifier=Modifier.fillMaxWidth().testTag("weather-location-permission")){Text(tr(R.string.ui_0fa9e71025c9, "允许大致位置"))}
            automatic&&!a.weatherLocation.isLocationEnabled()->Button(onClick={a.openLocationSettings()},modifier=Modifier.fillMaxWidth().testTag("weather-location-settings")){Text(tr(R.string.ui_6a95d918876a, "开启手机定位"))}
            else->OutlinedButton(onClick={if(automatic)a.weatherLocation.refresh(true)else a.weather.fetch(true)},enabled=!locating&&!refreshing,
                modifier=Modifier.fillMaxWidth().testTag("weather-refresh")){Text(if(locating)tr(R.string.ui_145fdf789f56, "正在定位…")else if(refreshing)tr(R.string.ui_e6442ce18f53, "正在更新…")else tr(R.string.ui_a429963064ee, "更新天气"))}
        }
        if(automatic&&needsGpsPermission&&a.weatherLocation.hasPermission()&&!a.weatherLocation.hasPrecisePermission()&&!locating){
            Text(tr(R.string.weather_gps_permission,"GPS 需要系统的精确位置授权，仅在禅打开时使用。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
            TextButton(onClick={a.requestWeatherGps()},modifier=Modifier.testTag("weather-gps-permission")){Text(tr(R.string.weather_gps_action,"尝试 GPS 定位"))}
        }
        if(!automatic)TextButton(onClick={currentLocation()},modifier=Modifier.testTag("weather-use-current")){Text(tr(R.string.ui_c789741b0dd2, "使用当前位置"))}
        TextButton(onClick={cityExpanded=!cityExpanded},modifier=Modifier.testTag("weather-manual-toggle")){Text(if(cityExpanded)tr(R.string.ui_4d91f8124c9a, "收起备用城市")else tr(R.string.ui_84548522633a, "手动选城市（备用）"))}
        if(cityExpanded){
            OutlinedTextField(query,{query=it;requestId++;job?.cancel();searching=false;results=emptyList()},Modifier.fillMaxWidth(),
                label={Text(tr(R.string.ui_1864ef8d238d, "城市名称"))},singleLine=true,keyboardActions=KeyboardActions(onDone={if(query.trim().length>=2)search()}))
            TextButton(onClick={search()},enabled=query.trim().length>=2&&!searching){Text(if(searching)tr(R.string.ui_d302f90b0996, "查询中…")else tr(R.string.ui_6400f7dc6389, "查找城市"))}
            if(error.isNotBlank())Text(error,fontSize=12.sp)
            results.forEach{city->PanelLink(city.name,city.region){
                a.perform({a.weather.select(city)},{cityExpanded=false});results=emptyList();query=""
            }}
        }
        PanelLink("Open-Meteo",tr(R.string.ui_ecbd188483f6, "约 15 分钟刷新 · 气象模型数据")){a.external(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))}
    }
}
