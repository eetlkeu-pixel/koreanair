package com.myhealth.app.data

import android.content.Context
import android.content.SharedPreferences
import com.myhealth.app.core.DetectParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    /** 탐색 구간 시작·끝 (자정 기준 분). 기본 20:00 ~ 다음 날 14:00 */
    val winStartMin: Int = 20 * 60,
    val winEndMin: Int = 14 * 60,
    val minSleepMin: Int = 180,
    val shortUseMin: Int = 2,
    val mergeGapMin: Int = 30,
    val latencyMin: Int = 0,
    val targetSleepMin: Int = 420,
    val goal1: Double = 89.0,
    val goal2: Double = 82.0,
) {
    val detect: DetectParams get() = DetectParams(minSleepMin, shortUseMin, mergeGapMin, latencyMin)

    /** 탐색 구간 길이(분) */
    val winLenMin: Int get() = Math.floorMod(winEndMin - winStartMin - 1, 1440) + 1
}

class Prefs private constructor(private val sp: SharedPreferences) {
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Settings> get() = state

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            sp.getInt("winStartMin", d.winStartMin), sp.getInt("winEndMin", d.winEndMin),
            sp.getInt("minSleepMin", d.minSleepMin), sp.getInt("shortUseMin", d.shortUseMin),
            sp.getInt("mergeGapMin", d.mergeGapMin), sp.getInt("latencyMin", d.latencyMin),
            sp.getInt("targetSleepMin", d.targetSleepMin),
            sp.getFloat("goal1", d.goal1.toFloat()).toDouble().round1(),
            sp.getFloat("goal2", d.goal2.toFloat()).toDouble().round1(),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putInt("winStartMin", s.winStartMin).putInt("winEndMin", s.winEndMin)
            .putInt("minSleepMin", s.minSleepMin).putInt("shortUseMin", s.shortUseMin)
            .putInt("mergeGapMin", s.mergeGapMin).putInt("latencyMin", s.latencyMin)
            .putInt("targetSleepMin", s.targetSleepMin)
            .putFloat("goal1", s.goal1.toFloat()).putFloat("goal2", s.goal2.toFloat())
            .apply()
        state.value = s
    }

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)).also { inst = it }
        }
    }
}

fun Double.round1(): Double = Math.round(this * 10) / 10.0
