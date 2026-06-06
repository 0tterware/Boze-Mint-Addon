package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.option.SliderOption;
public class ViewModelExtension extends ClientModuleExtension {

    public final SliderOption handFov = new SliderOption(parent, "FOV", "FOV used for first-person hand rendering", 70.0, 30.0, 120.0, 1.0);

    public ViewModelExtension() {
        super(ModuleManager.getClientModule("ViewModel"));
    }
}
