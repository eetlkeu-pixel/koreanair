package com.myhealth.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 전체 백업(JSON)·복원, CSV 내보내기, 첫 실행 시드 가져오기 */
object Backup {
    private fun JSONObject.optD(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.optL(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null

    suspend fun exportJson(ctx: Context): String {
        val db = AppDb.get(ctx)
        val s = Prefs.get(ctx).flow.value
        val root = JSONObject().put("version", 1)
        root.put("settings", JSONObject()
            .put("winStartMin", s.winStartMin).put("winEndMin", s.winEndMin)
            .put("minSleepMin", s.minSleepMin).put("shortUseMin", s.shortUseMin)
            .put("mergeGapMin", s.mergeGapMin).put("latencyMin", s.latencyMin)
            .put("targetSleepMin", s.targetSleepMin).put("goal1", s.goal1).put("goal2", s.goal2))
        root.put("sleep", JSONArray().also { a ->
            db.sleep().list().forEach {
                a.put(JSONObject().put("date", LocalDate.ofEpochDay(it.day).toString())
                    .put("bed", it.bed ?: JSONObject.NULL).put("wake", it.wake ?: JSONObject.NULL)
                    .put("source", it.source)
                    .put("autoBed", it.autoBed ?: JSONObject.NULL).put("autoWake", it.autoWake ?: JSONObject.NULL)
                    .put("deleted", it.deleted))
            }
        })
        root.put("weight", JSONArray().also { a ->
            db.weight().list().forEach {
                a.put(JSONObject().put("date", LocalDate.ofEpochDay(it.day).toString())
                    .put("device", it.device).put("kg", it.kg)
                    .put("smm", it.smm ?: JSONObject.NULL).put("pbf", it.pbf ?: JSONObject.NULL)
                    .put("fat", it.fat ?: JSONObject.NULL).put("savedAt", it.savedAt))
            }
        })
        root.put("archive", JSONArray().also { a ->
            db.archive().list().forEach {
                a.put(JSONObject().put("label", it.label).put("short", it.short).put("avg", it.avg)
                    .put("min", it.min ?: JSONObject.NULL).put("max", it.max ?: JSONObject.NULL))
            }
        })
        return root.toString(2)
    }

    /** 백업 내용을 현재 데이터에 합친다(같은 날짜·기기는 덮어씀). 가져온 행 수를 돌려준다. */
    suspend fun importJson(ctx: Context, text: String, withSettings: Boolean = true): Int {
        val root = JSONObject(text)
        val db = AppDb.get(ctx)
        var n = 0
        if (withSettings) root.optJSONObject("settings")?.let { o ->
            val d = Prefs.get(ctx).flow.value
            Prefs.get(ctx).save(Settings(
                o.optInt("winStartMin", d.winStartMin), o.optInt("winEndMin", d.winEndMin),
                o.optInt("minSleepMin", d.minSleepMin), o.optInt("shortUseMin", d.shortUseMin),
                o.optInt("mergeGapMin", d.mergeGapMin), o.optInt("latencyMin", d.latencyMin),
                o.optInt("targetSleepMin", d.targetSleepMin),
                o.optDouble("goal1", d.goal1), o.optDouble("goal2", d.goal2)))
        }
        root.optJSONArray("sleep")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.sleep().put(SleepRec(LocalDate.parse(o.getString("date")).toEpochDay(),
                    o.optL("bed"), o.optL("wake"), o.optString("source", SRC_AUTO),
                    o.optL("autoBed"), o.optL("autoWake"), o.optBoolean("deleted", false)))
                n++
            }
        }
        root.optJSONArray("weight")?.let { a ->
            val base = System.currentTimeMillis()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.weight().put(WeightRec(0, LocalDate.parse(o.getString("date")).toEpochDay(),
                    o.getString("device"), o.getDouble("kg"), o.optD("smm"), o.optD("pbf"), o.optD("fat"),
                    o.optL("savedAt") ?: (base + i)))
                n++
            }
        }
        root.optJSONArray("archive")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.archive().put(ArchiveRec(o.getString("label"), i, o.optString("short", o.getString("label")),
                    o.getDouble("avg"), o.optD("min"), o.optD("max")))
                n++
            }
        }
        return n
    }

    /** 첫 실행 때 APK에 담긴 seed.json(있으면)을 한 번만 가져온다. */
    suspend fun seedIfNeeded(ctx: Context) {
        val prefs = Prefs.get(ctx)
        if (prefs.seeded) return
        runCatching {
            val text = ctx.assets.open("seed.json").bufferedReader().use { it.readText() }
            importJson(ctx, text, withSettings = false)
        }
        prefs.seeded = true
    }

    suspend fun sleepCsv(ctx: Context): String {
        val zone = ZoneId.systemDefault()
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        fun t(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(f)
        val sb = StringBuilder("date,bedtime,waketime,minutes,source\n")
        AppDb.get(ctx).sleep().list().filter { it.valid }.forEach {
            sb.append("${LocalDate.ofEpochDay(it.day)},${t(it.bed!!)},${t(it.wake!!)},${it.minutes},${it.source}\n")
        }
        return sb.toString()
    }

    suspend fun weightCsv(ctx: Context): String {
        val sb = StringBuilder("date,device,weight_kg,skeletal_muscle_kg,body_fat_pct,body_fat_kg\n")
        AppDb.get(ctx).weight().list().forEach {
            sb.append("${LocalDate.ofEpochDay(it.day)},${it.device},${it.kg},${it.smm ?: ""},${it.pbf ?: ""},${it.fat ?: ""}\n")
        }
        return sb.toString()
    }
}
