package com.example.saltbound.client;

import com.example.saltbound.PlayerStats;
import com.example.saltbound.QuickItem;
import com.example.saltbound.Saltbound;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * HUD на текстурах (assets/saltbound/textures/gui/hud/).
 * Слои снизу вверх: тёмная подложка полос, "след" урона, заливка здоровья/выносливости (обрезается по проценту),
 * рамка, флакон, числа. Все координаты ниже даны в пикселях текстур (рамка 584x104);
 * итоговый размер на экране задаётся HUD_WIDTH.
 */
public final class SaltboundHud {
    private SaltboundHud() {}

    // ---- Настройки размещения (в единицах GUI) ----
    private static final int HUD_X = 6;
    private static final int HUD_Y = 4;
    private static final float HUD_WIDTH = 250f; // ширина всего HUD на экране; меняйте под вкус

    // ---- Текстуры ----
    private static final Identifier TEX_FRAME = tex("frame");
    private static final Identifier TEX_HP = tex("hp_fill");
    private static final Identifier TEX_TRAIL = tex("hp_trail");
    private static final Identifier TEX_ST = tex("st_fill");
    private static final Identifier TEX_FLASK_FULL = tex("flask_full");
    private static final Identifier TEX_FLASK_EMPTY = tex("flask_empty");
    private static final Identifier TEX_SALT = tex("salt_icon");
    private static final Identifier TEX_SILVER = tex("silver_icon");

    private static Identifier tex(String name) {
        return Identifier.of(Saltbound.MOD_ID, "textures/gui/hud/" + name + ".png");
    }

    // ---- Геометрия в пикселях текстур ----
    private static final int FRAME_W = 584, FRAME_H = 104;
    // Окна полос в рамке (внутренность, тёмная подложка) и заливки внутри них
    private static final int HP_WIN_X = 158, HP_WIN_Y = 18, HP_WIN_W = 404, HP_WIN_H = 20;
    private static final int HP_X = 160, HP_Y = 22, HP_W = 400, HP_H = 16;
    private static final int ST_WIN_X = 158, ST_WIN_Y = 45, ST_WIN_W = 236, ST_WIN_H = 13;
    private static final int ST_X = 158, ST_Y = 48, ST_W = 234, ST_H = 10;
    private static final int FLASK_CX = 113, FLASK_CY = 53, FLASK_W = 30, FLASK_H = 45;
    private static final int LEVEL_CX = 51, LEVEL_CY = 56;
    private static final int COUNTER_X = 162, COUNTER_CY = 79, COUNTER_MAX_W = 78;

    private static final int COL_HP_BG = 0xFF210E0D;
    private static final int COL_ST_BG = 0xFF0B1A1B;
    private static final int COL_TEXT = 0xFFEFE6D0;
    private static final int COL_TEXT_DIM = 0xFF8F8F86;

    // "След" потерянного здоровья: светлая часть полосы, которая сжимается с задержкой
    private static float hpTrail = -1f;
    private static float lastHp = -1f;
    private static float trailHold = 0f;
    // Плавное значение выносливости (сервер присылает его раз в 4 тика)
    private static float stShown = -1f;

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        TextRenderer tr = mc.textRenderer;
        PlayerStats s = ClientData.stats;

        float maxHp = Math.max(1f, mc.player.getMaxHealth());
        float hp = MathHelper.clamp(mc.player.getHealth(), 0f, maxHp);
        updateTrail(hp, maxHp, tick.getLastFrameDuration());
        float hpFrac = hp / maxHp;
        float trailFrac = MathHelper.clamp(hpTrail / maxHp, 0f, 1f);

        float maxSt = Math.max(1f, ClientData.maxStamina);
        if (stShown < 0f) {
            stShown = ClientData.stamina;
        }
        stShown += (ClientData.stamina - stShown) * MathHelper.clamp(tick.getLastFrameDuration() * 0.6f, 0f, 1f);
        float stFrac = MathHelper.clamp(stShown / maxSt, 0f, 1f);

        float k = HUD_WIDTH / FRAME_W;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(HUD_X, HUD_Y, 0);
        m.scale(k, k, 1f);
        RenderSystem.enableBlend();

        // 1) подложка полос
        ctx.fill(HP_WIN_X - 1, HP_WIN_Y - 1, HP_WIN_X + HP_WIN_W + 1, HP_WIN_Y + HP_WIN_H, COL_HP_BG);
        ctx.fill(ST_WIN_X - 1, ST_WIN_Y - 1, ST_WIN_X + ST_WIN_W + 1, ST_WIN_Y + ST_WIN_H, COL_ST_BG);

