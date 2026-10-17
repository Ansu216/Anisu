package com.ansu.anime.core.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import com.ansu.anime.data.prefs.AppearancePrefs

/**
 * App-wide haptics. Every vibration here is deliberately gentle: a short, low-amplitude pulse (or, on phones
 * with a modern haptic motor, the soft "low tick" primitive) instead of the stock sharp click that
 * `performHapticFeedback` gives on many devices.
 *
 * Three strengths (30% / 50% / 70%), from lightest to firmest:
 *  - [tick]  - selection changes: switching tabs, flipping a switch, picking a chip.
 *  - [soft]  - taps on content: a poster, a card, a play button.
 *  - [hold]  - a press-and-hold that opens something: the Continue Watching menu, a drag pick-up.
 *
 * The "Haptic feedback" switch in Settings turns all of it off, and the system's own "touch feedback" setting is
 * respected as well. Calls are cheap and safe from any thread; they do nothing until [init] has run.
 */
object Haptics {

    /**
     * Intensity is a plain percentage of the motor's range: tick 30%, tap 50%, hold 70%. The same primitive is
     * used for all three, so the percentages mean the same thing on phones with a modern haptic motor; older
     * phones get a one-shot at the same percentage of full amplitude.
     */
    private enum class Strength(val millis: Long, val percent: Int) {
        Tick(millis = 12L, percent = 30),
        Soft(millis = 16L, percent = 50),
        Hold(millis = 22L, percent = 70);

        val scale: Float get() = percent / 100f
        val amplitude: Int get() = (255 * percent / 100f).toInt().coerceIn(1, 255)
    }

    private var appContext: Context? = null
    private var vibrator: Vibrator? = null
    private var prefs: AppearancePrefs? = null
    private var lastAt = 0L

    /** Called once from the app container, so the call sites need no context. */
    fun init(context: Context, appearancePrefs: AppearancePrefs) {
        appContext = context.applicationContext
        prefs = appearancePrefs
        vibrator = runCatching { context.applicationContext.getSystemService(Vibrator::class.java) }.getOrNull()
    }

    fun tick() = play(Strength.Tick)

    fun soft() = play(Strength.Soft)

    fun hold() = play(Strength.Hold)

    private fun play(strength: Strength) {
        val v = vibrator ?: return
        if (prefs?.hapticsEnabled?.value != true) return
        if (!v.hasVibrator() || !systemTouchFeedbackOn()) return
        // A burst of events (a fast swipe over several tabs) must not turn into a buzz.
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MILLIS) return
        lastAt = now
        runCatching { v.vibrate(effectFor(v, strength)) }
    }

    private fun effectFor(v: Vibrator, strength: Strength): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v.areAllPrimitivesSupported(PRIMITIVE_TICK)) {
            return VibrationEffect.startComposition().addPrimitive(PRIMITIVE_TICK, strength.scale).compose()
        }
        // Without amplitude control the amplitude is ignored, so keep the pulse as short as it can be.
        return if (v.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(strength.millis, strength.amplitude)
        } else {
            VibrationEffect.createOneShot(strength.millis, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun systemTouchFeedbackOn(): Boolean {
        val resolver = appContext?.contentResolver ?: return true
        return runCatching { Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }
            .getOrDefault(true)
    }

    private const val MIN_GAP_MILLIS = 35L

    // VibrationEffect.Composition.PRIMITIVE_* values, inlined so this file compiles without an API-30 guard.
    private const val PRIMITIVE_TICK = 7
}

/** `clickable` that plays a soft tap first - the drop-in for posters and cards. */
fun Modifier.softClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled) {
        Haptics.soft()
        onClick()
    }

/** `clickable` that plays the lightest tick first - the drop-in for chips, tabs and other selectors. */
fun Modifier.tickClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled) {
        Haptics.tick()
        onClick()
    }
