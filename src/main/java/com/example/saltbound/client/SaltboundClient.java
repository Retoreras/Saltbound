package com.example.saltbound.client;

import com.example.saltbound.ModPayloads;
import com.example.saltbound.QuickItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class SaltboundClient implements ClientModInitializer {
    public static KeyBinding rollKey;
    public static KeyBinding useQuickKey;
    public static KeyBinding cycleQuickKey;

    @Override
    public void onInitializeClient() {
        rollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saltbound.roll", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.category.saltbound"));
        useQuickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saltbound.use_quick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.saltbound"));
        cycleQuickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saltbound.cycle_quick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.category.saltbound"));

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.StatsSync.ID,
                (payload, ctx) -> ClientData.apply(payload));
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenSanctuary.ID,
                (payload, ctx) -> ctx.client().setScreen(new SanctuaryScreen()));
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.DeathInfo.ID, (payload, ctx) -> {
            ClientData.deathLost = payload.lostSalt();
            ClientData.deathOldLost = payload.oldLost();
            ClientData.deathOldAmount = payload.oldAmount();
            ClientData.deathSilver = payload.silverLost();
        });

        // Дополняем экран смерти: сколько соли потеряно и предупреждение о старой соли
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof DeathScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> DeathOverlay.render(ctx, scr));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (rollKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(new ModPayloads.Roll(
                            client.player.input.movementForward, client.player.input.movementSideways));
                }
            }
            while (useQuickKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(new ModPayloads.UseQuick(ClientData.selectedQuick));
                }
            }
            while (cycleQuickKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientData.selectedQuick = (ClientData.selectedQuick + 1) % QuickItem.values().length;
                }
            }
        });

        // Не даём замахнуться, если выносливость на нуле (сервер проверяет то же самое)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && !player.isCreative() && ClientData.stamina <= 0) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        HudRenderCallback.EVENT.register(SaltboundHud::render);
    }
}
