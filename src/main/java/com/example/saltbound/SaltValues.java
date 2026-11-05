package com.example.saltbound;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

/** Таблица награды солью: у каждого типа моба свой диапазон (по сложности). Для неизвестных (модовых) мобов считается от здоровья. */
public final class SaltValues {
    private SaltValues() {}

    private static final Map<String, int[]> TABLE = new HashMap<>();

    private static void put(String path, int min, int max) {
        TABLE.put("minecraft:" + path, new int[]{min, max});
    }

    static {
        // Слабые
        put("silverfish", 2, 4);
        put("endermite", 3, 5);
        put("slime", 1, 2);
        put("magma_cube", 2, 4);
        put("spider", 5, 9);
        put("cave_spider", 6, 10);
        put("zombie", 6, 10);
        put("zombie_villager", 6, 10);
        put("piglin", 6, 10);
        put("drowned", 7, 11);
        put("skeleton", 7, 11);
        put("husk", 8, 12);
        put("zombified_piglin", 8, 14);
        put("vex", 5, 8);
        // Средние
        put("stray", 9, 13);
        put("creeper", 9, 15);
        put("bogged", 10, 15);
        put("pillager", 10, 16);
        put("phantom", 10, 16);
        put("vindicator", 14, 22);
        put("witch", 15, 25);
        put("guardian", 15, 22);
        put("blaze", 18, 28);
        put("enderman", 20, 30);
        put("hoglin", 20, 30);
        put("zoglin", 22, 32);
        // Сильные
        put("ghast", 25, 35);
        put("breeze", 25, 35);
        put("wither_skeleton", 28, 40);
        put("evoker", 30, 45);
        put("shulker", 30, 40);
        put("piglin_brute", 40, 55);
        put("ravager", 45, 65);
        // Мини-боссы и боссы
        put("elder_guardian", 150, 200);
        put("warden", 400, 600);
        put("wither", 2200, 2800);
        put("ender_dragon", 5000, 6500);
    }

    /** Случайная награда за данного моба (до множителей респавна). */
    public static long roll(LivingEntity e) {
        String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
        int[] r = TABLE.get(id);
        if (r == null) {
            int base = Math.max(1, Math.round(e.getMaxHealth() * 0.45f)); // zombie (20 HP) ~ 9
            r = new int[]{Math.max(1, (int) (base * 0.8)), Math.max(1, (int) Math.ceil(base * 1.2))};
        }
        return e.getRandom().nextBetween(r[0], Math.max(r[0], r[1]));
    }
}
