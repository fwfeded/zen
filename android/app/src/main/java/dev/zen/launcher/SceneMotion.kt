package dev.zen.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Geometry is in fractions of the displayed scene, independent of Android drawing APIs. */
internal enum class SceneMarkKind { RING, DOT, LINE, GLOW, WAVE, LEAF, BIRD, MIST, INK, GLINT }
internal enum class SceneTone { ACCENT, INK, MUTED, LIGHT }
internal data class SceneMark(
    val kind: SceneMarkKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
    val tone: SceneTone = SceneTone.ACCENT,
    val stroke: Float = 1.5f,
    val rotation: Float = 0f,
)

/** Screen-normalized touch position; callers observe taps without consuming their button events. */
data class SceneInteraction(
    val xFraction: Float,
    val yFraction: Float,
    val createdAtNanos: Long = System.nanoTime(),
)

/** A paused/background scene retains its phase and never catches up the inactive wall-clock time. */
internal data class SceneMotionClock(val seconds: Double = 0.0, val lastNanos: Long? = null) {
    fun advance(nowNanos: Long, enabled: Boolean): SceneMotionClock {
        if (!enabled) return copy(lastNanos = null)
        val delta = lastNanos?.let { ((nowNanos - it) / 1_000_000_000.0).coerceIn(0.0, .12) } ?: 0.0
        return SceneMotionClock(seconds + delta, nowNanos)
    }
}

/** The same frame geometry is used by Landscape and by host regression tests. */
internal object SceneMotion {
    fun isEnabled(active: Boolean, requested: Boolean, powerSaving: Boolean, systemAnimatorsEnabled: Boolean) =
        active && requested && !powerSaving && systemAnimatorsEnabled

    private fun wrap(value: Float): Float = ((value % 1f) + 1f) % 1f
    private fun pulse(seconds: Float, speed: Float, offset: Float = 0f) = .5f + .5f * sin(seconds * speed + offset)

