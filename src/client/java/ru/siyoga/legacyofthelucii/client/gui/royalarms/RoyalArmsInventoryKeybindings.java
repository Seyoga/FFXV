package ru.siyoga.legacyofthelucii.client.gui.royalarms;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class RoyalArmsInventoryKeybindings {
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static KeyBinding openKey;

    private RoyalArmsInventoryKeybindings() {
    }

    public static void register() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.royal_arms.inventory",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(RoyalArmsInventoryKeybindings::tick);
    }

    private static void tick(MinecraftClient client) {
        while (openKey.wasPressed()) {
            if (client.player == null || client.currentScreen != null) {
                continue;
            }
            if (ClientLuciiState.legacy() == LuciiLegacy.NONE) {
                client.player.sendMessage(
                        Text.translatable("message.legacyofthelucii.skill_tree.requires_legacy"),
                        true
                );
                continue;
            }
            ClientPlayNetworking.send(
                    LegacyOfTheLucii.ROYAL_ARMS_INVENTORY_OPEN_PACKET,
                    PacketByteBufs.create()
            );
        }
    }
}
