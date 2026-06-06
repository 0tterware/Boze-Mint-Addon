package me.otter.mint.client.core;

import dev.boze.api.addon.AddonCommand;
import dev.boze.api.addon.AddonModule;
import dev.boze.api.client.module.ClientModuleExtension;
import me.otter.mint.Mint;
import me.otter.mint.client.impl.commands.CoinFlip;
import me.otter.mint.client.impl.commands.ModulesCommand;
import me.otter.mint.client.impl.extentions.WorldESPExtension;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import me.otter.mint.client.impl.modules.AutoTNT;
import me.otter.mint.client.impl.modules.AutoWither;

public class FeatureManager {

    public static void registerFeatures() {
        // Modules:
        register(new AutoTNT());
        register(new AutoWither());

        // Extensions:
        register(new WorldTweaksExtension());
        register(new WorldESPExtension());

        // Commands:
        register(new CoinFlip());
        register(new ModulesCommand());

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
