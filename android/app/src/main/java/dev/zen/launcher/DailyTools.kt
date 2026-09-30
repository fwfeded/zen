package dev.zen.launcher

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable data class Rhythm(val id:String=UUID.randomUUID().toString(),val focus:Int,val rest:Int,
    val focusSeconds:Int?=null,val restSeconds:Int?=null)
fun defaultRhythms()=listOf(Rhythm("short",15,5),Rhythm("standard",25,5),Rhythm("long",45,10))
@Serializable data class Todo(val id:String=UUID.randomUUID().toString(),val date:String,val text:String,val done:Boolean=false)
object DailyTools {
    fun valid(focus:Int,rest:Int)=focus in 1..1440 && rest in 1..1440
    fun validSeconds(focus:Int,rest:Int)=focus in 1..MAX_DURATION_SECONDS && rest in 1..MAX_DURATION_SECONDS
    fun applyRhythm(s:AppState,focus:Int,rest:Int):AppState {
        require(valid(focus,rest));return applyRhythmSeconds(s,focus*60,rest*60)
    }
    fun saveRhythm(s:AppState,focus:Int,rest:Int,id:String?=null):AppState {
        require(valid(focus,rest))
        return saveRhythmSeconds(s,focus*60,rest*60,id)
    }
    fun applyRhythmSeconds(s:AppState,focus:Int,rest:Int):AppState {
        require(validSeconds(focus,rest))
        return s.copy(settings=s.settings.copy(focusMinutes=legacyDurationMinutes(focus),restMinutes=legacyDurationMinutes(rest),
            focusSeconds=preciseDurationRemainder(focus),restSeconds=preciseDurationRemainder(rest)))
    }
    fun saveRhythmSeconds(s:AppState,focus:Int,rest:Int,id:String?=null):AppState {
        require(validSeconds(focus,rest))
        if(s.settings.rhythms.any{it.id!=id&&it.focusDurationSeconds==focus&&it.restDurationSeconds==rest})return s
        val next=Rhythm(id=id?:UUID.randomUUID().toString(),focus=legacyDurationMinutes(focus),rest=legacyDurationMinutes(rest),
            focusSeconds=preciseDurationRemainder(focus),restSeconds=preciseDurationRemainder(rest))
        val list=if(id==null)s.settings.rhythms+next else s.settings.rhythms.map{if(it.id==id)next else it}
        return s.copy(settings=s.settings.copy(rhythms=list))
    }
    fun applySnoozeSeconds(s:AppState,seconds:Int):AppState {
        require(seconds in 1..MAX_DURATION_SECONDS)
        return s.copy(settings=s.settings.copy(snoozeMinutes=legacyDurationMinutes(seconds),snoozeSeconds=preciseDurationRemainder(seconds)))
    }
    fun addTodo(s:AppState,date:String,text:String):AppState {
        LocalDate.parse(date);val clean=text.trim();require(clean.isNotEmpty()&&clean.length<=80)
        return s.copy(todos=s.todos+Todo(date=date,text=clean))
    }
    fun completeTodo(s:AppState,id:String,done:Boolean)=s.copy(todos=s.todos.map{if(it.id==id)it.copy(done=done)else it})
    fun moveTodo(s:AppState,id:String,today:String):AppState {
        val target=LocalDate.parse(today)
        return s.copy(todos=s.todos.map{if(it.id==id&&!it.done&&LocalDate.parse(it.date)<target)it.copy(date=today)else it})
    }
    fun periodDays(date:LocalDate,month:Boolean):List<LocalDate> {
        val first=if(month)date.withDayOfMonth(1)else date.minusDays(date.dayOfWeek.value-1L)
        return (0 until if(month)date.lengthOfMonth()else 7).map{first.plusDays(it.toLong())}
    }
    fun total(daily:Map<String,Long>,days:List<LocalDate>)=days.sumOf{daily[it.toString()]?:0L}
    fun compact(ms:Long):String {
        if(ms<=0)return "0min";if(ms<60000)return "<1min"
        val m=ms/60000;return if(m<60)"${m}min" else "${m/60}h"+if(m%60==0L)"" else "${m%60}min"
    }
}
