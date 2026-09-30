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
        HelpButton(tr(R.string.ui_127527c89c51,"语言 / Language"),tr(R.string.quote_language_help,"内置文案随语言切换，编辑按语言分别保存。主题卡原文不变。不支持的系统语言使用英文。"),compact=false)
    }
}
