package com.example.saltbound.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Скрывает ванильные сердечки здоровья (вместо них своя полоса в SaltboundHud).
 * require = 0: если имя метода в другой версии отличается, мод не упадёт, просто сердца останутся.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void saltbound$hideHearts(CallbackInfo ci) {
        ci.cancel();
    }
}
