package dev.zen.launcher

/** Only migrate the exact defaults we shipped; explicit system choices always win. */
internal data class TimerChannelSnapshot(
    val importance:Int, val sound:String?, val vibrates:Boolean, val pattern:List<Long>?,
    val userImportance:Boolean=false, val soundChoiceKnown:Boolean=true, val userSound:Boolean=false
)
internal object TimerChannelPolicy {
    const val LEGACY="timer"
    const val SILENT="timer-completion-v2"
    const val CURRENT="timer-completion-v3"
    val pattern=listOf(0L,180L,100L,180L)
    fun select(legacy:TimerChannelSnapshot?,previous:TimerChannelSnapshot?):String {
        if(legacy!=null && (!legacy.soundChoiceKnown || legacy.userImportance || legacy.userSound ||
            legacy.importance!=3 || legacy.sound!=null || legacy.vibrates))return LEGACY
        if(previous!=null && (!previous.soundChoiceKnown || previous.userImportance || previous.userSound ||
            previous.importance!=4 || previous.sound!=null || !previous.vibrates || previous.pattern!=pattern))return SILENT
        return CURRENT
    }
}
