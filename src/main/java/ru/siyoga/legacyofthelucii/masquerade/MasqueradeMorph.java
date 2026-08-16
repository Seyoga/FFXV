package ru.siyoga.legacyofthelucii.masquerade;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MasqueradeMorph {
    private static final String KIND_KEY = "Kind";
    private static final String ENTITY_TYPE_KEY = "EntityType";
    private static final String PROFILE_KEY = "Profile";

    private final Kind kind;
    private final Identifier entityTypeId;
    private final GameProfile playerProfile;

    private MasqueradeMorph(Kind kind, Identifier entityTypeId, GameProfile playerProfile) {
        this.kind = kind;
        this.entityTypeId = entityTypeId;
        this.playerProfile = playerProfile;
    }

    public static MasqueradeMorph entity(EntityType<?> entityType) {
        return entity(Registries.ENTITY_TYPE.getId(entityType));
    }

    public static MasqueradeMorph entity(Identifier entityTypeId) {
        return new MasqueradeMorph(Kind.ENTITY, Objects.requireNonNull(entityTypeId), null);
    }

    public static MasqueradeMorph player(GameProfile profile) {
        Objects.requireNonNull(profile);
        return new MasqueradeMorph(Kind.PLAYER, null, copyProfile(profile));
    }

    public Kind kind() {
        return kind;
    }

    public Identifier entityTypeId() {
        return entityTypeId;
    }

    public GameProfile playerProfile() {
        return playerProfile == null ? null : copyProfile(playerProfile);
    }

    public String key() {
        if (kind == Kind.ENTITY) {
            return "entity:" + entityTypeId;
        }
        return "player:" + playerProfile.getId();
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(kind);
        if (kind == Kind.ENTITY) {
            buf.writeIdentifier(entityTypeId);
        } else {
            buf.writeGameProfile(playerProfile);
        }
    }

    public static MasqueradeMorph read(PacketByteBuf buf) {
        Kind kind = buf.readEnumConstant(Kind.class);
        return kind == Kind.ENTITY ? entity(buf.readIdentifier()) : player(buf.readGameProfile());
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KIND_KEY, kind.id);
        if (kind == Kind.ENTITY) {
            nbt.putString(ENTITY_TYPE_KEY, entityTypeId.toString());
        } else {
            nbt.put(PROFILE_KEY, NbtHelper.writeGameProfile(new NbtCompound(), playerProfile));
        }
        return nbt;
    }

    public static Optional<MasqueradeMorph> fromNbt(NbtCompound nbt) {
        Kind kind = Kind.byId(nbt.getString(KIND_KEY));
        if (kind == Kind.ENTITY) {
            Identifier id = Identifier.tryParse(nbt.getString(ENTITY_TYPE_KEY));
            return id == null ? Optional.empty() : Optional.of(entity(id));
        }
        if (kind == Kind.PLAYER && nbt.contains(PROFILE_KEY)) {
            GameProfile profile = NbtHelper.toGameProfile(nbt.getCompound(PROFILE_KEY));
            if (profile != null && profile.getId() != null) {
                return Optional.of(player(profile));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MasqueradeMorph morph && key().equals(morph.key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    private static GameProfile copyProfile(GameProfile source) {
        UUID uuid = source.getId();
        GameProfile copy = new GameProfile(uuid, source.getName());
        copy.getProperties().putAll(source.getProperties());
        return copy;
    }

    public enum Kind {
        ENTITY("entity"),
        PLAYER("player");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        private static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }
}
