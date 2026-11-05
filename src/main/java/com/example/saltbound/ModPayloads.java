package com.example.saltbound;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Сетевые пакеты мода. */
public final class ModPayloads {
    private ModPayloads() {}

    /** Сервер -> клиент: статы, выносливость, фляга и серебро (для HUD и экрана святилища). */
    public record StatsSync(long salt, int vitality, int strength, int dexterity, int endurance,
                            float stamina, float maxStamina, int flaskCharges, int flaskMax,
                            long silver, int flaskChargeLvl, int flaskPotencyLvl) implements CustomPayload {
        public static final Id<StatsSync> ID = new Id<>(Saltbound.id("stats_sync"));
        public static final PacketCodec<RegistryByteBuf, StatsSync> CODEC = new PacketCodec<>() {
            @Override
            public StatsSync decode(RegistryByteBuf buf) {
                return new StatsSync(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, StatsSync v) {
                buf.writeVarLong(v.salt());
                buf.writeVarInt(v.vitality());
                buf.writeVarInt(v.strength());
                buf.writeVarInt(v.dexterity());
                buf.writeVarInt(v.endurance());
                buf.writeFloat(v.stamina());
                buf.writeFloat(v.maxStamina());
                buf.writeVarInt(v.flaskCharges());
                buf.writeVarInt(v.flaskMax());
                buf.writeVarLong(v.silver());
                buf.writeVarInt(v.flaskChargeLvl());
                buf.writeVarInt(v.flaskPotencyLvl());
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Сервер -> клиент: открыть экран святилища. */
    public record OpenSanctuary() implements CustomPayload {
        public static final Id<OpenSanctuary> ID = new Id<>(Saltbound.id("open_sanctuary"));
        public static final PacketCodec<RegistryByteBuf, OpenSanctuary> CODEC = PacketCodec.unit(new OpenSanctuary());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Клиент -> сервер: повысить атрибут (0..3) на 1 уровень. */
    public record LevelUp(int attr) implements CustomPayload {
        public static final Id<LevelUp> ID = new Id<>(Saltbound.id("level_up"));
        public static final PacketCodec<RegistryByteBuf, LevelUp> CODEC = new PacketCodec<>() {
            @Override
            public LevelUp decode(RegistryByteBuf buf) {
                return new LevelUp(buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, LevelUp v) {
                buf.writeVarInt(v.attr());
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Клиент -> сервер: перекат. forward/sideways берутся из ввода игрока. */
    public record Roll(float forward, float sideways) implements CustomPayload {
        public static final Id<Roll> ID = new Id<>(Saltbound.id("roll"));
        public static final PacketCodec<RegistryByteBuf, Roll> CODEC = new PacketCodec<>() {
            @Override
            public Roll decode(RegistryByteBuf buf) {
                return new Roll(buf.readFloat(), buf.readFloat());
            }

            @Override
            public void encode(RegistryByteBuf buf, Roll v) {
                buf.writeFloat(v.forward());
                buf.writeFloat(v.sideways());
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Сервер -> клиент: итоги смерти (для экрана смерти). */
    public record DeathInfo(long lostSalt, boolean oldLost, long oldAmount, long silverLost) implements CustomPayload {
        public static final Id<DeathInfo> ID = new Id<>(Saltbound.id("death_info"));
        public static final PacketCodec<RegistryByteBuf, DeathInfo> CODEC = new PacketCodec<>() {
            @Override
            public DeathInfo decode(RegistryByteBuf buf) {
                return new DeathInfo(buf.readVarLong(), buf.readBoolean(), buf.readVarLong(), buf.readVarLong());
            }

            @Override
            public void encode(RegistryByteBuf buf, DeathInfo v) {
                buf.writeVarLong(v.lostSalt());
                buf.writeBoolean(v.oldLost());
                buf.writeVarLong(v.oldAmount());
                buf.writeVarLong(v.silverLost());
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
