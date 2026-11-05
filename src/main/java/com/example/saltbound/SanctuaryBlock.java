package com.example.saltbound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Святилище: точка возрождения, отдых и прокачка за соль. */
public class SanctuaryBlock extends Block {
    public SanctuaryBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && player instanceof ServerPlayerEntity sp && world instanceof ServerWorld sw) {
            Saltbound.rest(sp, sw, pos);
        }
        return ActionResult.success(world.isClient());
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getY() + 1.05,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                0, 0.02, 0);
    }
}
