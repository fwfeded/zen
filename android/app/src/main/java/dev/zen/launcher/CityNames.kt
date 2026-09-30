package dev.zen.launcher

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

internal object CityNames {
    fun sameLocation(a:City?,b:City)=a?.id==b.id&&a.lat==b.lat&&a.lon==b.lon
    fun fromAddress(city:City,address:Address):City? {
        // Do not expose street/house addresses: the desktop only needs a city or district.
        val name=listOf(address.locality,address.subAdminArea,address.adminArea)
            .firstOrNull{!it.isNullOrBlank()}?:return null
        return city.copy(name=name,region=address.adminArea.orEmpty())
    }
    @Suppress("DEPRECATION")
    suspend fun lookup(context:Context,city:City):City? = withContext(Dispatchers.IO) {
        try {
            if(!Geocoder.isPresent())return@withContext null
            val geocoder=Geocoder(context,UiLanguage.locale())
            val addresses=withTimeoutOrNull(8_000) {
                if(Build.VERSION.SDK_INT>=33)suspendCancellableCoroutine<List<Address>> { continuation->
                    geocoder.getFromLocation(city.lat,city.lon,1,object:Geocoder.GeocodeListener {
                        override fun onGeocode(addresses:MutableList<Address>){if(continuation.isActive)continuation.resume(addresses)}
                        override fun onError(errorMessage:String?){if(continuation.isActive)continuation.resume(emptyList())}
                    })
                } else geocoder.getFromLocation(city.lat,city.lon,1).orEmpty()
            }.orEmpty()
            addresses.firstOrNull()?.let{fromAddress(city,it)}
        }catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
}
