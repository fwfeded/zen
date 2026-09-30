package dev.zen.launcher

import kotlinx.serialization.json.*
import kotlin.math.*

data class WeatherCoordinates(val latitude:Double,val longitude:Double,val recordedAt:Long,val ageMs:Long,val accuracyMeters:Float)

object AutomaticWeatherPolicy {
    fun accepts(p:WeatherCoordinates)=p.latitude.isFinite()&&p.longitude.isFinite()&&
        p.latitude in -90.0..90.0&&p.longitude in -180.0..180.0&&p.recordedAt>0&&
        p.ageMs in 0..300_000&&p.accuracyMeters.isFinite()&&p.accuracyMeters in 0f..20_000f

    fun sameArea(city:City,p:WeatherCoordinates):Boolean {
        val lat1=Math.toRadians(city.lat);val lat2=Math.toRadians(p.latitude)
        val dlat=lat2-lat1;val dlon=Math.toRadians(p.longitude-city.lon)
        val h=sin(dlat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dlon/2).pow(2)
        return 6_371_000*2*asin(sqrt(h.coerceIn(0.0,1.0)))<=5_000
    }
}

/** Open-Meteo's current.time is the model-data time, not the time of this download. */
object WeatherResponse {
    fun parse(data:JsonElement,previous:Weather,receivedAt:Long):Weather {
        val root=data.jsonObject
        val current=root.getValue("current").jsonObject
        val daily=root.getValue("daily").jsonObject
        val dataAt=Math.multiplyExact(current.getValue("time").jsonPrimitive.long,1000)
        val temp=current.getValue("temperature_2m").jsonPrimitive.double
        val code=current.getValue("weather_code").jsonPrimitive.int
        val wind=current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull?:0.0
        require(dataAt>0&&dataAt<=receivedAt+300_000&&temp.isFinite()&&wind.isFinite())
        require(WeatherRules.category(code,wind)!="unknown")
        require(previous.dataAt==0L||dataAt>=previous.dataAt){tr(R.string.ui_f0e3941f83d6, "天气服务尚未提供更新数据")}
        return previous.copy(code=code,temp=temp,wind=wind,dataAt=dataAt,fetchedAt=receivedAt,
            sunrise=Math.multiplyExact(daily.getValue("sunrise").jsonArray.first().jsonPrimitive.long,1000),
            sunset=Math.multiplyExact(daily.getValue("sunset").jsonArray.first().jsonPrimitive.long,1000),
            zone=root["timezone"]?.jsonPrimitive?.content?:previous.city?.zone.orEmpty(),
            error="",failureCount=0,nextRetryAt=0,lastAttemptAt=receivedAt)
    }
}
