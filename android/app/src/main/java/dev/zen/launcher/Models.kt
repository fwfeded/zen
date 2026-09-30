package dev.zen.launcher

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Serializable data class Settings(
    val theme: String = "water", val phase: String = "auto", val motion: Boolean = true,
    val category: String = "scene", val favorites: List<String> = emptyList(),
    val reminderPackages: Set<String> = emptySet(), val focusMinutes: Int = 25,
    val restMinutes: Int = 5, val snoozeMinutes: Int = 5, val vibration: Boolean = false,
    val priorityPackages:Set<String> = emptySet(), val rhythms:List<Rhythm> = defaultRhythms(),
    val focusSeconds:Int? = null, val restSeconds:Int? = null, val snoozeSeconds:Int? = null,
    val quietFocus:Boolean=true, val quietHintSeen:Boolean=false,
    val collapseHomeApps:Boolean=false, val energySaving:Boolean=false,
    val homeIconDp:Int=36,
    val todoOverlay:Boolean=false, val overlayPinnedId:String=""
)
@Serializable data class Goal(
    val id: String = UUID.randomUUID().toString(), val pkg: String, val component: String,
    val label: String, val text: String, val usedMs: Long = 0, val thresholdMs: Long = 600_000,
    val active: Boolean = true, val notified: Boolean = false, val startedAt: Long = System.currentTimeMillis()
)
@Serializable data class Timer(
    val mode: String = "focus", val status: String = "idle", val durationMs: Long = 1_500_000,
    val remainingMs: Long = 1_500_000, val deadlineMs: Long = 0,
    val cursorMs: Long = 0, val anchorElapsedMs: Long = 0, val anchorWallMs: Long = 0,
    val zone: String = "Asia/Shanghai", val boot: Int = -1,
    val generation: String = UUID.randomUUID().toString(), val session: String = UUID.randomUUID().toString(),
    val creditedMs: Long = 0, val completionNotified: Boolean = false,
    val completionAcknowledged: Boolean = false,
    val todoId:String?=null, val taskTitle:String=""
)
@Serializable data class UsageCursor(
    val wallMs: Long = 0, val boot: Int = -1, val pkg: String = "", val component: String = "",
    val unlocked: Boolean = true, val offsetMs: Long = 0, val screenOn:Boolean=true, val keyguardLocked:Boolean=false
)
@Serializable data class City(val id: String, val name: String, val region: String, val lat: Double, val lon: Double, val zone: String = "auto")
fun cityDisplayName(city:City?):String = when(city?.name){
    null -> tr(R.string.ui_b40f82791b7c, "所在地天气")
    "当前位置","城市待识别","" -> tr(R.string.city_pending,"城市待识别")
    else -> city.name
}
@Serializable data class Weather(
    val city: City? = null, val code: Int = -1, val temp: Double? = null, val wind: Double = 0.0,
    val fetchedAt: Long = 0, val sunrise: Long = 0, val sunset: Long = 0,
    val error: String = "", val zone: String = "",
    val mode:String = if(city==null)"auto"else"manual", val dataAt:Long = 0, val locationAt:Long = 0,
    val lastAttemptAt:Long = 0, val nextRetryAt:Long = 0, val failureCount:Int = 0
)
@Serializable data class AppState(
    val schemaVersion: Int = 1, val settings: Settings = Settings(), val goals: List<Goal> = emptyList(),
    val usage: UsageCursor = UsageCursor(), val monitorMessage: String = "",
    val timer: Timer = Timer(), val daily: Map<String, Long> = emptyMap(), val recoveryNote: String = "",
    val weather: Weather = Weather(), val copyIndices: Map<String, Int> = emptyMap(), val todos:List<Todo> = emptyList(),
    val focusBookmark:FocusBookmark?=null, val quoteEdits:List<QuoteEdit> = emptyList(),
    val themeCards:List<InstalledTheme> = emptyList()
)

