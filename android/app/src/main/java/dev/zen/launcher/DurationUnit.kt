package dev.zen.launcher

import java.math.BigDecimal

enum class DurationUnit(val seconds:Int) { HOURS(3600), MINUTES(60), SECONDS(1);
    val label:String get()=when(this){
        HOURS->tr(R.string.ui_7d58b6422cac,"时")
        MINUTES->tr(R.string.ui_b6a993c256ef,"分")
        SECONDS->tr(R.string.ui_9dcdc2b289b9,"秒")
    }
}

/** Changing the unit applies to the entered number; no hidden rounding or truncation. */
fun durationInputSeconds(value:String,unit:DurationUnit):Int? {
    if(!value.matches(Regex("[0-9]{1,6}(\\.[0-9]{1,6})?")))return null
    return runCatching{BigDecimal(value).multiply(BigDecimal(unit.seconds)).intValueExact()}.getOrNull()?.takeIf{it in 1..86400}
}

fun durationPairText(focus:Int,rest:Int):String = when {
    focus%3600==0&&rest%3600==0 -> tr(R.string.ui_c20c71585cb8, "%1\$s/%2\$s时", focus/3600, rest/3600)
    focus%60==0&&rest%60==0 -> tr(R.string.ui_a918325d549b, "%1\$s/%2\$s分", focus/60, rest/60)
    focus<60&&rest<60 -> tr(R.string.ui_a5d041a1aefc, "%1\$s/%2\$s秒", focus, rest)
    else -> "${durationSecondsText(focus)}/${durationSecondsText(rest)}"
}
