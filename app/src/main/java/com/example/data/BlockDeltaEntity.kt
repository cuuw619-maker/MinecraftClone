package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "block_deltas",
    primaryKeys = ["worldId", "x", "y", "z"],
    foreignKeys = [
        ForeignKey(
            entity = WorldEntity::class,
            parentColumns = ["id"],
            childColumns = ["worldId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["worldId"])]
)
data class BlockDeltaEntity(
    val worldId: Long,
    val x: Int,
    val y: Int,
    val z: Int,
    val blockId: Int
)
