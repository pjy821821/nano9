package kr.techbridge.battery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, SampleEntity::class, ChargerEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao
    abstract fun chargerDao(): ChargerDao

    companion object {
        /** v2(2026-11-25): 충전기 지문 컬럼. 세션에는 시작 때 지문, 충전기에는 이름 붙일 때 학습한 지문과 대표 전력. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN plugType INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN maxCurrentUa INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN maxVoltageUv INTEGER")
                db.execSQL("ALTER TABLE chargers ADD COLUMN plugType INTEGER")
                db.execSQL("ALTER TABLE chargers ADD COLUMN maxCurrentUa INTEGER")
                db.execSQL("ALTER TABLE chargers ADD COLUMN maxVoltageUv INTEGER")
                db.execSQL("ALTER TABLE chargers ADD COLUMN typicalPowerMw REAL")
            }
        }

        fun build(context: Context): BatteryDatabase =
            Room.databaseBuilder(context, BatteryDatabase::class.java, "battery.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
