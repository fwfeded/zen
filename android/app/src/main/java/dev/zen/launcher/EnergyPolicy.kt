package dev.zen.launcher

/** Reduce optional work; alarms and foreground usage accounting keep their own clocks. */
internal object EnergyPolicy {
    fun saving(requested:Boolean,systemSaving:Boolean)=requested||systemSaving
    fun tickDelay(running:Boolean,wall:Long):Long =
        if(running)1_000 else 60_000-Math.floorMod(wall,60_000L)
    fun locationInterval(saving:Boolean,succeeded:Boolean):Long =
        if(saving)900_000 else if(succeeded)300_000 else 60_000
    fun reuseLocation(saving:Boolean,force:Boolean,hasCity:Boolean,locationAt:Long,now:Long)=
        saving&&!force&&hasCity&&locationAt>0&&now-locationAt in 0 until 900_000L
}
