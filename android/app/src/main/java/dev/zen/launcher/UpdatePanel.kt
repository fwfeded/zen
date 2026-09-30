package dev.zen.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun UpdatePanel(a:MainActivity,updates:AppUpdates) {
    val state by updates.state.collectAsState()
    var configuring by rememberSaveable{mutableStateOf(false)}
    var address by rememberSaveable{mutableStateOf<String?>(null)}
    PanelColumn(tr(R.string.ui_42e40f432b6d, "版本更新"),a) {
        Text(tr(R.string.ui_46b514ed3779, "禅 %1\$s", BuildConfig.VERSION_NAME),fontFamily=ZenSerif,fontSize=22.sp)
        if(state.message.isNotEmpty())Text(state.message,fontSize=13.sp,color=MaterialTheme.colorScheme.secondary,
            modifier=Modifier.testTag("update-message"))
        val release=state.release
        if(release!=null) {
            HorizontalDivider()
            Text(tr(R.string.ui_0711e635bd8d, "新版本 %1\$s", release.versionName),fontSize=16.sp)
            if(release.notes.isNotBlank())Text(release.notes,fontSize=14.sp)
        }
        if(state.busy) {
            if(state.phase=="downloading") {
                val progress=state.totalBytes?.takeIf{it>0}?.let{(state.bytes.toFloat()/it).coerceIn(0f,1f)}
                if(progress==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Text(tr(R.string.ui_f6867cced3c2, "正在下载 · %1\$s MB", state.bytes/1024/1024),fontSize=12.sp)
            }else Text(when(state.phase){"checking"->tr(R.string.ui_6b72c3d6855c, "正在检查…");"saving"->tr(R.string.ui_6bdb4435095e, "正在保存…");else->tr(R.string.ui_282370191ffd, "正在验证安装包…")},fontSize=13.sp)
            TextButton(onClick={updates.cancel()}){Text(tr(R.string.ui_2cd0f3be8738, "取消"))}
        }else {
            if(state.canInstall)Button(onClick={updates.install()},modifier=Modifier.fillMaxWidth().testTag("install-update")){Text(tr(R.string.ui_7a6bafc9c853, "安装更新"))}
            else if(state.canDownload)Button(onClick={updates.download()},modifier=Modifier.fillMaxWidth().testTag("download-update")){Text(tr(R.string.ui_a78b542de282, "下载更新"))}
            OutlinedButton(onClick={updates.check()},enabled=!state.loadingAddress,modifier=Modifier.fillMaxWidth().testTag("check-update")){Text(tr(R.string.ui_7f68ebad19ba, "检查更新"))}
        }
        val automaticLabel=tr(R.string.update_automatic,"自动检查更新")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(automaticLabel,Modifier.weight(1f),fontSize=15.sp)
            Switch(checked=state.automatic,onCheckedChange={updates.setAutomatic(it)},enabled=!state.busy&&!state.loadingAddress,
                modifier=Modifier.testTag("automatic-update").semantics{contentDescription=automaticLabel})
        }
        Text(tr(R.string.update_automatic_detail,"返回禅时每 24 小时检查一次，设置内提示新版本。下载需点按，安装需系统确认。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
        TextButton(onClick={configuring=!configuring},enabled=!state.busy){Text(if(configuring)tr(R.string.ui_2c1be45c7eb7, "收起地址设置")else tr(R.string.ui_5abf6c1a67a8, "更新地址"))}
        if(configuring) {
            OutlinedTextField(value=address?:state.address,onValueChange={address=it},label={Text(tr(R.string.ui_7faf03104741, "HTTPS 更新清单地址"))},
                enabled=!state.busy&&!state.loadingAddress,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
                modifier=Modifier.fillMaxWidth().testTag("update-address"))
            Text(tr(R.string.ui_4c0fa396507e, "填写发布方提供的地址。留空即关闭检查入口的联网功能。"),fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
            TextButton(onClick={updates.saveAddress(address?:state.address)},enabled=!state.busy&&!state.loadingAddress){Text(tr(R.string.ui_354ff24063d5, "保存地址"))}
        }
    }
}
