package com.example.engine

class Chunk(val cx: Int, val cz: Int) {
    private val blocks = ByteArray(CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE)

    @Volatile
    var isDirty: Boolean = true

    fun getBlock(lx: Int, y: Int, lz: Int): BlockType {
        if (lx !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE || y !in 0 until WORLD_HEIGHT) {
            return BlockType.AIR
        }
        return BlockType.fromByte(blocks[index(lx, y, lz)])
    }

    fun setBlock(lx: Int, y: Int, lz: Int, type: BlockType) {
        if (lx !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE || y !in 0 until WORLD_HEIGHT) return
        blocks[index(lx, y, lz)] = type.id
    }

    private fun index(lx: Int, y: Int, lz: Int): Int {
        return (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx
    }

    companion object {
        const val CHUNK_SIZE = 16
        const val WORLD_HEIGHT = 64
    }
}
