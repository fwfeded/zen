package dev.zen.launcher

const val MAX_DURATION_SECONDS=24*60*60

/** Editing units is a representation change; one second remains the stored resolution. */
data class DurationParts(val hours:Int,val minutes:Int,val seconds:Int) {
    companion object {
        fun fromSeconds(total:Int):DurationParts {
            require(total in 0..MAX_DURATION_SECONDS)
            return DurationParts(total/3600,total%3600/60,total%60)
        }
        fun toSeconds(hours:Int,minutes:Int,seconds:Int):Int? {
            if(hours !in 0..24||minutes !in 0..59||seconds !in 0..59)return null
            return (hours*3600+minutes*60+seconds).takeIf{it in 1..MAX_DURATION_SECONDS}
        }
    }
}

internal fun effectiveDurationSeconds(exact:Int?,legacyMinutes:Int,fallback:Int):Int =
    exact?.takeIf{it in 1..MAX_DURATION_SECONDS}
        ?:(legacyMinutes.toLong()*60).takeIf{it in 1L..MAX_DURATION_SECONDS.toLong()}?.toInt()
        ?:fallback

// Ceil only the compatibility field. New code always reads the exact accessors below.
internal fun legacyDurationMinutes(seconds:Int)=(seconds+59)/60
internal fun preciseDurationRemainder(seconds:Int)=seconds.takeIf{it%60!=0}

val Settings.focusDurationSeconds:Int get()=effectiveDurationSeconds(focusSeconds,focusMinutes,25*60)
val Settings.restDurationSeconds:Int get()=effectiveDurationSeconds(restSeconds,restMinutes,5*60)
val Settings.snoozeDurationSeconds:Int get()=effectiveDurationSeconds(snoozeSeconds,snoozeMinutes,5*60)
val Rhythm.focusDurationSeconds:Int get()=effectiveDurationSeconds(focusSeconds,focus,25*60)
val Rhythm.restDurationSeconds:Int get()=effectiveDurationSeconds(restSeconds,rest,5*60)

fun durationSecondsText(seconds:Int):String {
    val total=seconds.coerceAtLeast(0)
    val h=total/3600;val m=total%3600/60;val s=total%60
    return buildString {
        if(h>0)append("${h}h")
        if(m>0)append("${m}min")
        if(s>0||isEmpty())append("${s}s")
    }
}
