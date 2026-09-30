package dev.zen.launcher

internal object TodoOverlayPolicy {
    fun pending(tasks:List<Todo>,today:String)=tasks.filter{it.date==today&&!it.done}
    fun selected(tasks:List<Todo>,pinned:String)=tasks.firstOrNull{it.id==pinned}?:tasks.firstOrNull()
    fun visible(enabled:Boolean,allowed:Boolean,locked:Boolean,count:Int,hiddenUntil:Long,now:Long)=
        enabled&&allowed&&!locked&&count>0&&now>=hiddenUntil
}
