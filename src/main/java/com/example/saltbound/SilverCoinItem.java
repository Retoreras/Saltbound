package com.example.saltbound;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Серебряная монета из сундуков. ПКМ: вся стопка переходит в серебро игрока. */
public class SilverCoinItem extends Item {
    public SilverCoinItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
            int n = stack.getCount();
            Saltbound.addSilver(sp, n);
            stack.decrement(n);
            sp.sendMessage(Text.translatable("message.saltbound.silver_added", n).formatted(Formatting.GRAY), true);
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
