package com.example.saltbound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Серебро и улучшения фляги (не теряются при смерти, кроме небольшого процента серебра). */
public record PlayerExtras(long silver, int flaskCharges, int flaskPotency) {
    public static final PlayerExtras DEFAULT = new PlayerExtras(0, 0, 0);

    public static final int MAX_CHARGE_LVL = 3;   // +3 заряда (3 -> 6)
    public static final int MAX_POTENCY_LVL = 4;  // +5% лечения за уровень (40% -> 60%)
    private static final long[] CHARGE_COST = {40, 120, 300};
    private static final long[] POTENCY_COST = {30, 90, 200, 400};

    public static final Codec<PlayerExtras> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("silver", 0L).forGetter(PlayerExtras::silver),
            Codec.INT.optionalFieldOf("flask_charges", 0).forGetter(PlayerExtras::flaskCharges),
            Codec.INT.optionalFieldOf("flask_potency", 0).forGetter(PlayerExtras::flaskPotency)
    ).apply(inst, PlayerExtras::new));

    public PlayerExtras withSilver(long v) {
        return new PlayerExtras(Math.max(0, v), flaskCharges, flaskPotency);
    }

    public PlayerExtras withFlaskCharges(int v) {
        return new PlayerExtras(silver, v, flaskPotency);
    }

    public PlayerExtras withFlaskPotency(int v) {
        return new PlayerExtras(silver, flaskCharges, v);
    }

    /** Цена следующего улучшения зарядов (при текущем уровне lvl). */
    public static long chargeCost(int lvl) {
        return lvl >= 0 && lvl < CHARGE_COST.length ? CHARGE_COST[lvl] : -1;
    }

    public static long potencyCost(int lvl) {
        return lvl >= 0 && lvl < POTENCY_COST.length ? POTENCY_COST[lvl] : -1;
    }

    /** Доля макс. здоровья, которую лечит глоток, на данном уровне улучшения. */
    public static float healFraction(int potencyLvl) {
        return Saltbound.FLASK_HEAL_FRACTION + 0.05f * potencyLvl;
    }
}
