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

@Database(entities = [SleepRec::class, WeightRec::class, ArchiveRec::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun sleep(): SleepDao
    abstract fun weight(): WeightDao
    abstract fun archive(): ArchiveDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "health.db").build().also { inst = it }
        }
    }
}
