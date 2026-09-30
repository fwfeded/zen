package dev.zen.launcher

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal object WeatherLocationRace {
    suspend fun first(providers:List<String>,timeoutMs:Long,read:suspend (String)->WeatherCoordinates?):WeatherCoordinates? = coroutineScope {
        if(providers.isEmpty())return@coroutineScope null
        val results=Channel<WeatherCoordinates?>(Channel.UNLIMITED)
        val jobs=providers.distinct().map{provider->launch {
            val point=try{read(provider)?.takeIf(AutomaticWeatherPolicy::accepts)}
                catch(e:CancellationException){throw e}catch(_:Exception){null}
            results.send(point)
        }}
        try {
            withTimeoutOrNull(timeoutMs){
                repeat(jobs.size){results.receive()?.let{return@withTimeoutOrNull it}}
                null
            }
        }finally{jobs.forEach{it.cancel()}}
    }
}
