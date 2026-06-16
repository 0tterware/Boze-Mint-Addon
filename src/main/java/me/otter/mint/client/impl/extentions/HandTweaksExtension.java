package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.option.ToggleOption;

public class HandTweaksExtension extends ClientModuleExtension {

    public ToggleOption noInterrupt = new ToggleOption(parent, "NoInterrupt", "Don't restart the swing animation while one is already playing.", false);

    public HandTweaksExtension() {
        super(ModuleManager.getClientModule("HandTweaks"));
    }
}
