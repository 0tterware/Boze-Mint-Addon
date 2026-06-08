package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.ToggleOption;
import me.otter.mint.Mint;

public class OptionsExtension extends ClientModuleExtension {

    public ToggleOption instantPrefix = new ToggleOption(parent, "InstantPrefix", "Open chat when prefix is pressed", true);
    public ToggleOption commandHighlight = new ToggleOption(parent, "CmdHighlight", "Draw an outline in chat to signify command usage", false);
    public ColorOption commandHighlightColor = new ColorOption(parent, "Color", "What color to do", Mint.CLIENT_COLOR, 1, commandHighlight);

    public OptionsExtension() {
        super(ModuleManager.getClientModule("Options"));
    }

    // The behaviour driven by these options lives in always-active hosts, because the
    // Boze "Options" module is off by default and this extension is only subscribed to
    // the event bus while its parent module is enabled:
    //   - InstantPrefix / QuickClose -> InstantPrefixListener (subscribed on addon init)
    //   - CmdHighlight               -> ChatScreenMixin
}