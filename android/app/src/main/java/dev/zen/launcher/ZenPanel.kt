package dev.zen.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Keep component navigation in one rendering window. Animate position, never content measurement. */
@Composable internal fun ZenPanel(a:MainActivity,motion:Boolean,paper:Color,content:@Composable ()->Unit) {
    val focus=LocalFocusManager.current
    val route=a.panel
    LaunchedEffect(route){focus.clearFocus()}
    BackHandler { a.backFromPanel() }
    Box(Modifier.fillMaxSize().testTag("panel-overlay")) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.32f))
            .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,
                onClickLabel=tr(R.string.ui_e453bb7ac8e1, "关闭面板")){a.panel=""}.semantics{contentDescription=tr(R.string.ui_e453bb7ac8e1, "关闭面板")})
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .padding(top=24.dp),contentAlignment=Alignment.BottomCenter) {
            Surface(Modifier.widthIn(max=640.dp).fillMaxWidth().semantics{isTraversalGroup=true;paneTitle=tr(R.string.ui_8a12b6acdafd, "功能面板")},
                shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=paper) {
                Column {
                    // Only the handle dismisses on drag, leaving fields and scrolling unaffected.
                    Box(Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit){
                        var distance=0f
                        detectVerticalDragGestures(onDragStart={distance=0f},onVerticalDrag={change,dy->change.consume();distance+=dy},
                            onDragEnd={if(distance>64.dp.toPx())a.panel=""})
                    },contentAlignment=Alignment.Center){
                        Box(Modifier.size(32.dp,4.dp).background(MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(2.dp)))
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=16.dp)) {
                        PanelArrival(route,motion,content)
                    }
                }
            }
        }
    }
}
