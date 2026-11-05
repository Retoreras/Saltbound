package com.example.saltbound.client;

import com.example.saltbound.ModPayloads;
import com.example.saltbound.PlayerExtras;
import com.example.saltbound.PlayerStats;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Экран святилища: атрибуты за соль и улучшения фляги за серебро. */
public class SanctuaryScreen extends Screen {
    private static final String[] ATTR_KEYS = {
            "attr.saltbound.vitality", "attr.saltbound.strength", "attr.saltbound.dexterity", "attr.saltbound.endurance"
    };
    private static final int FLASK_CHARGES = PlayerStats.ATTR_COUNT;     // индекс кнопки / тип улучшения
    private static final int FLASK_POTENCY = PlayerStats.ATTR_COUNT + 1;
    private final ButtonWidget[] buttons = new ButtonWidget[PlayerStats.ATTR_COUNT + 2];

    public SanctuaryScreen() {
        super(Text.translatable("screen.saltbound.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            int y = i < PlayerStats.ATTR_COUNT ? top + i * 24 : top + 108 + (i - PlayerStats.ATTR_COUNT) * 24;
            buttons[i] = addDrawableChild(ButtonWidget.builder(Text.empty(),
                            b -> ClientPlayNetworking.send(new ModPayloads.LevelUp(idx)))
                    .dimensions(cx - 100, y, 200, 20)
                    .build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        PlayerStats s = ClientData.stats;
        long cost = s.levelUpCost();
        for (int i = 0; i < PlayerStats.ATTR_COUNT; i++) {
            buttons[i].setMessage(Text.translatable(ATTR_KEYS[i]).append(": " + s.attr(i) + "  (+1)"));
            buttons[i].active = s.salt() >= cost && s.attr(i) < PlayerStats.MAX_ATTR;
        }

        // Фляга: заряды
        int cl = ClientData.flaskChargeLvl;
        long cc = PlayerExtras.chargeCost(cl);
        boolean chargesMax = cl >= PlayerExtras.MAX_CHARGE_LVL;
        buttons[FLASK_CHARGES].setMessage(chargesMax
                ? Text.translatable("screen.saltbound.flask_charges_max")
                : Text.translatable("screen.saltbound.flask_charges", ClientData.flaskMax, ClientData.flaskMax + 1, cc));
        buttons[FLASK_CHARGES].active = !chargesMax && ClientData.silver >= cc;

        // Фляга: сила лечения
        int pl = ClientData.flaskPotencyLvl;
        long pc = PlayerExtras.potencyCost(pl);
        boolean potencyMax = pl >= PlayerExtras.MAX_POTENCY_LVL;
        int nowPct = Math.round(PlayerExtras.healFraction(pl) * 100f);
        int nextPct = Math.round(PlayerExtras.healFraction(pl + 1) * 100f);
        buttons[FLASK_POTENCY].setMessage(potencyMax
                ? Text.translatable("screen.saltbound.flask_potency_max", nowPct)
                : Text.translatable("screen.saltbound.flask_potency", nowPct, nextPct, pc));
        buttons[FLASK_POTENCY].active = !potencyMax && ClientData.silver >= pc;

        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top - 50, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.saltbound.level", s.level()), cx, top - 38, 0xE8D080);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.saltbound.wallet", s.salt(), ClientData.silver), cx, top - 26, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.saltbound.cost", cost), cx, top + 96, 0xA0A0A0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
