package ru.siyoga.legacyofthelucii.client.masquerade;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

final class MasqueradePlayerRenderEntity extends OtherClientPlayerEntity {
    private final Identifier skinTexture;
    private final String model;
    private final Text displayName;

    MasqueradePlayerRenderEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier resolvedSkin = DefaultSkinHelper.getTexture(profile.getId());
        String resolvedModel = DefaultSkinHelper.getModel(profile.getId());
        try {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                    client.getSkinProvider().getTextures(profile);
            MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
            if (skin != null) {
                resolvedSkin = client.getSkinProvider().loadSkin(skin, MinecraftProfileTexture.Type.SKIN);
                String metadataModel = skin.getMetadata("model");
                if ("slim".equals(metadataModel)) {
                    resolvedModel = "slim";
                }
            }
        } catch (RuntimeException ignored) {
            // Invalid or unavailable signed skin data falls back to the UUID-based skin.
        }
        skinTexture = resolvedSkin;
        model = resolvedModel;
        displayName = Text.literal(profile.getName());
    }

    @Override
    public boolean hasSkinTexture() {
        return true;
    }

    @Override
    public Identifier getSkinTexture() {
        return skinTexture;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Text getDisplayName() {
        return displayName;
    }
}
