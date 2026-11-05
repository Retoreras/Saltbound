package com.example.saltbound;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Убитые игроками мобы, ожидающие возрождения, и перезарядка святилищ.
 * Защита от фарма:
 *  1) у каждого святилища перезарядка (REST_RESPAWN_COOLDOWN);
 *  2) за раз возрождается не больше RESPAWN_MAX_PER_REST мобов, и не больше, чем нужно до лимита врагов рядом;
 *  3) "поколение": возрождённые мобы дают всё меньше соли (см. Saltbound.RESPAWN_SALT_DECAY).
 */
public class SlainMobs extends PersistentState {
    private static final int MAX_ENTRIES = 600;

    public record Entry(String type, BlockPos pos, int gen) {}

    private final List<Entry> list = new ArrayList<>();
    private final Map<Long, Long> lastReset = new HashMap<>();

    public static final PersistentState.Type<SlainMobs> TYPE =
            new PersistentState.Type<>(SlainMobs::new, SlainMobs::fromNbt, null);

    public static SlainMobs get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "saltbound_slain");
    }

    public void add(String type, BlockPos pos, int gen) {
        list.add(new Entry(type, pos.toImmutable(), gen));
        if (list.size() > MAX_ENTRIES) {
            list.remove(0);
        }
        markDirty();
    }

    /** Сколько тиков осталось до следующего возрождения мобов у этого святилища (0 = можно). */
    public long cooldownLeft(ServerWorld world, BlockPos sanctuary, int cooldown) {
        Long last = lastReset.get(sanctuary.asLong());
        if (last == null) {
            return 0;
        }
        return Math.max(0, Math.min(cooldown, last + cooldown - world.getTime()));
    }

    /** Возрождает убитых мобов рядом со святилищем. Возвращает число возрождённых. */
    public int respawnNear(ServerWorld world, BlockPos center, int radius) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return 0;
        }
        int nearby = world.getEntitiesByClass(HostileEntity.class, new Box(center).expand(radius), e -> true).size();
        int budget = Math.min(Saltbound.RESPAWN_MAX_PER_REST, Math.max(0, Saltbound.RESPAWN_MAX_HOSTILES_NEARBY - nearby));

        int count = 0;
        Iterator<Entry> it = list.iterator();
        while (it.hasNext() && count < budget) {
            Entry e = it.next();
            if (!e.pos().isWithinDistance(center, radius) || e.pos().isWithinDistance(center, 6)) {
                continue;
            }
            if (!world.isChunkLoaded(e.pos())) {
                continue;
            }
            Identifier id = Identifier.tryParse(e.type());
            Optional<EntityType<?>> type = id == null ? Optional.empty() : Registries.ENTITY_TYPE.getOrEmpty(id);
            it.remove();
            if (type.isEmpty()) {
                continue;
            }
            Entity spawned = type.get().spawn(world, e.pos(), SpawnReason.EVENT);
            if (spawned == null) {
                continue;
            }
            spawned.addCommandTag(Saltbound.GEN_TAG + e.gen());
            if (spawned instanceof MobEntity mob) {
                mob.setPersistent();
            }
            count++;
        }
        if (count > 0) {
            lastReset.put(center.asLong(), world.getTime());
        }
        markDirty();
        return count;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList out = new NbtList();
        for (Entry e : list) {
            NbtCompound c = new NbtCompound();
            c.putString("t", e.type());
            c.putInt("x", e.pos().getX());
            c.putInt("y", e.pos().getY());
            c.putInt("z", e.pos().getZ());
            c.putInt("g", e.gen());
            out.add(c);
        }
        nbt.put("entries", out);

        NbtList resets = new NbtList();
        for (Map.Entry<Long, Long> r : lastReset.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("p", r.getKey());
            c.putLong("t", r.getValue());
            resets.add(c);
        }
        nbt.put("resets", resets);
        return nbt;
    }

    public static SlainMobs fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        SlainMobs s = new SlainMobs();
        NbtList in = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < in.size(); i++) {
            NbtCompound c = in.getCompound(i);
            s.list.add(new Entry(c.getString("t"),
                    new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                    Math.max(1, c.getInt("g"))));
        }
        NbtList resets = nbt.getList("resets", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < resets.size(); i++) {
            NbtCompound c = resets.getCompound(i);
            s.lastReset.put(c.getLong("p"), c.getLong("t"));
        }
        return s;
    }
}
