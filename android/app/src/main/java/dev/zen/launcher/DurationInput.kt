@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.zen.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Stable
class DurationDraft(value: String, unit: DurationUnit) {
    var value by mutableStateOf(value)
    var unit by mutableStateOf(unit)
    val totalSeconds: Int? get() = durationInputSeconds(value,unit)
    fun set(total: Int) {
        unit=DurationUnit.entries.firstOrNull{total%it.seconds==0}?:DurationUnit.SECONDS
        value=(total/unit.seconds).toString()
    }
}

private val DurationDraftSaver = listSaver<DurationDraft, String>(
    save = { listOf(it.value, it.unit.name) },
    restore = { DurationDraft(it[0],DurationUnit.valueOf(it[1])) }
)
@Composable fun rememberDurationDraft(initialSeconds: Int): DurationDraft = rememberSaveable(saver = DurationDraftSaver) {
    DurationDraft("",DurationUnit.MINUTES).apply{set(initialSeconds)}
}

@Composable fun DurationInput(label: String, prefix: String, draft: DurationDraft, onChange: () -> Unit = {}) {
    var expanded by remember{mutableStateOf(false)}
    OutlinedTextField(draft.value, { next ->
        if(next.length<=12&&next.all{it in '0'..'9'||it=='.'}){draft.value=next;onChange()}
    }, Modifier.fillMaxWidth().testTag("$prefix-value"),label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(16.dp),
        trailingIcon={Box{
            TextButton(onClick={expanded=true},modifier=Modifier.testTag("$prefix-unit").semantics{contentDescription=tr(R.string.ui_9df7e8cdd0bf, "%1\$s 时间单位：%2\$s，点击切换", label, draft.unit.label)}){Text("${draft.unit.label} ▾")}
            DropdownMenu(expanded,onDismissRequest={expanded=false}){
                DurationUnit.entries.forEach{unit->DropdownMenuItem(text={Text(unit.label)},onClick={draft.unit=unit;expanded=false;onChange()},modifier=Modifier.testTag("$prefix-unit-${unit.name.lowercase()}"))}
            }
        }})
}

/** Two equally weighted controls form one compact row, with independent editable units. */
@Composable fun DurationPairInput(focus:DurationDraft,rest:DurationDraft,onChange:()->Unit={}) {
    val restFocus=remember{FocusRequester()}
    val focusManager=LocalFocusManager.current
    Row(Modifier.fillMaxWidth().testTag("duration-pair"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
        DurationCapsule(tr(R.string.ui_41d42ade41e8, "专注"),"focus",focus,Modifier.weight(1f),imeAction=ImeAction.Next,
            keyboardActions=KeyboardActions(onNext={restFocus.requestFocus()}),onChange=onChange)
        DurationCapsule(tr(R.string.ui_fff33732fc97, "休息"),"rest",rest,Modifier.weight(1f),inputModifier=Modifier.focusRequester(restFocus),
            imeAction=ImeAction.Done,keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),onChange=onChange)
    }
}

@Composable private fun DurationCapsule(
    label:String,prefix:String,draft:DurationDraft,modifier:Modifier,
    inputModifier:Modifier=Modifier,imeAction:ImeAction,keyboardActions:KeyboardActions,onChange:()->Unit
) {
    var expanded by remember{mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    Surface(modifier.testTag("$prefix-capsule"),shape=RoundedCornerShape(32.dp),color=colors.primary.copy(alpha=.085f)){
        Row(Modifier.heightIn(min=60.dp).padding(start=10.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
            Text(label,fontSize=12.sp,color=colors.secondary,maxLines=1)
            BasicTextField(draft.value,{next->
                if(next.length<=12&&next.all{it in '0'..'9'||it=='.'}){draft.value=next;onChange()}
            },modifier=inputModifier.weight(1f).heightIn(min=48.dp).testTag("$prefix-value").semantics{contentDescription=tr(R.string.ui_3fbd0a103c8c, "%1\$s 时长", label)},
                singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=20.sp,color=colors.onSurface,textAlign=TextAlign.Center),
                cursorBrush=SolidColor(colors.primary),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=imeAction),keyboardActions=keyboardActions,
                decorationBox={inner->Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center){inner()}})
            Box{
                TextButton(onClick={expanded=true},contentPadding=PaddingValues(0.dp),
                    modifier=Modifier.size(48.dp).testTag("$prefix-unit").semantics{contentDescription=tr(R.string.ui_9df7e8cdd0bf, "%1\$s 时间单位：%2\$s，点击切换", label, draft.unit.label)}){
                    Text("${draft.unit.label} ▾",fontSize=12.sp,maxLines=1)
                }
                DropdownMenu(expanded,onDismissRequest={expanded=false}){
                    DurationUnit.entries.forEach{unit->DropdownMenuItem(text={Text(unit.label)},onClick={draft.unit=unit;expanded=false;onChange()},modifier=Modifier.testTag("$prefix-unit-${unit.name.lowercase()}"))}
                }
            }
        }
    }
}

fun countdownText(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val tail = "${(safe / 60 % 60).toString().padStart(2, '0')}:${(safe % 60).toString().padStart(2, '0')}"
    return if(safe >= 3600) "${safe / 3600}:$tail" else tail
}

@Composable fun DurationFeedback(text:String,revision:Int){
    if(text.isEmpty())return
    val requester=remember{BringIntoViewRequester()}
    Text(text,Modifier.bringIntoViewRequester(requester).semantics{liveRegion=LiveRegionMode.Polite},
        fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
    LaunchedEffect(revision){withFrameNanos{};requester.bringIntoView()}
}
