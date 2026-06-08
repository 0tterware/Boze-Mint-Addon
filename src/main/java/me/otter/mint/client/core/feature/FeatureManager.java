package me.otter.mint.client.core.feature;

import dev.boze.api.addon.AddonCommand;
import dev.boze.api.addon.AddonModule;
import dev.boze.api.client.module.ClientModuleExtension;
import me.otter.mint.Mint;
import me.otter.mint.client.impl.commands.CapeSourceCommand;
import me.otter.mint.client.impl.commands.CoinFlipCommand;
import me.otter.mint.client.impl.commands.ModulesCommand;
import me.otter.mint.client.impl.extentions.ViewModelExtension;
import me.otter.mint.client.impl.extentions.WorldESPExtension;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import me.otter.mint.client.impl.modules.AutoTNTModule;
import me.otter.mint.client.impl.modules.AutoWitherModule;

public class FeatureManager {

    public static void registerFeatures() {
        // Modules:
        register(new AutoTNTModule());
        register(new AutoWitherModule());

        // Extensions:
        register(new WorldTweaksExtension());
        register(new WorldESPExtension());
        register(new ViewModelExtension());

        // Commands:
        register(new CoinFlipCommand());
        register(new ModulesCommand());
        register(new CapeSourceCommand());

        //TODO: instantprefix when we have keyevents
    }

    public static void register(Object registrable) {
        switch (registrable) {
            case AddonCommand command -> Mint.INSTANCE.dispatcher.registerCommand(command);
            case AddonModule module -> Mint.INSTANCE.modules.add(module);
            case ClientModuleExtension extension -> Mint.INSTANCE.extensions.add(extension);
            case null -> Mint.LOGGER.warn("Cannot register null feature");
            default -> Mint.LOGGER.warn("Unsupported type for registration: {}", registrable.getClass().getName());
        }
    }

    private static <T> T findByType(Class<T> type, Iterable<?> source) {
        for (Object o : source) {
            if (type.isInstance(o)) {
                return type.cast(o);
            }
        }
        return null;
    }

    public static <T extends AddonModule> T getModule(Class<T> clazz) {
        return findByType(clazz, Mint.INSTANCE.modules);
    }

    public static <T extends ClientModuleExtension> T getExtension(Class<T> clazz) {
        return findByType(clazz, Mint.INSTANCE.extensions);
    }
}
