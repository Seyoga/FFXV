package ru.siyoga.legacyofthelucii.client.gui.skilltree;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SkillTreeKeybindings {
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static KeyBinding openSkillTreeKey;

    private SkillTreeKeybindings() {
    }

    public static void register() {
        openSkillTreeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.skill_tree.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(SkillTreeKeybindings::tick);
    }

    private static void tick(MinecraftClient client) {
        while (openSkillTreeKey.wasPressed()) {
            SkillTreeScreen.open(client);
        }
    }
}
