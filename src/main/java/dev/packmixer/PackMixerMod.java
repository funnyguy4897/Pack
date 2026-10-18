package dev.packmixer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PackMixerMod implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.packmixer.open", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K, KeyBinding.Category.create(Identifier.of("packmixer", "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.wasPressed()) mc.setScreen(new MixerScreen(mc.currentScreen));
            long d = Library.dirtyAt;
            if (d > 0 && System.currentTimeMillis() - d > 600) { Library.dirtyAt = 0; Library.compose(mc); }
        });
    }
}
