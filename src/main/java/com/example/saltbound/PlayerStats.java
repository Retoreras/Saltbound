package com.example.saltbound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

/**
 * Все данные игрока: соль, атрибуты и "пятно" (соль, оставшаяся на месте смерти).
 * Неизменяемая запись: для изменений используются with-методы.
 */
public record PlayerStats(long salt, int vitality, int strength, int dexterity, int endurance,
                          long stainSalt, String stainDim, int stainX, int stainY, int stainZ) {

    public static final int ATTR_COUNT = 4; // 0 живучесть, 1 сила, 2 ловкость, 3 выносливость
    public static final int MAX_ATTR = 40;

    public static final PlayerStats DEFAULT = new PlayerStats(0, 0, 0, 0, 0, 0, "", 0, 0, 0);

    public static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("salt", 0L).forGetter(PlayerStats::salt),
            Codec.INT.optionalFieldOf("vitality", 0).forGetter(PlayerStats::vitality),
            Codec.INT.optionalFieldOf("strength", 0).forGetter(PlayerStats::strength),
            Codec.INT.optionalFieldOf("dexterity", 0).forGetter(PlayerStats::dexterity),
            Codec.INT.optionalFieldOf("endurance", 0).forGetter(PlayerStats::endurance),
            Codec.LONG.optionalFieldOf("stain_salt", 0L).forGetter(PlayerStats::stainSalt),
            Codec.STRING.optionalFieldOf("stain_dim", "").forGetter(PlayerStats::stainDim),
            Codec.INT.optionalFieldOf("stain_x", 0).forGetter(PlayerStats::stainX),
            Codec.INT.optionalFieldOf("stain_y", 0).forGetter(PlayerStats::stainY),
            Codec.INT.optionalFieldOf("stain_z", 0).forGetter(PlayerStats::stainZ)
    ).apply(inst, PlayerStats::new));

    public int level() {
        return 1 + vitality + strength + dexterity + endurance;
    }

    /** Цена повышения уровня (любого атрибута на +1). */
    public long levelUpCost() {
        return (long) (80 * Math.pow(level(), 1.4));
    }

    public float maxStamina() {
        return 100f + 4f * endurance;
    }

    public int attr(int i) {
        return switch (i) {
            case 0 -> vitality;
            case 1 -> strength;
            case 2 -> dexterity;
            case 3 -> endurance;
            default -> 0;
        };
    }

    public PlayerStats withAttr(int i, int v) {
        return switch (i) {
            case 0 -> new PlayerStats(salt, v, strength, dexterity, endurance, stainSalt, stainDim, stainX, stainY, stainZ);
            case 1 -> new PlayerStats(salt, vitality, v, dexterity, endurance, stainSalt, stainDim, stainX, stainY, stainZ);
            case 2 -> new PlayerStats(salt, vitality, strength, v, endurance, stainSalt, stainDim, stainX, stainY, stainZ);
            case 3 -> new PlayerStats(salt, vitality, strength, dexterity, v, stainSalt, stainDim, stainX, stainY, stainZ);
            default -> this;
        };
    }

    public PlayerStats withSalt(long s) {
        return new PlayerStats(s, vitality, strength, dexterity, endurance, stainSalt, stainDim, stainX, stainY, stainZ);
    }

    public boolean hasStain() {
        return stainSalt > 0;
    }

    public BlockPos stainPos() {
        return new BlockPos(stainX, stainY, stainZ);
    }

    public PlayerStats withStain(long amount, String dim, BlockPos pos) {
        return new PlayerStats(salt, vitality, strength, dexterity, endurance, amount, dim, pos.getX(), pos.getY(), pos.getZ());
    }

    public PlayerStats withoutStain() {
        return new PlayerStats(salt, vitality, strength, dexterity, endurance, 0, "", 0, 0, 0);
    }
}
