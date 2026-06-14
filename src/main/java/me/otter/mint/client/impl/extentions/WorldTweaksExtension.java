package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.ColorMaker;

public class WorldTweaksExtension extends ClientModuleExtension {

    public ToggleOption tntParentToggle = new ToggleOption(parent, "Tnt", "Tint Tnt", false);
    public ColorOption tntColor = new ColorOption(parent, "Color", "Tint color for Tnt", ColorMaker.staticColor(0, 150, 150), 0.2f, 0.6f, tntParentToggle);

    public ToggleOption glintParentToggle = new ToggleOption(parent, "Enchant Glint", "Recolor the enchantment glint", false);
    public ColorOption glintColor = new ColorOption(parent, "Color", "Color of the enchantment glint", ColorMaker.staticColor(255, 50, 255), 0.2f, 0.6f, glintParentToggle);
    public ToggleOption glintAlways = new ToggleOption(parent, "Always", "Render the glint on all items, even unenchanted ones", false, glintParentToggle);

    public ToggleOption damageTintToggle = new ToggleOption(parent, "Damage Tint", "Recolor the red hurt flash on entities", false);
    public ColorOption damageTintColor = new ColorOption(parent, "Color", "Color of the entity hurt flash", ColorMaker.staticColor(255, 0, 0), 0.7f, damageTintToggle);

    public WorldTweaksExtension() {
        super(ModuleManager.getClientModule("WorldTweaks"));
    }
}
