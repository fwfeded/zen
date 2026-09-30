package dev.zen.launcher

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.*

class StateStore(context: Context) {
    private val themeRoot = ThemeCards.initialize(context)
    private val file = AtomicFile(File(context.filesDir, "zen-state.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val error = MutableStateFlow("")
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()
    init { ThemeCards.sync(mutable.value.themeCards) }
    private fun load(): AppState {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return AppState()
        return try {
            val loaded = json.decodeFromString<AppState>(file.openRead().bufferedReader().use { it.readText() })
            require(loaded.schemaVersion == 1)
            require(loaded.timer.remainingMs >= 0 && loaded.timer.durationMs > 0)
            require(loaded.daily.values.all { it >= 0 })
            val cards=loaded.themeCards.filter{ThemeCards.available(it)}.distinctBy{it.id}.take(20)
            loaded.copy(themeCards=cards,settings=loaded.settings.copy(theme=ThemeCardFormat.migratedTheme(loaded.settings.theme,cards)))
        } catch (_: Exception) {
            runCatching { file.baseFile.copyTo(File(file.baseFile.parent, "zen-state-damaged-${System.currentTimeMillis()}.json")) }
            error.value = tr(R.string.ui_55439cd615d9, "本地记录无法读取，已保留副本并恢复安全默认值。")
            AppState()
        }
    }
    @Synchronized fun update(transform: (AppState) -> AppState): Boolean {
        val next = transform(mutable.value)
        if (next == mutable.value) return true
        var out: java.io.FileOutputStream? = null
        return try {
            out = file.startWrite()
            out.write(json.encodeToString(next).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            ThemeCards.sync(next.themeCards)
            mutable.value = next
            true
        } catch (_: Exception) {
            file.failWrite(out)
            error.value = tr(R.string.ui_3ba1789e6ed9, "记录未能保存，请检查存储空间后重试。")
            false
        }
    }
}
class ZenApp : Application() {
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){
        super.onConfigurationChanged(newConfig)
        UiLanguage.refresh(this)
        perform({platform.createChannels()})
    }
    private val operations=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    fun perform(work:()->Unit,after:()->Unit={}){
        operations.launch{
            try{work();main.post{after()}}
            catch(_:Exception){store.error.value=tr(R.string.ui_7d6e6dbffa8d, "操作未完成，请重试。已有记录保留。")}
        }
    }
    lateinit var store: StateStore
    lateinit var platform: Platform
    override fun onCreate() {
        super.onCreate()
        UiLanguage.initialize(this)
        store = StateStore(this)
        perform({ThemeCards.cleanUnused()})
        platform = Platform(this, store)
        platform.createChannels()
        platform.settleTimer()
        val boot = bootId(this)
        store.update { s -> if (s.usage.boot != -1 && s.usage.boot != boot) s.copy(
            goals = s.goals.map { it.copy(active = false) }, usage = UsageCursor(),
            monitorMessage = tr(R.string.ui_5314850768d6, "设备重启，应用目标监测已暂停。可在进行中的目标中恢复。")) else s }
    }
}
fun Context.zen() = applicationContext as ZenApp
fun bootId(context: Context): Int = AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, 0)
fun currentZone(): String = ZoneId.systemDefault().id
fun StateStore.preview(context: Context): AppState = FocusLedger.advance(state.value, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootId(context), currentZone())
