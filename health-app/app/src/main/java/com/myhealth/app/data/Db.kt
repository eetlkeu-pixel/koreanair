package com.myhealth.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val SRC_AUTO = "auto"
const val SRC_MANUAL = "manual"
const val DEV_INBODY = "INBODY"
const val DEV_HOME = "HOME"

/**
 * 하룻밤 수면. day = 기상일(epoch day).
 * bed/wake = 현재 유효값(삭제된 날은 null). autoBed/autoWake = 자동 감지 원본(수동 수정해도 보존).
 */
@Entity(tableName = "sleep")
data class SleepRec(
    @PrimaryKey val day: Long,
    val bed: Long?,
    val wake: Long?,
    val source: String,
    val autoBed: Long? = null,
    val autoWake: Long? = null,
    val deleted: Boolean = false,
) {
    val valid: Boolean get() = !deleted && bed != null && wake != null
    val minutes: Int get() = if (valid) ((wake!! - bed!!) / 60_000L).toInt() else 0
}

@Entity(tableName = "weight", indices = [Index(value = ["day", "device"], unique = true)])
data class WeightRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val device: String,
    val kg: Double,
    val smm: Double? = null,
    val pbf: Double? = null,
    val fat: Double? = null,
    val savedAt: Long = 0,
)

/** 삼성 헬스 장기 캡처 요약(기간 평균·최저·최고). 실측 원장·목표 계산에는 쓰지 않는다. */
@Entity(tableName = "archive")
data class ArchiveRec(
    @PrimaryKey val label: String,
    val sort: Int,
    val short: String,
    val avg: Double,
    val min: Double? = null,
    val max: Double? = null,
)

/** 코골이 측정 한 밤. day = 측정이 끝난 날(기상일, epoch day) */
@Entity(tableName = "snore_session")
data class SnoreSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val startAt: Long,
    /** 지연 시간이 지나 실제 감지를 시작한 시각 */
    val measureFrom: Long,
    val endAt: Long,
    /** 측정 시작 때의 방 소음 (dBFS) */
    val baselineDb: Double? = null,
    val charging: Boolean = false,
    /** 쉼표로 이어 붙인 태그 */
    val tags: String = "",
    val running: Boolean = false,
    /** 서비스가 중간에 강제 종료됨 → endAt이 중단 시각 */
    val interrupted: Boolean = false,
    /** 숨이 멈춘 듯한 패턴 횟수 */
    val apneaLike: Int = 0,
) {
    val measuredSec: Double get() = maxOf(0L, endAt - measureFrom) / 1000.0
    val tagList: List<String> get() = tags.split(',').filter { it.isNotBlank() }
}

/** 확정된 코골이 구간. meanDb·peakDb는 기준 소음 대비 dB. clipPath = 대표 샘플 파일(없을 수 있음) */
@Entity(tableName = "snore_event", indices = [Index(value = ["sessionId"])])
data class SnoreEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startAt: Long,
    val endAt: Long,
    val intensity: Int,
    val meanDb: Double,
    val peakDb: Double,
    val confidence: Double,
    val clipPath: String? = null,
    /** 사용자 라벨: null = 미평가, 1 = 코골이 맞음, 0 = 아님 */
    val label: Int? = null,
) {
    val durSec: Double get() = (endAt - startAt) / 1000.0
}

@Dao
interface SnoreDao {
    @Query("SELECT * FROM snore_session ORDER BY startAt DESC") fun sessions(): Flow<List<SnoreSession>>
    @Query("SELECT * FROM snore_event ORDER BY startAt") fun events(): Flow<List<SnoreEvent>>
    @Query("SELECT * FROM snore_session ORDER BY startAt") suspend fun sessionList(): List<SnoreSession>
    @Query("SELECT * FROM snore_event ORDER BY startAt") suspend fun eventList(): List<SnoreEvent>
    @Query("SELECT * FROM snore_session WHERE id = :id") suspend fun session(id: Long): SnoreSession?
    @Query("SELECT * FROM snore_event WHERE sessionId = :id ORDER BY startAt") suspend fun eventsOf(id: Long): List<SnoreEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSession(s: SnoreSession): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEvent(e: SnoreEvent): Long
    @Query("UPDATE snore_event SET label = :label WHERE id = :id") suspend fun setLabel(id: Long, label: Int?)
    @Query("UPDATE snore_event SET clipPath = NULL WHERE id = :id") suspend fun dropClip(id: Long)
    @Query("UPDATE snore_session SET tags = :tags WHERE id = :id") suspend fun setTags(id: Long, tags: String)
    @Query("DELETE FROM snore_session WHERE id = :id") suspend fun removeSession(id: Long)
    @Query("DELETE FROM snore_event WHERE sessionId = :id") suspend fun removeEvents(id: Long)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep ORDER BY day DESC") fun all(): Flow<List<SleepRec>>
    @Query("SELECT * FROM sleep ORDER BY day") suspend fun list(): List<SleepRec>
    @Query("SELECT * FROM sleep WHERE day = :day") suspend fun get(day: Long): SleepRec?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: SleepRec)
    @Query("DELETE FROM sleep WHERE day = :day") suspend fun remove(day: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY day DESC, savedAt DESC") fun all(): Flow<List<WeightRec>>
    @Query("SELECT * FROM weight ORDER BY day, savedAt") suspend fun list(): List<WeightRec>
    /** (day, device)가 같으면 교체 = 정정 */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: WeightRec)
    @Query("DELETE FROM weight WHERE id = :id") suspend fun remove(id: Long)
}

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM archive ORDER BY sort") fun all(): Flow<List<ArchiveRec>>
    @Query("SELECT * FROM archive ORDER BY sort") suspend fun list(): List<ArchiveRec>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: ArchiveRec)
}

@Database(
    entities = [SleepRec::class, WeightRec::class, ArchiveRec::class, SnoreSession::class, SnoreEvent::class],
    version = 2, exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun sleep(): SleepDao
    abstract fun weight(): WeightDao
    abstract fun archive(): ArchiveDao
    abstract fun snore(): SnoreDao

    companion object {
        /** v1 → v2: 코골이 측정 테이블 추가 (기존 수면·체중 데이터는 그대로) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `snore_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `startAt` INTEGER NOT NULL, `measureFrom` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `baselineDb` REAL, `charging` INTEGER NOT NULL, `tags` TEXT NOT NULL, `running` INTEGER NOT NULL, `interrupted` INTEGER NOT NULL, `apneaLike` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `snore_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `intensity` INTEGER NOT NULL, `meanDb` REAL NOT NULL, `peakDb` REAL NOT NULL, `confidence` REAL NOT NULL, `clipPath` TEXT, `label` INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_snore_event_sessionId` ON `snore_event` (`sessionId`)")
            }
        }

        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "health.db")
                .addMigrations(MIGRATION_1_2).build().also { inst = it }
        }
    }
}
