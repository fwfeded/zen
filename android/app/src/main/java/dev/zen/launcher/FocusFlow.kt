package dev.zen.launcher

import kotlinx.serialization.Serializable

@Serializable data class FocusBookmark(val todoId:String,val nextStep:String="")
enum class TodoFocusAction{START,RESUME,VIEW;
    val label:String get()=when(this){
        START->tr(R.string.ui_033df8fb6e36,"开始专注")
        RESUME->tr(R.string.ui_d5193582287a,"继续专注")
        VIEW->tr(R.string.ui_624135e2de6a,"查看计时")
    }
}

/** Task context is independent of elapsed-time accounting and never completes a todo. */
object FocusFlow {
    fun quiet(s:AppState)=s.settings.quietFocus&&s.timer.mode=="focus"&&s.timer.status=="running"
    fun rememberedTask(s:AppState)=s.focusBookmark?.let{b->s.todos.find{it.id==b.todoId&&!it.done}}
    fun taskAction(s:AppState,id:String):TodoFocusAction {
        if(s.timer.todoId!=id)return TodoFocusAction.START
        return when(s.timer.status){
            "running"->TodoFocusAction.VIEW
            "paused"->if(s.timer.mode=="focus")TodoFocusAction.RESUME else TodoFocusAction.VIEW
            else->TodoFocusAction.START
        }
    }
    fun start(s:AppState,id:String,elapsed:Long,wall:Long,boot:Int,zone:String,durations:Pair<Int,Int>?=null):AppState {
        val task=s.todos.find{it.id==id&&!it.done}?:return s
        val settled=FocusLedger.advance(s,elapsed,wall,boot,zone)
        if(settled.timer.status in listOf("running","paused")){
            if(settled.timer.mode!="focus"||settled.timer.todoId!=id)return settled
            return if(settled.timer.status=="paused")FocusLedger.toggle(settled,elapsed,wall,boot,zone)else settled
        }
        val configured=durations?.let{DailyTools.applyRhythmSeconds(settled,it.first,it.second)}?:settled
        val fresh=FocusLedger.end(configured,elapsed,wall,boot,zone)
        val started=FocusLedger.toggle(fresh,elapsed,wall,boot,zone)
        return started.copy(timer=started.timer.copy(todoId=task.id,taskTitle=task.text),
            focusBookmark=if(s.focusBookmark?.todoId==id)s.focusBookmark else FocusBookmark(id))
    }
    fun saveNote(s:AppState,id:String,text:String):AppState {
        require(text.trim().length<=120)
        if(s.focusBookmark?.todoId!=id||s.todos.none{it.id==id&&!it.done})return s
        return s.copy(focusBookmark=FocusBookmark(id,text.trim()))
    }
}
