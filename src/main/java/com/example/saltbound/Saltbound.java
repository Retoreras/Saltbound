package com.example.saltbound;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Saltbound implements ModInitializer {
    public static final String MOD_ID = "saltbound";

    // ---- Баланс (меняй под себя) ----
    public static final float ATTACK_COST = 8f;
    public static final float ROLL_COST = 20f;
    public static final int REGEN_DELAY = 25;      // тики паузы перед восстановлением выносливости
    public static final int ROLL_IFRAMES = 10;     // тики неуязвимости при перекате
    public static final int ROLL_COOLDOWN = 12;
    public static final double SALT_MULTIPLIER = 1.0;          // общий множитель соли за мобов
    public static final boolean ALLOW_AIR_DASH = true;         // рывок в прыжке (один раз за полёт)
    public static final boolean OLD_STAIN_LOST_EVEN_IF_EMPTY = true; // true: любая смерть до сбора старой соли стирает её
    public static final double RESPAWN_SALT_DECAY = 0.6;       // соль с возрождённых мобов: x0.6, x0.36, x0.22 ... (минимум x0.1)
    public static final int REST_RESPAWN_COOLDOWN = 6000;      // перезарядка возрождения у святилища (тики; 6000 = 5 мин)
    public static final int RESPAWN_MAX_PER_REST = 20;         // максимум возрождённых мобов за один отдых
    public static final int RESPAWN_MAX_HOSTILES_NEARBY = 25;  // если рядом уже столько врагов, новые не появляются
    public static final String GEN_TAG = "saltbound_gen_";

    // ---- Голод и регенерация ----
    public static final boolean DISABLE_NATURAL_REGEN = true; // здоровье возвращают только фляга и святилище
    public static final float STAMINA_REGEN_FED = 1.25f;      // сытость >= 18: выносливость восстанавливается быстрее
    public static final float STAMINA_REGEN_HUNGRY = 0.5f;    // сытость <= 6: медленнее

    // ---- Фляга ----
    public static final int FLASK_MAX = 3;                // зарядов (позже: улучшения за серебро)
    public static final float FLASK_HEAL_MIN = 8f;        // минимум лечения за глоток (4 сердца)
    public static final float FLASK_HEAL_FRACTION = 0.4f; // или 40% макс. здоровья, что больше
    public static final int FLASK_DRINK_TICKS = 20;       // длительность питья (1 сек)

    // ---- Серебро ----
    public static final float SILVER_DROP_CHANCE = 0.15f;  // шанс серебра с обычного моба
    public static final int SILVER_DEATH_LOSS_PERCENT = 5; // % серебра, теряемый при смерти (без пятна)

    /** Постоянные данные игрока (сохраняются в файле игрока). */
    public static final AttachmentType<PlayerStats> STATS =
            AttachmentRegistry.createPersistent(id("stats"), PlayerStats.CODEC);

    /** Текущее число зарядов фляги (по умолчанию полная). */
    public static final AttachmentType<Integer> FLASK =
            AttachmentRegistry.createPersistent(id("flask"), Codec.INT);

    /** Серебро и улучшения фляги. */
    public static final AttachmentType<PlayerExtras> EXTRAS =
            AttachmentRegistry.createPersistent(id("extras"), PlayerExtras.CODEC);

    public static Block SANCTUARY_BLOCK;
    public static Item SANCTUARY_ITEM;
    public static Item SILVER_COIN;

    /** Временные данные (не сохраняются): выносливость, неуязвимость и т.д. */
    private static final class Runtime {
        float stamina = -1; // -1 = инициализировать полным значением
        int regenDelay;
        int iframes;
        int rollCooldown;
        BlockPos sanctuary;
        boolean airDashUsed;
        int drinkTicks;
    }

    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private static Runtime runtime(ServerPlayerEntity p) {
        return RUNTIME.computeIfAbsent(p.getUuid(), k -> new Runtime());
    }

    public static PlayerStats stats(ServerPlayerEntity p) {
        return p.getAttachedOrElse(STATS, PlayerStats.DEFAULT);
    }

    public static void setStats(ServerPlayerEntity p, PlayerStats s) {
        p.setAttached(STATS, s);
    }

    private static String dimId(ServerWorld w) {
        return w.getRegistryKey().getValue().toString();
    }

    @Override
    public void onInitialize() {
        // --- Блок и предмет ---
        SANCTUARY_BLOCK = Registry.register(Registries.BLOCK, id("sanctuary"),
                new SanctuaryBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.GRAY)
                        .strength(3.5f, 1200.0f)
                        .requiresTool()
                        .luminance(state -> 8)));
        SANCTUARY_ITEM = Registry.register(Registries.ITEM, id("sanctuary"),
                new BlockItem(SANCTUARY_BLOCK, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(SANCTUARY_ITEM));

        SILVER_COIN = Registry.register(Registries.ITEM, id("silver_coin"), new SilverCoinItem(new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e -> e.add(SILVER_COIN));

        // Серебряные монеты в сундуках структур (в ~50% сундуков 3-9 монет)
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.getValue().getPath().startsWith("chests/")) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(UniformLootNumberProvider.create(0f, 1f))
                        .with(ItemEntry.builder(SILVER_COIN)
                                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(3f, 9f)))));
            }
        });

        // --- Естественная регенерация от сытости выключается при запуске мира/сервера ---
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (DISABLE_NATURAL_REGEN) {
                server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
            }
        });

        // --- Сеть ---
        PayloadTypeRegistry.playS2C().register(ModPayloads.StatsSync.ID, ModPayloads.StatsSync.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.OpenSanctuary.ID, ModPayloads.OpenSanctuary.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.DeathInfo.ID, ModPayloads.DeathInfo.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.UseQuick.ID, ModPayloads.UseQuick.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.LevelUp.ID, ModPayloads.LevelUp.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.Roll.ID, ModPayloads.Roll.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.LevelUp.ID, (payload, ctx) -> handleLevelUp(ctx.player(), payload.attr()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.Roll.ID, (payload, ctx) -> handleRoll(ctx.player(), payload.forward(), payload.sideways()));
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.UseQuick.ID, (payload, ctx) -> handleUseQuick(ctx.player(), payload.index()));

        // --- Жизненный цикл игрока ---
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            applyAttributes(handler.player);
            sync(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNTIME.remove(handler.player.getUuid()));

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            newPlayer.setAttached(STATS, stats(oldPlayer));
            newPlayer.setAttached(FLASK, oldPlayer.getAttachedOrElse(FLASK, FLASK_MAX));
            newPlayer.setAttached(EXTRAS, extras(oldPlayer));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            applyAttributes(newPlayer);
            if (!alive) {
                newPlayer.setHealth(newPlayer.getMaxHealth());
            }
            runtime(newPlayer).stamina = -1;
            runtime(newPlayer).drinkTicks = 0;
            sync(newPlayer);
        });

        // --- Смерть: выдача соли и "пятно" ---
        ServerLivingEntityEvents.AFTER_DEATH.register(Saltbound::onDeath);

        // --- Выносливость: удары, неуязвимость при перекате ---
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || sp.isCreative() || sp.isSpectator()) {
                return ActionResult.PASS;
            }
            Runtime rt = runtime(sp);
            if (rt.drinkTicks > 0) {
                cancelDrinking(sp, rt);
            }
            if (rt.stamina < 0) {
                rt.stamina = stats(sp).maxStamina();
            }
            if (rt.stamina <= 0) {
                return ActionResult.FAIL;
            }
            rt.stamina = Math.max(0, rt.stamina - ATTACK_COST);
            rt.regenDelay = rt.stamina <= 0 ? REGEN_DELAY * 2 : REGEN_DELAY;
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                Runtime rt = RUNTIME.get(p.getUuid());
                if (rt != null) {
                    if (rt.iframes > 0) {
                        return false;
                    }
                    if (rt.drinkTicks > 0 && amount > 0) {
                        cancelDrinking(p, rt); // урон прерывает питьё
                    }
                }
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(server, p);
            }
        });
    }

    // ------------------------------------------------------------------ смерть / соль

    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player) {
            onPlayerDeath(player);
            return;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity killer) || !(entity instanceof Monster)) {
            return;
        }

        boolean boss = entity instanceof EnderDragonEntity || entity instanceof WitherEntity;
        int gen = respawnGeneration(entity);
        double mult = gen == 0 ? 1.0 : Math.max(0.1, Math.pow(RESPAWN_SALT_DECAY, gen));
        long base = SaltValues.roll(entity);
        long gain = Math.max(1, Math.round(base * mult * SALT_MULTIPLIER));
        long silver = rollSilver(entity, base, gen);

        PlayerStats s = stats(killer);
        setStats(killer, s.withSalt(s.salt() + gain));
        if (silver > 0) {
            PlayerExtras ex = extras(killer);
            setExtras(killer, ex.withSilver(ex.silver() + silver));
            killer.sendMessage(Text.translatable("message.saltbound.salt_silver_gain", gain, silver).formatted(Formatting.GOLD), true);
        } else {
            killer.sendMessage(Text.translatable("message.saltbound.salt_gain", gain).formatted(Formatting.GRAY), true);
        }
        sync(killer);

        if (!boss && entity.getWorld() instanceof ServerWorld sw) {
            SlainMobs.get(sw).add(Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
                    entity.getBlockPos(), Math.min(gen + 1, 10));
        }
    }

    /** 0 = естественный моб, 1+ = возрождённый святилищем (сколько раз). */
    private static int respawnGeneration(LivingEntity e) {
        for (String tag : e.getCommandTags()) {
            if (tag.startsWith(GEN_TAG)) {
                try {
                    return Integer.parseInt(tag.substring(GEN_TAG.length()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private static void onPlayerDeath(ServerPlayerEntity player) {
        runtime(player).drinkTicks = 0;
        PlayerStats s = stats(player);
        long lost = s.salt();

        // Серебро: теряется небольшой процент (округление вверх), без "пятна"
        PlayerExtras ex = extras(player);
        long silverLost = (ex.silver() * SILVER_DEATH_LOSS_PERCENT + 99) / 100;
        if (silverLost > 0) {
            setExtras(player, ex.withSilver(ex.silver() - silverLost));
        }

        // Старая соль навсегда пропадает, если не успели её забрать
        boolean oldLost = false;
        long oldAmount = 0;
        if (s.hasStain() && (lost > 0 || OLD_STAIN_LOST_EVEN_IF_EMPTY)) {
            oldLost = true;
            oldAmount = s.stainSalt();
            s = s.withoutStain();
        }
        // Новая соль остаётся на месте смерти
        if (lost > 0) {
            ServerWorld w = player.getServerWorld();
            BlockPos pos = player.getBlockPos();
            pos = new BlockPos(pos.getX(), Math.max(pos.getY(), w.getBottomY() + 1), pos.getZ());
            s = s.withStain(lost, dimId(w), pos).withSalt(0);
        }
        setStats(player, s);
        ServerPlayNetworking.send(player, new ModPayloads.DeathInfo(lost, oldLost, oldAmount, silverLost));
    }

    // ------------------------------------------------------------------ тик

    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity p) {
        if (p.isSpectator() || p.isDead()) {
            return;
        }
        Runtime rt = runtime(p);
        PlayerStats s = stats(p);
        float max = s.maxStamina();
        if (rt.stamina < 0 || rt.stamina > max) {
            rt.stamina = max;
        }
        if (rt.iframes > 0) rt.iframes--;
        if (rt.rollCooldown > 0) rt.rollCooldown--;
        if (p.isOnGround()) rt.airDashUsed = false;
        if (rt.drinkTicks > 0) {
            rt.drinkTicks--;
            if (rt.drinkTicks == 0) {
                finishDrinking(p);
            }
        }
        if (rt.regenDelay > 0) {
            rt.regenDelay--;
        } else if (rt.stamina < max) {
            rt.stamina = Math.min(max, rt.stamina + (0.5f + 0.03f * s.dexterity()) * hungerFactor(p));
        }

        int t = server.getTicks();
        if (s.hasStain()) {
            handleStain(p, s, t);
        }
        if (t % 4 == 0) {
            sync(p);
        }
    }

    /** Голод влияет на выносливость: сытый восстанавливает быстрее, голодный медленнее. */
    private static float hungerFactor(ServerPlayerEntity p) {
        int food = p.getHungerManager().getFoodLevel();
        if (food >= 18) return STAMINA_REGEN_FED;
        if (food <= 6) return STAMINA_REGEN_HUNGRY;
        return 1f;
    }

    private static void handleStain(ServerPlayerEntity p, PlayerStats s, int tick) {
        ServerWorld w = p.getServerWorld();
        if (!dimId(w).equals(s.stainDim())) {
            return;
        }
        BlockPos sp = s.stainPos();
        double cx = sp.getX() + 0.5, cy = sp.getY() + 0.5, cz = sp.getZ() + 0.5;

        if (tick % 10 == 0) {
            // Частицы видит только владелец
            w.spawnParticles(p, ParticleTypes.SOUL_FIRE_FLAME, true, cx, cy, cz, 8, 0.25, 0.6, 0.25, 0.01);
        }
        if (tick % 5 == 0 && p.squaredDistanceTo(cx, cy, cz) < 9.0) {
            long back = s.stainSalt();
            setStats(p, s.withSalt(s.salt() + back).withoutStain());
            p.sendMessage(Text.translatable("message.saltbound.salt_recovered", back).formatted(Formatting.GOLD), true);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 0.8f);
            sync(p);
        }
    }

    // ------------------------------------------------------------------ святилище

    public static void rest(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
        // Точка возрождения на блоке НАД святилищем (forced = true, чтобы не требовалась кровать)
        p.setSpawnPoint(w.getRegistryKey(), pos.up(), 0f, true, false);
        p.setHealth(p.getMaxHealth());
        p.getHungerManager().setFoodLevel(20);
        p.getHungerManager().setSaturationLevel(5f);

        Runtime rt = runtime(p);
        rt.stamina = stats(p).maxStamina();
        rt.sanctuary = pos.toImmutable();
        p.setAttached(FLASK, flaskMax(p)); // фляга пополняется только у святилища

        SlainMobs slain = SlainMobs.get(w);
        long left = slain.cooldownLeft(w, pos, REST_RESPAWN_COOLDOWN);
        Text msg;
        if (left > 0) {
            msg = Text.translatable("message.saltbound.rest_cooldown", (left + 1199) / 1200);
        } else {
            msg = Text.translatable("message.saltbound.rest", slain.respawnNear(w, pos, 48));
        }
        p.sendMessage(msg.copy().formatted(Formatting.AQUA), true);
        w.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.2f);

        sync(p);
        ServerPlayNetworking.send(p, new ModPayloads.OpenSanctuary());
    }

    private static void handleLevelUp(ServerPlayerEntity p, int attr) {
        if (attr < 0 || attr >= PlayerStats.ATTR_COUNT + 2) {
            return;
        }
        Runtime rt = runtime(p);
        // Антиобман: игрок должен находиться рядом с настоящим святилищем
        if (rt.sanctuary == null
                || !p.getServerWorld().getBlockState(rt.sanctuary).isOf(SANCTUARY_BLOCK)
                || p.squaredDistanceTo(Vec3d.ofCenter(rt.sanctuary)) > 100) {
            return;
        }
        if (attr >= PlayerStats.ATTR_COUNT) {
            handleFlaskUpgrade(p, attr - PlayerStats.ATTR_COUNT);
            return;
        }
        PlayerStats s = stats(p);
        long cost = s.levelUpCost();
        if (s.salt() < cost || s.attr(attr) >= PlayerStats.MAX_ATTR) {
            return;
        }
        setStats(p, s.withSalt(s.salt() - cost).withAttr(attr, s.attr(attr) + 1));
        applyAttributes(p);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.2f);
        sync(p);
    }

    // ------------------------------------------------------------------ перекат

    private static void handleRoll(ServerPlayerEntity p, float forward, float sideways) {
        if (p.isSpectator() || p.isDead() || p.hasVehicle() || p.isFallFlying()) {
            return;
        }
        Runtime rt = runtime(p);
        if (rt.rollCooldown > 0) {
            return;
        }
        boolean air = !p.isOnGround();
        if (air && (!ALLOW_AIR_DASH || rt.airDashUsed || p.isTouchingWater())) {
            return; // в воздухе рывок доступен один раз за прыжок
        }
        if (rt.stamina < 0) {
            rt.stamina = stats(p).maxStamina();
        }
        if (!p.isCreative() && rt.stamina < ROLL_COST) {
            return;
        }
        if (!Float.isFinite(forward) || !Float.isFinite(sideways)) {
            return;
        }
        float f = MathHelper.clamp(forward, -1f, 1f);
        float side = MathHelper.clamp(sideways, -1f, 1f);
        if (f == 0 && side == 0) {
            f = -1f; // без ввода = отскок назад
        }

        double yaw = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // вперёд
        double lx = Math.cos(yaw), lz = Math.sin(yaw);    // влево
        double dx = fx * f + lx * side;
        double dz = fz * f + lz * side;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            return;
        }
        dx /= len;
        dz /= len;

        if (rt.drinkTicks > 0) {
            cancelDrinking(p, rt); // перекат прерывает питьё
        }
        if (!p.isCreative()) {
            rt.stamina -= ROLL_COST;
        }
        rt.regenDelay = REGEN_DELAY;
        rt.rollCooldown = ROLL_COOLDOWN;
        rt.iframes = ROLL_IFRAMES;
        if (air) {
            rt.airDashUsed = true;
        }

        double power = air ? 0.9 : 1.0;
        p.setVelocity(dx * power, air ? 0.05 : 0.12, dz * power);
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------ серебро

    public static PlayerExtras extras(ServerPlayerEntity p) {
        return p.getAttachedOrElse(EXTRAS, PlayerExtras.DEFAULT);
    }

    public static void setExtras(ServerPlayerEntity p, PlayerExtras e) {
        p.setAttached(EXTRAS, e);
    }

    public static void addSilver(ServerPlayerEntity p, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerExtras e = extras(p);
        setExtras(p, e.withSilver(e.silver() + amount));
        sync(p);
    }

    /** Серебро с моба: боссы и мини-боссы дают гарантированно, остальные редко; возрождённые не дают. */
    private static long rollSilver(LivingEntity entity, long baseSalt, int gen) {
        if (gen > 0) {
            return 0;
        }
        if (baseSalt >= 100) {
            return baseSalt / 10;
        }
        if (entity.getRandom().nextFloat() < SILVER_DROP_CHANCE) {
            return Math.max(2, baseSalt / 3);
        }
        return 0;
    }

    /** kind: 0 = заряды фляги, 1 = сила лечения. Платится серебром. */
    private static void handleFlaskUpgrade(ServerPlayerEntity p, int kind) {
        PlayerExtras e = extras(p);
        if (kind == 0) {
            int lvl = e.flaskCharges();
            long cost = PlayerExtras.chargeCost(lvl);
            if (lvl >= PlayerExtras.MAX_CHARGE_LVL || cost < 0 || e.silver() < cost) {
                return;
            }
            e = e.withSilver(e.silver() - cost).withFlaskCharges(lvl + 1);
        } else if (kind == 1) {
            int lvl = e.flaskPotency();
            long cost = PlayerExtras.potencyCost(lvl);
            if (lvl >= PlayerExtras.MAX_POTENCY_LVL || cost < 0 || e.silver() < cost) {
                return;
            }
            e = e.withSilver(e.silver() - cost).withFlaskPotency(lvl + 1);
        } else {
            return;
        }
        setExtras(p, e);
        p.setAttached(FLASK, flaskMax(p)); // новое улучшение сразу наполняет флягу
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.5f);
        sync(p);
    }

    // ------------------------------------------------------------------ фляга

    public static int flaskMax(ServerPlayerEntity p) {
        return FLASK_MAX + extras(p).flaskCharges();
    }

    public static int flaskCharges(ServerPlayerEntity p) {
        return Math.max(0, Math.min(p.getAttachedOrElse(FLASK, FLASK_MAX), flaskMax(p)));
    }

    /** Использование предмета быстрого слота (клавиша R). */
    private static void handleUseQuick(ServerPlayerEntity p, int index) {
        if (index != QuickItem.RED_FLASK.ordinal()) {
            return;
        }
        if (p.isSpectator() || p.isDead() || p.hasVehicle()) {
            return;
        }
        Runtime rt = runtime(p);
        if (rt.drinkTicks > 0) {
            return;
        }
        if (flaskCharges(p) <= 0) {
            p.sendMessage(Text.translatable("message.saltbound.flask_empty").formatted(Formatting.GRAY), true);
            return;
        }
        if (p.getHealth() >= p.getMaxHealth()) {
            p.sendMessage(Text.translatable("message.saltbound.flask_full").formatted(Formatting.GRAY), true);
            return;
        }
        rt.drinkTicks = FLASK_DRINK_TICKS;
        setDrinkSlow(p, true);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /** Заряд тратится и лечение даётся в конце питья. Если питьё прервали, заряд не пропадает. */
    private static void finishDrinking(ServerPlayerEntity p) {
        setDrinkSlow(p, false);
        int charges = flaskCharges(p);
        if (charges <= 0 || p.isDead()) {
            return;
        }
        p.setAttached(FLASK, charges - 1);
        p.heal(Math.max(FLASK_HEAL_MIN, p.getMaxHealth() * PlayerExtras.healFraction(extras(p).flaskPotency())));
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 0.8f, 1.0f);
        w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.0, p.getZ(), 5, 0.3, 0.4, 0.3, 0.02);
        sync(p);
    }

    private static void cancelDrinking(ServerPlayerEntity p, Runtime rt) {
        rt.drinkTicks = 0;
        setDrinkSlow(p, false);
    }

    private static void setDrinkSlow(ServerPlayerEntity p, boolean on) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        Identifier modId = id("drinking");
        inst.removeModifier(modId);
        if (on) {
            inst.addTemporaryModifier(new EntityAttributeModifier(modId, -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ------------------------------------------------------------------ атрибуты и синхронизация

    public static void applyAttributes(ServerPlayerEntity p) {
        PlayerStats s = stats(p);
        setModifier(p, EntityAttributes.GENERIC_MAX_HEALTH, "vitality", s.vitality() * 2.0);       // +1 сердце за очко
        setModifier(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, "strength", s.strength() * 0.4);
        setModifier(p, EntityAttributes.GENERIC_ATTACK_SPEED, "dexterity", s.dexterity() * 0.04);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    private static void setModifier(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, String name, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) {
            return;
        }
        Identifier modId = id(name);
        inst.removeModifier(modId);
        if (value != 0) {
            inst.addPersistentModifier(new EntityAttributeModifier(modId, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void sync(ServerPlayerEntity p) {
        PlayerStats s = stats(p);
        Runtime rt = runtime(p);
        float max = s.maxStamina();
        float st = rt.stamina < 0 ? max : rt.stamina;
        ServerPlayNetworking.send(p, new ModPayloads.StatsSync(
                s.salt(), s.vitality(), s.strength(), s.dexterity(), s.endurance(), st, max,
                flaskCharges(p), flaskMax(p),
                extras(p).silver(), extras(p).flaskCharges(), extras(p).flaskPotency()));
    }
}
