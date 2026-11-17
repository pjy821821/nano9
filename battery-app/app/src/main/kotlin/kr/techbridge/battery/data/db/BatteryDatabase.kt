package kr.techbridge.battery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SampleEntity::class, ChargerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao
    abstract fun chargerDao(): ChargerDao

    companion object {
        fun build(context: Context): BatteryDatabase =
            Room.databaseBuilder(context, BatteryDatabase::class.java, "battery.db")
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
