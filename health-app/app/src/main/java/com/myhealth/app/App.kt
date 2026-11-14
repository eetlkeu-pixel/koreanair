package com.myhealth.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.app.collect.Collector
import com.myhealth.app.core.Night
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.Backup
import com.myhealth.app.data.Prefs
import com.myhealth.app.data.SRC_AUTO
import com.myhealth.app.data.SRC_MANUAL
import com.myhealth.app.data.Settings
import com.myhealth.app.data.SleepRec
import com.myhealth.app.data.WeightRec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Collector.schedule(this) }
    }
}

fun SleepRec.toNight(zone: ZoneId = ZoneId.systemDefault()): Night? {
    if (!valid) return null
    fun mod(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
    return Night(day, mod(bed!!), mod(wake!!), minutes)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val prefs = Prefs.get(app)

    val settings = prefs.flow
    val sleeps = db.sleep().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val weights = db.weight().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archive = db.archive().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasPermission = MutableStateFlow(Collector.hasPermission(app))
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) { Backup.seedIfNeeded(app) }
    }

    /** 앱을 열 때마다 권한을 다시 확인하고 미수집 날짜를 재수집 */
    fun onResume() {
        hasPermission.value = Collector.hasPermission(getApplication())
        if (hasPermission.value) viewModelScope.launch { Collector.collect(getApplication()) }
    }

    fun collectNow() = viewModelScope.launch {
        val n = Collector.collect(getApplication())
        message.value = when {
            n < 0 -> "사용 정보 접근 권한이 필요합니다"
            n == 0 -> "새로 바뀐 기록이 없습니다"
            else -> "${n}일의 기록을 갱신했습니다"
        }
    }

    fun saveSettings(s: Settings) {
        prefs.save(s)
    }

    fun saveSleepManual(day: Long, bed: Long, wake: Long) = viewModelScope.launch(Dispatchers.IO) {
        val old = db.sleep().get(day)
        db.sleep().put((old ?: SleepRec(day, bed, wake, SRC_MANUAL)).copy(bed = bed, wake = wake, source = SRC_MANUAL, deleted = false))
    }

    /** 오류 기록 제외. 자동 수집이 다시 만들지 않도록 삭제 표시로 남긴다. */
    fun deleteSleep(day: Long) = viewModelScope.launch(Dispatchers.IO) {
        val old = db.sleep().get(day) ?: return@launch
        db.sleep().put(old.copy(bed = null, wake = null, source = SRC_MANUAL, deleted = true))
    }

    fun restoreAutoSleep(day: Long) = viewModelScope.launch(Dispatchers.IO) {
        val old = db.sleep().get(day) ?: return@launch
        if (old.autoBed != null && old.autoWake != null)
            db.sleep().put(old.copy(bed = old.autoBed, wake = old.autoWake, source = SRC_AUTO, deleted = false))
        else db.sleep().remove(day)
    }

    /** editingId가 있으면 그 행을 고친다. 같은 날짜·기기 행이 이미 있으면 교체(정정)된다. */
    fun saveWeight(editingId: Long?, rec: WeightRec) = viewModelScope.launch(Dispatchers.IO) {
        if (editingId != null) db.weight().remove(editingId)
        db.weight().put(rec.copy(id = 0, savedAt = System.currentTimeMillis()))
    }

    fun deleteWeight(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.weight().remove(id) }

    fun restore(text: String) = viewModelScope.launch(Dispatchers.IO) {
        message.value = runCatching { "${Backup.importJson(getApplication(), text)}건을 복원했습니다" }
            .getOrElse { "복원 실패: 백업 파일 형식이 아닙니다" }
    }
}
