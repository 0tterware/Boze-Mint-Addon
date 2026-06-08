package me.otter.mint.client.impl.listeners;

import dev.boze.api.event.EventBind;
import dev.boze.api.utility.ChatHelper;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.OptionsExtension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

import static me.otter.mint.Mint.mc;

public class InstantPrefixListener {

    @EventHandler
    public void onBind(EventBind event) {
        if (event.isButton || event.action != GLFW.GLFW_PRESS) return;

        OptionsExtension opts = FeatureManager.getExtension(OptionsExtension.class);
        if (opts == null || !opts.instantPrefix.getValue()) return;

        final String prefix = ChatHelper.getCommandPrefix();
        if (prefix == null || prefix.length() != 1) return;

        final String keyName = GLFW.glfwGetKeyName(event.bind, 0);
        if (keyName == null || !keyName.equalsIgnoreCase(prefix)) return;

        if (mc.currentScreen == null) {
            event.setCancelled(true);
            mc.setScreen(new ChatScreen("", false));
        }
    }
}
