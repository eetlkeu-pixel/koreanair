package com.myhealth.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myhealth.app.data.AppDb
import com.myhealth.app.data.SnoreEvent
import com.myhealth.app.data.SnoreSession
import com.myhealth.app.snore.Wav
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** 이미 설치된 v1(수면·체중만 있는) DB가 데이터 손실 없이 v2(코골이 추가)로 올라가는지 확인 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun v1DatabaseUpgradesAndKeepsData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = ctx.getDatabasePath("migrate-test.db").apply { parentFile!!.mkdirs(); delete() }
        // v1.0·v1.1이 만든 것과 같은 스키마
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `sleep` (`day` INTEGER NOT NULL, `bed` INTEGER, `wake` INTEGER, `source` TEXT NOT NULL, `autoBed` INTEGER, `autoWake` INTEGER, `deleted` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `weight` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `device` TEXT NOT NULL, `kg` REAL NOT NULL, `smm` REAL, `pbf` REAL, `fat` REAL, `savedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_day_device` ON `weight` (`day`, `device`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `archive` (`label` TEXT NOT NULL, `sort` INTEGER NOT NULL, `short` TEXT NOT NULL, `avg` REAL NOT NULL, `min` REAL, `max` REAL, PRIMARY KEY(`label`))")
            db.execSQL("INSERT INTO weight (day, device, kg, savedAt) VALUES (20000, 'HOME', 98.0, 1)")
            db.execSQL("INSERT INTO sleep (day, bed, wake, source, deleted) VALUES (20000, 1000, 2000, 'auto', 0)")
            db.version = 1
        }
        val db = Room.databaseBuilder(ctx, AppDb::class.java, "migrate-test.db").addMigrations(AppDb.MIGRATION_1_2).build()
        try {
            assertEquals(98.0, db.weight().list().single().kg, 0.0)
            assertEquals(1000L, db.sleep().list().single().bed)
            val id = db.snore().putSession(SnoreSession(0, 20000, 1, 1, 2))
            db.snore().putEvent(SnoreEvent(0, id, 1, 2, 2, 22.0, 25.0, 0.7))
            assertEquals(1, db.snore().eventsOf(id).size)
        } finally { db.close() }
    }

    @Test fun wavFileHasValidHeader() {
        val f = File.createTempFile("clip", ".wav")
        Wav.write(f, ShortArray(8000) { (it % 100).toShort() }, 8000)
        val b = f.readBytes()
        assertEquals(44 + 16000, b.size)
        assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4))
    }
}
