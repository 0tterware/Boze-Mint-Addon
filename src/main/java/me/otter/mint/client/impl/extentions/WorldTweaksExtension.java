package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.ColorMaker;

public class WorldTweaksExtension extends ClientModuleExtension {

    public ToggleOption tntParentToggle = new ToggleOption(parent, "Tnt", "Tint Tnt", false);
    public ColorOption tntColor = new ColorOption(parent, "Color", "Tint color for Tnt", ColorMaker.staticColor(0, 150, 150), 0.2f, 0.6f, tntParentToggle);

    public WorldTweaksExtension() {
        super(ModuleManager.getClientModule("WorldTweaks"));
    }
}
