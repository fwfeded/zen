package dev.zen.launcher

/** Applies only to Zen's own inbox; never dismisses or changes other apps' system notifications. */
object NotificationSelection{
    fun include(pkg:String,category:String?,basic:Set<String>,chosen:Set<String>)=
        pkg in basic || pkg in chosen || category in setOf("call","alarm")
}
