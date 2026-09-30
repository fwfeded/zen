package dev.zen.launcher

object NotificationPermissionFlow {
    fun shouldRequestRuntime(sdk:Int,granted:Boolean,requestedBefore:Boolean,canExplain:Boolean):Boolean =
        sdk>=33&&!granted&&(!requestedBefore||canExplain)
    fun offerSettingsAfterResult(granted:Boolean,canExplain:Boolean)=!granted&&!canExplain
}
