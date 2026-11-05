package com.example.saltbound.client;

import com.example.saltbound.ModPayloads;
import com.example.saltbound.PlayerStats;

/** Клиентская копия данных, полученных от сервера. */
public final class ClientData {
    private ClientData() {}

    public static PlayerStats stats = PlayerStats.DEFAULT;
    public static float stamina = 100f;
    public static float maxStamina = 100f;

    public static int flaskCharges = 3;
    public static int flaskMax = 3;
    public static long silver = 0;
    public static int flaskChargeLvl = 0;
    public static int flaskPotencyLvl = 0;
    /** Выбранный предмет быстрого слота (индекс QuickItem). */
    public static int selectedQuick = 0;

    // Итоги последней смерти (для экрана смерти)
    public static long deathLost = 0;
    public static boolean deathOldLost = false;
    public static long deathOldAmount = 0;
    public static long deathSilver = 0;

    public static void apply(ModPayloads.StatsSync p) {
        stats = new PlayerStats(p.salt(), p.vitality(), p.strength(), p.dexterity(), p.endurance(), 0, "", 0, 0, 0);
        stamina = p.stamina();
        maxStamina = p.maxStamina();
        flaskCharges = p.flaskCharges();
        flaskMax = p.flaskMax();
        silver = p.silver();
        flaskChargeLvl = p.flaskChargeLvl();
        flaskPotencyLvl = p.flaskPotencyLvl();
    }
}
