package dev.zen.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun LanguagePanel(a:MainActivity){
    PanelColumn(tr(R.string.ui_127527c89c51, "语言 / Language"),a){
        listOf("" to tr(R.string.ui_217cfe7db1e3,"跟随系统"),"zh-CN" to tr(R.string.ui_9937e365aaf3,"简体中文"),"zh-TW" to tr(R.string.ui_1c95ab9b144e,"繁體中文"),"en" to "English").forEach{(tag,label)->
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("language-${tag.ifEmpty{"system"}}").clickable{UiLanguage.choose(a,tag)},verticalAlignment=Alignment.CenterVertically){
                Text(label,Modifier.weight(1f))
                RadioButton(selected=UiLanguage.selected(a)==tag,onClick=null)
            }
        }
        Text(tr(R.string.ui_3256feb7deb5, "暂未支持的系统语言使用英文。诗词原句和自定义内容保留原文。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
    }
}