/** Immutable accounting. Elapsed time determines duration; civil time only determines the day. */
object FocusLedger {
    fun split(startWall: Long, duration: Long, zone: String): Map<String, Long> {
        require(duration >= 0)
        val result = mutableMapOf<String, Long>()
        val tz = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))
        var at = startWall
        val end = Math.addExact(startWall, duration)
        while (at < end) {
            val date = Instant.ofEpochMilli(at).atZone(tz).toLocalDate()
            val boundary = date.plusDays(1).atStartOfDay(tz).toInstant().toEpochMilli()
            val next = minOf(end, boundary)
            require(next > at)
            result[date.toString()] = (result[date.toString()] ?: 0) + next - at
            at = next
        }
        return result
    }

    fun advance(s: AppState, elapsed: Long, wall: Long, boot: Int, zone: String): AppState {
        val t = s.timer
        if (t.status != "running") return s
        if (t.boot != boot || elapsed < t.cursorMs) {
            val expected = t.anchorWallMs + (t.deadlineMs - t.anchorElapsedMs)
            val remaining = (expected - wall).coerceIn(0, t.remainingMs)
            return s.copy(timer = t.copy(status = if (remaining == 0L) "complete" else "paused",
                remainingMs = remaining, boot = boot, generation = UUID.randomUUID().toString(),
                completionNotified = true), recoveryNote = tr(R.string.ui_773b73127aaf, "设备重启，本轮已暂停或结束。重启前部分时长未能确认，未补记。"))
        }
        val until = minOf(elapsed, t.deadlineMs).coerceAtLeast(t.cursorMs)
        val delta = (until - t.cursorMs).coerceAtMost((t.durationMs - t.creditedMs).coerceAtLeast(0))
        val daily = s.daily.toMutableMap()
        if (t.mode == "focus" && delta > 0) {
            split(t.anchorWallMs + t.cursorMs - t.anchorElapsedMs, delta, t.zone).forEach { (day, ms) ->
                daily[day] = (daily[day] ?: 0) + ms
            }
        }
        val remaining = (t.deadlineMs - elapsed).coerceAtLeast(0)
        val changedClock = kotlin.math.abs((wall - elapsed) - (t.anchorWallMs - t.anchorElapsedMs)) > 2000 || zone != t.zone
        return s.copy(daily = daily, timer = t.copy(
            remainingMs = remaining, status = if (remaining == 0L) "complete" else "running",
            cursorMs = until, creditedMs = t.creditedMs + delta,
            anchorElapsedMs = if (changedClock) elapsed else t.anchorElapsedMs,
            anchorWallMs = if (changedClock) wall else t.anchorWallMs,
            zone = zone
        ), recoveryNote = if (changedClock) tr(R.string.ui_4cd32eac0c21, "设备时间或时区已变化，已有日期记录保留；未结算部分按原时间归属。") else s.recoveryNote)
    }

    fun toggle(s: AppState, elapsed: Long, wall: Long, boot: Int, zone: String): AppState {
        val settled = advance(s, elapsed, wall, boot, zone)
        val t = settled.timer
        if (t.status == "running") return settled.copy(timer = t.copy(status = "paused", generation = UUID.randomUUID().toString()))
        val mode = if (t.status == "complete") (if (t.mode == "focus") "rest" else "focus") else t.mode
        val fresh = t.status == "idle" || t.status == "complete"
        val duration = if (fresh) (if (mode == "focus") s.settings.focusDurationSeconds else s.settings.restDurationSeconds) * 1_000L else t.durationMs
        val remaining = if (fresh) duration else t.remainingMs
        return settled.copy(timer = t.copy(mode = mode, status = "running", durationMs = duration,
            remainingMs = remaining, deadlineMs = elapsed + remaining, cursorMs = elapsed,
            anchorElapsedMs = elapsed, anchorWallMs = wall, zone = zone, boot = boot,
            generation = UUID.randomUUID().toString(), session = if (fresh) UUID.randomUUID().toString() else t.session,
            creditedMs = if (fresh) 0 else t.creditedMs, completionNotified = false, completionAcknowledged = false))
    }
    fun end(s: AppState, elapsed: Long, wall: Long, boot: Int, zone: String): AppState =
        advance(s, elapsed, wall, boot, zone).copy(timer = Timer(durationMs = s.settings.focusDurationSeconds * 1_000L, remainingMs = s.settings.focusDurationSeconds * 1_000L))
    fun clear(s: AppState, elapsed: Long, wall: Long, boot: Int, zone: String): AppState =
        advance(s, elapsed, wall, boot, zone).copy(daily = emptyMap(), recoveryNote = "")
}

