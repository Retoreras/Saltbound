package com.example.saltbound.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Надпись на экране смерти: потерянная соль, потерянное серебро и предупреждение о старой соли. */
public final class DeathOverlay {
    private DeathOverlay() {}

    public static void render(DrawContext ctx, Screen screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int cx = screen.width / 2;
        int y = screen.height / 4 + 124; // ниже кнопок "Возродиться" / "Главное меню"

        boolean lost = ClientData.deathLost > 0;
        Text main = lost
                ? Text.translatable("death.saltbound.lost", ClientData.deathLost)
                : Text.translatable("death.saltbound.none");

        ctx.getMatrices().push();
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, main, (int) (cx / 1.5f), (int) (y / 1.5f),
                lost ? 0xFFE8C060 : 0xFFA0A0A0);
        ctx.getMatrices().pop();

        int line = y + 18;
        if (ClientData.deathSilver > 0) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer,
                    Text.translatable("death.saltbound.silver_lost", ClientData.deathSilver),
                    cx, line, 0xFFC8C6BD);
            line += 12;
        }
        if (ClientData.deathOldLost) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer,
                    Text.translatable("death.saltbound.old_lost", ClientData.deathOldAmount),
                    cx, line, 0xFFFF5555);
        }
    }
}