    fun frame(
        theme: String,
        dark: Boolean,
        weather: String,
        seconds: Float,
        interaction: SceneInteraction? = null,
        interactionAgeSeconds: Float = Float.POSITIVE_INFINITY,
    ): List<SceneMark> = buildList {
        // Bounded deterministic geometry keeps composition and asset parsing out of the draw loop.
        val t = seconds.takeIf { it.isFinite() && it >= 0 } ?: 0f
        val wet = weather in setOf("rain", "drizzle", "freezing", "showers", "thunder", "hail")
        when (theme) {
            "cloud" -> repeat(3) { i ->
                add(SceneMark(SceneMarkKind.MIST,-.15f+i*.29f+.07f*sin(t*.32f+i),
                    .72f+i*.055f,.62f,.035f,.20f,if(dark)SceneTone.LIGHT else SceneTone.MUTED))
            }
            "river" -> repeat(4) { i ->
                add(SceneMark(SceneMarkKind.WAVE,.10f+i*.19f+.045f*sin(t*.5f+i),
                    .76f+i*.047f,.16f+.018f*sin(t*.7f+i),.003f,.24f,
                    if(dark)SceneTone.LIGHT else SceneTone.MUTED,stroke=1.2f))
            }
            "engawa", "fireflies" -> repeat(if(dark)6 else 4) { i ->
                val x=(if(i%2==0).12f else .86f)+.035f*sin(t*.6f+i)
                val y=.55f+i*.029f+.017f*cos(t*.5f+i)
                val light=.25f+.21f*pulse(t,1.1f,i.toFloat())
                if(dark)add(SceneMark(SceneMarkKind.GLOW,x,y,.02f,0f,light*.5f,SceneTone.LIGHT))
                add(SceneMark(SceneMarkKind.DOT,x,y,if(dark).0035f else .0028f,0f,light,SceneTone.LIGHT))
            }
            "water" -> repeat(3) { i ->
                val cycle = wrap(t / 9f + i / 3f)
                val width = .10f + cycle * .43f
                add(SceneMark(SceneMarkKind.RING, .25f + i * .23f - width / 2,
                    .815f + i * .045f - cycle * .01f, width, .008f + cycle * .026f,
                    .24f * (1f - cycle), stroke = 1.8f))
            }
            "forest" -> {
                if (!dark) repeat(4) { i ->
                    val cycle = wrap(t / 13f + i * .24f)
                    val side = if (i % 2 == 0) .045f else .87f
                    add(SceneMark(SceneMarkKind.LEAF, side + .045f * sin(t * .5f + i),
                        .51f + cycle * .35f, .025f, .012f, .27f,
                        rotation = 28f * sin(t * .7f + i) + if (i % 2 == 0) 20f else 155f))
                } else repeat(6) { i ->
                    val x = (if (i % 2 == 0) .08f else .91f) + .025f * sin(t * .65f + i)
                    val y = .57f + i * .047f + .018f * cos(t * .45f + i)
                    val brightness = .25f + .25f * pulse(t, 1.2f, i.toFloat())
                    add(SceneMark(SceneMarkKind.GLOW, x, y, .022f, 0f, brightness * .45f, SceneTone.LIGHT))
                    add(SceneMark(SceneMarkKind.DOT, x, y, .0035f, 0f, brightness, SceneTone.LIGHT))
                }
            }
            "dawn" -> {
                if (!dark) repeat(2) { i ->
                    val cycle = wrap(t / 24f + i * .12f)
                    add(SceneMark(SceneMarkKind.BIRD, .63f + cycle * .28f,
                        .185f + i * .03f + .009f * sin(t * .7f + i), .036f - i * .009f,
                        .004f + .004f * sin(t * 2.2f + i), .25f, stroke = 1.6f))
                } else repeat(4) { i ->
                    add(SceneMark(SceneMarkKind.DOT, .07f + i * .025f + .025f * sin(t * .45f + i),
                        .66f + i * .055f - .012f * sin(t * .75f + i), .003f, 0f,
                        .23f + .15f * pulse(t, 1.2f, i.toFloat()), SceneTone.LIGHT))
                }
                add(SceneMark(SceneMarkKind.GLOW, .15f, .25f, .20f + .035f * sin(t * .5f), 0f,
                    .07f + .03f * pulse(t, .5f), SceneTone.LIGHT))
            }
            "dusk" -> repeat(3) { i ->
                add(SceneMark(SceneMarkKind.MIST, -.18f + i * .3f + .07f * sin(t * .36f + i),
                    .76f + i * .045f + .007f * sin(t * .6f + i), .55f,
                    .030f + .008f * sin(t * .42f + i), if (dark) .25f else .22f,
                    if (dark) SceneTone.LIGHT else SceneTone.MUTED))
            }
            "paper" -> {
                // The paper scene has living bamboo/ink, rather than being excluded from all motion.
                repeat(3) { i ->
                    add(SceneMark(SceneMarkKind.INK, .035f + i * .042f + .014f * sin(t * .8f + i),
                        .79f + i * .041f + .011f * sin(t * .55f + i), .055f, .018f,
                        .28f, SceneTone.INK, rotation = -30f + i * 44f + 18f * sin(t * .7f + i)))
                }
                if (dark) {
                    val breath = .012f * sin(t * .6f)
                    add(SceneMark(SceneMarkKind.INK, .88f - breath, .78f - breath / 2,
                        .08f + 2 * breath, .025f + breath, .20f, SceneTone.INK, rotation = -50f))
                }
            }
            else -> {
                // Mooring light: vertical glints and short reflections, never water's expanding rings.
                repeat(3) { i ->
                    val light = .2f + .2f * pulse(t, .8f, i * 2f)
                    add(SceneMark(SceneMarkKind.GLINT, .18f + i * .31f + .015f * sin(t * .6f + i),
                        .77f + i * .048f, .017f + .010f * pulse(t,.8f,i*2f), .019f,
                        light, if(dark) SceneTone.LIGHT else SceneTone.ACCENT))
                    add(SceneMark(SceneMarkKind.WAVE, .11f + i * .32f + .033f * sin(t * .7f + i),
                        .80f + i * .049f, .09f + .022f * sin(t * .9f + i), .003f,
                        .25f + .09f * pulse(t, .9f, i.toFloat()),
                        if (dark) SceneTone.LIGHT else SceneTone.ACCENT, stroke = 1.8f))
                }
            }
        }

        // The weather stays recognizable, with wind direction/pace fitted to each landscape.
        val drift = when(theme) { "cloud" -> -.012f; "river" -> .011f; "engawa", "fireflies" -> .025f; "forest" -> .034f; "dawn" -> .014f; "dusk" -> -.018f; "paper" -> .007f; "night" -> -.010f; else -> .022f }
        val pace = when(theme) { "cloud" -> .6f; "river" -> .72f; "engawa", "fireflies" -> .8f; "forest" -> .85f; "dawn" -> 1.1f; "dusk" -> .75f; "paper" -> .65f; "night" -> .9f; else -> 1f }
        if (wet) repeat(if (weather == "drizzle") 16 else 24) { i ->
            val speed = if (weather == "drizzle") .08f else .15f
            add(SceneMark(SceneMarkKind.LINE, wrap(i * .173f - t * drift), wrap(i * .137f + t * speed * pace),
                -drift * .4f, if (weather == "drizzle") .010f else .018f, .16f, SceneTone.MUTED, 1.2f))
        }
        if (weather == "hail") repeat(7) { i ->
            add(SceneMark(SceneMarkKind.DOT, wrap(i * .257f - t * .025f), wrap(i * .19f + t * .22f),
                .003f, 0f, .28f, SceneTone.INK))
        }
        if (weather in setOf("snow", "snowshowers")) repeat(22) { i ->
            add(SceneMark(SceneMarkKind.DOT, wrap(i * .227f + .025f * sin(t * .7f + i) - t * drift * .2f),
                wrap(i * .173f + t * (.026f + i % 3 * .006f) * pace), .0028f + i % 3 * .0008f,
                0f, if (dark) .43f else .26f, if (dark) SceneTone.LIGHT else SceneTone.MUTED))
        }
        if (weather in setOf("wind", "fog", "overcast")) repeat(2) { i ->
            add(SceneMark(SceneMarkKind.WAVE, -.14f + i * .63f + .05f * sin(t * .45f * pace + i),
                .72f + i * .1f, .48f, .016f * sin(t * .6f * pace + i), .09f,
                SceneTone.MUTED, if (weather == "wind") 1.5f else 8f))
        }

        if (interaction != null && interactionAgeSeconds in 0f..1.6f &&
            interaction.xFraction.isFinite() && interaction.yFraction.isFinite()) {
            addAll(touchMarks(theme, interaction, interactionAgeSeconds))
        }
    }