        // 2) след урона
        int hpPx = Math.round(HP_W * hpFrac);
        int trailPx = Math.round(HP_W * trailFrac);
        if (trailPx > hpPx) {
            ctx.drawTexture(TEX_TRAIL, HP_X + hpPx, HP_Y, hpPx, 0, trailPx - hpPx, HP_H, HP_W, HP_H);
        }

        // 3) заливка здоровья (при низком здоровье пульсирует яркостью)
        if (hpPx > 0) {
            if (hpFrac < 0.25f) {
                float c = 0.78f + 0.22f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                RenderSystem.setShaderColor(c, c, c, 1f);
            }
            ctx.drawTexture(TEX_HP, HP_X, HP_Y, 0, 0, hpPx, HP_H, HP_W, HP_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // 4) заливка выносливости (при малом запасе краснеет)
        int stPx = Math.round(ST_W * stFrac);
        if (stPx > 0) {
            if (stFrac < 0.25f) {
                RenderSystem.setShaderColor(1f, 0.55f, 0.55f, 1f);
            }
            ctx.drawTexture(TEX_ST, ST_X, ST_Y, 0, 0, stPx, ST_H, ST_W, ST_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // 5) рамка
        ctx.drawTexture(TEX_FRAME, 0, 0, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        // 6) предмет быстрого слота
        QuickItem[] items = QuickItem.values();
        QuickItem selected = items[Math.floorMod(ClientData.selectedQuick, items.length)];
        boolean hasCharges = ClientData.flaskCharges > 0;
        if (selected == QuickItem.RED_FLASK) {
            ctx.drawTexture(hasCharges ? TEX_FLASK_FULL : TEX_FLASK_EMPTY,
                    FLASK_CX - FLASK_W / 2, FLASK_CY - FLASK_H / 2, 0, 0, FLASK_W, FLASK_H, FLASK_W, FLASK_H);
        }

        // 7) числа: уровень и счётчик фляги
        String level = String.valueOf(s.level());
        float levelScale = level.length() >= 3 ? 1.8f : 2.4f;
        m.push();
        m.translate(LEVEL_CX, LEVEL_CY, 0);
        m.scale(levelScale, levelScale, 1f);
        ctx.drawCenteredTextWithShadow(tr, level, 0, -4, 0xFFEFD9A0);
        m.pop();

        if (selected == QuickItem.RED_FLASK) {
            Text label = Text.empty()
                    .append("[")
                    .append(SaltboundClient.useQuickKey.getBoundKeyLocalizedText())
                    .append("] " + ClientData.flaskCharges + "/" + ClientData.flaskMax);
            float ts = Math.min(1.6f, COUNTER_MAX_W / (float) Math.max(1, tr.getWidth(label)));
            m.push();
            m.translate(COUNTER_X, COUNTER_CY - 4 * ts, 0);
            m.scale(ts, ts, 1f);
            ctx.drawTextWithShadow(tr, label, 0, 0, hasCharges ? COL_TEXT : COL_TEXT_DIM);
            m.pop();
        }

        m.pop();

        // ---- соль и серебро (слева внизу): иконки и числа
        int sy = ctx.getScaledWindowHeight() - 34;
        drawIcon(ctx, TEX_SALT, 8, sy, 14);
        ctx.drawTextWithShadow(tr, String.valueOf(s.salt()), 26, sy + 3, COL_TEXT);
        drawIcon(ctx, TEX_SILVER, 8, sy + 16, 14);
        ctx.drawTextWithShadow(tr, String.valueOf(ClientData.silver), 26, sy + 19, COL_TEXT);
    }

    /** Рисует квадратную иконку 48x48 в заданном размере (в единицах GUI). */
    private static void drawIcon(DrawContext ctx, Identifier tex, int x, int y, int size) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        float k = size / 48f;
        m.scale(k, k, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(tex, 0, 0, 0, 0, 48, 48, 48, 48);
        m.pop();
    }

    private static void updateTrail(float hp, float maxHp, float dt) {
        if (hpTrail < 0f) {
            hpTrail = hp;
            lastHp = hp;
        }
        if (hp < lastHp - 0.01f) {
            trailHold = 12f; // после удара след держится ~0.6 сек
        }
        lastHp = hp;
        if (hp >= hpTrail) {
            hpTrail = hp;
        } else if (trailHold > 0f) {
            trailHold -= dt;
        } else {
            hpTrail = Math.max(hp, hpTrail - maxHp * 0.04f * dt);
        }
    }
}
