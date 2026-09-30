package dev.zen.launcher

/** A transient navigation path. The empty path is the home screen. */
data class PanelNavigation(val path:List<String> = emptyList()) {
    val current:String get()=path.lastOrNull().orEmpty()
    fun open(panel:String):PanelNavigation {
        if(panel.isEmpty())return PanelNavigation()
        if(panel==current)return this
        val previous=path.indexOf(panel)
        return PanelNavigation(if(previous>=0)path.take(previous+1)else path+panel)
    }
    fun back()=PanelNavigation(path.dropLast(1))
    companion object {
        fun restore(path:List<String>)=path.fold(PanelNavigation()){navigation,panel->navigation.open(panel)}
    }
}
