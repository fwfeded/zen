package dev.zen.launcher

internal object AutomaticUpdatePolicy {
    const val INTERVAL_MS=24L*60*60*1000
    fun due(enabled:Boolean,address:String,now:Long,lastAttempt:Long):Boolean =
        enabled&&address.isNotBlank()&&(lastAttempt<=0||now<lastAttempt||now-lastAttempt>=INTERVAL_MS)
}