data class UsageEvent(val time: Long, val kind: String, val pkg: String = "", val component: String = "")
data class UsageSlice(val pkg:String,val start:Long,val end:Long)
data class UsageResult(val cursor: UsageCursor, val totals: Map<String, Long>,val slices:List<UsageSlice> = emptyList())
object UsageAccounting {
    fun consume(cursor: UsageCursor, events: List<UsageEvent>, until: Long): UsageResult {
        require(until >= cursor.wallMs)
        var c = cursor
        val totals = mutableMapOf<String, Long>()
        val slices = mutableListOf<UsageSlice>()
        fun advance(to: Long) {
            if (c.unlocked && c.pkg.isNotEmpty() && to > c.wallMs) {
                totals[c.pkg] = (totals[c.pkg] ?: 0) + to - c.wallMs
                slices.add(UsageSlice(c.pkg,c.wallMs,to))
            }
            c = c.copy(wallMs = to)
        }
        events.distinct().filter { it.time >= cursor.wallMs && it.time < until }.sortedBy { it.time }.forEach { e ->
            advance(e.time)
            c = when (e.kind) {
                "resume" -> c.copy(pkg = e.pkg, component = e.component)
                "pause" -> if (c.pkg == e.pkg && c.component == e.component) c.copy(pkg = "", component = "") else c
                "lock" -> c.copy(unlocked = false, keyguardLocked=true, pkg = "", component = "")
                "unlock" -> c.copy(unlocked = c.screenOn,keyguardLocked=false)
                "screen-off" -> c.copy(unlocked=false,screenOn=false,pkg="",component="")
                "screen-on" -> c.copy(screenOn=true,unlocked=!c.keyguardLocked)
                else -> c
            }
        }
        advance(until)
        return UsageResult(c, totals,slices)
    }
    fun apply(s: AppState, r: UsageResult): AppState = s.copy(usage = r.cursor, goals = s.goals.map { g ->
        if (g.active) g.copy(usedMs = g.usedMs + r.slices.filter{it.pkg==g.pkg}.sumOf{(it.end-maxOf(it.start,g.startedAt)).coerceAtLeast(0)}) else g
    })
}

object WeatherRules {
    fun category(code: Int, wind: Double = 0.0): String = when {
        code in 0..3 && wind >= 35 -> "wind"
        code in 0..1 -> "clear"
        code == 2 -> "cloudy"
        code == 3 -> "overcast"
        code in listOf(45, 48) -> "fog"
        code in listOf(51, 53, 55) -> "drizzle"
        code in listOf(56, 57, 66, 67) -> "freezing"
        code in listOf(61, 63, 65) -> "rain"
        code in listOf(71, 73, 75, 77) -> "snow"
        code in listOf(80, 81, 82) -> "showers"
        code in listOf(85, 86) -> "snowshowers"
        code == 95 -> "thunder"
        code in listOf(96, 99) -> "hail"
        else -> "unknown"
    }
    fun usable(w: Weather, now: Long) = w.dataAt>0&&now-w.dataAt in -300_000..86_400_000&&
        w.temp?.isFinite()==true&&category(w.code,w.wind)!="unknown"
    fun stale(w: Weather, now: Long) = w.dataAt<=0||now-w.dataAt>1_800_000
    fun retryDelay(failures:Int)=minOf(900_000L,30_000L shl (failures-1).coerceIn(0,5))
    fun shouldRefresh(w:Weather,now:Long,force:Boolean=false):Boolean {
        if(force)return true
        if(now>=w.lastAttemptAt&&now<w.nextRetryAt)return false
        return w.fetchedAt<=0||w.dataAt<=0||now<w.fetchedAt||now-w.fetchedAt>=900_000
    }
}
fun durationText(ms: Long): String {
    if (ms <= 0) return tr(R.string.ui_46ca86da52b3, "暂无记录")
    if (ms < 60_000) return tr(R.string.ui_05f038ebd35a, "少于 1 分钟")
    val minutes = ms / 60_000
    return if (minutes < 60) tr(R.string.ui_3892529bc623, "%1\$s 分钟", minutes) else tr(R.string.ui_681cd5947310, "%1\$s 小时 %2\$s 分钟", minutes / 60, (minutes % 60).toString().padStart(2, '0'))
}