    private fun touchMarks(theme: String, touch: SceneInteraction, age: Float): List<SceneMark> = buildList {
        val x = touch.xFraction.coerceIn(0f, 1f)
        val y = touch.yFraction.coerceIn(0f, 1f)
        val progress = (age / 1.6f).coerceIn(0f, 1f)
        val alpha = .34f * (1f - progress)
        when (theme) {
            "cloud" -> add(SceneMark(SceneMarkKind.MIST,x-.08f-progress*.13f,y,.16f+progress*.26f,.018f+progress*.04f,alpha,SceneTone.INK))
            "engawa", "fireflies" -> add(SceneMark(SceneMarkKind.GLOW,x,y,.04f+progress*.18f,0f,alpha,SceneTone.LIGHT))
            "forest" -> repeat(3) { i ->
                val direction = i * 2 * PI.toFloat() / 3
                add(SceneMark(SceneMarkKind.LEAF, x + cos(direction) * progress * .09f,
                    y + sin(direction) * progress * .045f - .015f * progress,
                    .027f, .012f, alpha, rotation = direction * 180 / PI.toFloat() + age * 35))
            }
            "dawn" -> {
                add(SceneMark(SceneMarkKind.GLOW,x,y,.035f+progress*.15f,0f,alpha,SceneTone.LIGHT))
                repeat(3) { i ->
                    val angle = (-115f+i*25f)*PI.toFloat()/180
                    add(SceneMark(SceneMarkKind.LINE,x+cos(angle)*progress*.035f,y+sin(angle)*progress*.02f,
                        cos(angle)*(.015f+progress*.045f),sin(angle)*(.009f+progress*.022f),alpha,SceneTone.ACCENT))
                }
            }
            "dusk" -> repeat(2) { i ->
                val width = .045f + progress * (.19f + i * .08f)
                add(SceneMark(SceneMarkKind.MIST, x - width / 2, y + (i - .5f) * progress * .025f,
                    width, .008f + progress * .018f, alpha))
            }
            "paper" -> repeat(3) { i ->
                add(SceneMark(SceneMarkKind.INK,x+(i-1)*progress*.05f,y-progress*.018f+i*.008f,
                    .035f+progress*.035f,.01f+progress*.009f,alpha,SceneTone.INK,rotation=-35f+i*35f))
            }
            "night" -> repeat(3) { i ->
                add(SceneMark(SceneMarkKind.GLINT,x+(i-1)*progress*.09f,y-progress*.025f+i*.01f,
                    .012f+progress*.018f,.012f+progress*.016f,alpha,SceneTone.LIGHT))
            }
            else -> repeat(2) { i ->
                val width = .024f + progress * (.21f + i * .10f)
                val height = .009f + progress * (.028f + i * .012f)
                add(SceneMark(SceneMarkKind.RING, x - width / 2, y - height / 2, width, height,
                    alpha * (1f - i * .25f), stroke = 1.8f))
            }
        }
    }
}
