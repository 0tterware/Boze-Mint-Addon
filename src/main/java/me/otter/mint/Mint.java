package me.otter.mint;

import dev.boze.api.BozeInstance;
import dev.boze.api.addon.Addon;
import dev.boze.api.render.ClientColor;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.utility.cape.CapesManager;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.core.cape.CustomCapeSource;
import me.otter.mint.client.impl.listeners.InstantPrefixListener;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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

    public static final Mint INSTANCE = new Mint();
    public static Minecraft mc;

    public static final ClientColor CLIENT_COLOR = ColorMaker.staticColor(60, 170, 120);
    public static final float MAIN_FILL_OPACITY = 0.4f;
    public static final float MAIN_OUTLINE_OPACITY = 0.8f;

    public Mint() {
        super(ID, NAME, DESCRIPTION, VERSION);
    }

    @Override
    public boolean initialize() {
        LOGGER.info("Initializing {}", name);
        mc = Minecraft.getInstance();

        BozeInstance.INSTANCE.registerPackage("me.otter.mint");

        EVENT_BUS.registerLambdaFactory("me.otter.mint", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Register all features
        FeatureManager.registerFeatures();
        CapesManager.addSource(new CustomCapeSource(ID));

        // Always-active listeners under boze event bus
        BozeInstance.INSTANCE.subscribe(new InstantPrefixListener());
        
        // Maybe change later if launch failed
        LOGGER.info("Successfully initialized {}", name);
        return true;
    }
}
