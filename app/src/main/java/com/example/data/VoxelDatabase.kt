package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorldEntity::class, BlockDeltaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoxelDatabase : RoomDatabase() {
    abstract fun worldDao(): WorldDao

    companion object {
        @Volatile
        private var INSTANCE: VoxelDatabase? = null

        fun getInstance(context: Context): VoxelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxelDatabase::class.java,
                    "voxelbox_worlds.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
