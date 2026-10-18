package dev.packmixer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PackMixerMod implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.packmixer.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("packmixer", "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.consumeClick()) mc.setScreen(new MixerScreen(mc.screen));
            long d = Library.dirtyAt;
            if (d > 0 && System.currentTimeMillis() - d > 600) { Library.dirtyAt = 0; Library.compose(mc); }
        });
    }
}
