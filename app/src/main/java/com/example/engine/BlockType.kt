package com.example.engine

enum class BlockType(
    val id: Byte,
    val displayName: String,
    val isSolid: Boolean,
    val isCutout: Boolean,
    val isTranslucent: Boolean,
    val isBreakable: Boolean,
    val topTile: Int,
    val sideTile: Int,
    val bottomTile: Int
) {
    AIR(0, "Воздух", isSolid = false, isCutout = true, isTranslucent = false, isBreakable = false, topTile = -1, sideTile = -1, bottomTile = -1),
    GRASS(1, "Трава", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 0, sideTile = 1, bottomTile = 2),
    DIRT(2, "Земля", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 2, sideTile = 2, bottomTile = 2),
    STONE(3, "Камень", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 3, sideTile = 3, bottomTile = 3),
    COBBLESTONE(4, "Булыжник", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 4, sideTile = 4, bottomTile = 4),
    OAK_LOG(5, "Бревно дуба", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 5, sideTile = 6, bottomTile = 5),
    OAK_PLANKS(6, "Доски", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 7, sideTile = 7, bottomTile = 7),
    OAK_LEAVES(7, "Листва дуба", isSolid = true, isCutout = true, isTranslucent = false, isBreakable = true, topTile = 8, sideTile = 8, bottomTile = 8),
    SAND(8, "Песок", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 9, sideTile = 9, bottomTile = 9),
    BRICKS(9, "Кирпич", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 10, sideTile = 10, bottomTile = 10),
    GLASS(10, "Стекло", isSolid = true, isCutout = true, isTranslucent = false, isBreakable = true, topTile = 11, sideTile = 11, bottomTile = 11),
    GLOW_LAMP(11, "Светильник", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 12, sideTile = 12, bottomTile = 12),
    STONE_BRICKS(12, "Кам. кирпич", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 13, sideTile = 13, bottomTile = 13),
    EMERALD_BLOCK(13, "Изумрудный блок", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 14, sideTile = 14, bottomTile = 14),
    BEDROCK(14, "Бедрок", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = false, topTile = 15, sideTile = 15, bottomTile = 15),
    WATER(15, "Вода", isSolid = false, isCutout = false, isTranslucent = true, isBreakable = false, topTile = 16, sideTile = 16, bottomTile = 16),
    SNOW_BLOCK(16, "Снег", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 17, sideTile = 17, bottomTile = 17),
    SNOW_GRASS(17, "Снежная трава", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 17, sideTile = 18, bottomTile = 2),
    SPRUCE_LOG(18, "Бревно ели", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 5, sideTile = 19, bottomTile = 5),
    SPRUCE_LEAVES(19, "Хвоя ели", isSolid = true, isCutout = true, isTranslucent = false, isBreakable = true, topTile = 20, sideTile = 20, bottomTile = 20),
    COAL_ORE(20, "Угольная руда", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 21, sideTile = 21, bottomTile = 21),
    IRON_ORE(21, "Железная руда", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 22, sideTile = 22, bottomTile = 22),
    DIAMOND_ORE(22, "Алмазная руда", isSolid = true, isCutout = false, isTranslucent = false, isBreakable = true, topTile = 23, sideTile = 23, bottomTile = 23);

    val isOpaque: Boolean
        get() = this != AIR && !isCutout && !isTranslucent

    companion object {
        private val byId = entries.associateBy { it.id.toInt() }

        val placeableBlocks: List<BlockType> = entries.filter {
            it != AIR && it != BEDROCK && it != WATER
        }

        fun fromId(id: Int): BlockType = byId[id] ?: AIR
        fun fromByte(id: Byte): BlockType = byId[id.toInt()] ?: AIR
    }
}
