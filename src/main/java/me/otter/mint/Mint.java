package me.otter.mint;

import dev.boze.api.BozeInstance;
import dev.boze.api.addon.Addon;
import dev.boze.api.render.ClientColor;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.utility.cape.CapesManager;
import me.otter.mint.client.core.FeatureManager;
import me.otter.mint.client.core.cape.CustomCapeSource;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

public class Mint extends Addon {
    public static final String NAME = "Mint";
    public static final String ID = NAME.toLowerCase();
    public static final String DESCRIPTION = FabricLoader.getInstance().getModContainer(ID).get().getMetadata().getDescription();
    public static final String VERSION = FabricLoader.getInstance().getModContainer(ID).get().getMetadata().getVersion().getFriendlyString();

    public static final Logger LOGGER = LogManager.getLogger();
    public static final IEventBus EVENT_BUS = new EventBus();

    public static Mint INSTANCE = new Mint();
    public static MinecraftClient mc;

    public static ClientColor CLIENT_COLOR = ColorMaker.staticColor(60, 170, 120);

    public Mint() {
        super(ID, NAME, DESCRIPTION, VERSION);
    }

    @Override
    public boolean initialize() {
        LOGGER.info("Initializing {}", name);
        mc = MinecraftClient.getInstance();

        BozeInstance.INSTANCE.registerPackage("me.otter.mint");

        EVENT_BUS.registerLambdaFactory("me.otter.mint", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Register all features
        FeatureManager.registerFeatures();
        CapesManager.addSource(new CustomCapeSource(ID));
        
        // Maybe change later if launch failed
        LOGGER.info("Successfully initialized {}", name);
        return true;
    }
}
