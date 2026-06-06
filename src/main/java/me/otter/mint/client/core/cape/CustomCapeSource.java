package me.otter.mint.client.core.cape;

import com.mojang.authlib.GameProfile;
import dev.boze.api.utility.cape.CapeLoadResult;
import dev.boze.api.utility.cape.CapeSource;
import me.otter.mint.Mint;
import net.minecraft.util.Identifier;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CustomCapeSource extends CapeSource {
    private static final Map<UUID, String> SPECIAL_CAPES = new HashMap<>();
    private static final String NORMAL_CAPE = "cape";
    private static final String DEV_CAPE = "cape_dev";
    private static final String BASE_URL = "https://0tterware.github.io/mint-capes/";

    static {
        // Otterware
        SPECIAL_CAPES.put(UUID.fromString("19eae5d1-fb1f-40f2-8188-3ce1fd3910fd"), DEV_CAPE);
    }

    public CustomCapeSource(String name) {
        super(name);
    }

    @Override
    public URL getUrl(GameProfile profile) {
        if (profile == null || profile.id() == null) return null;

        String capeName = SPECIAL_CAPES.getOrDefault(profile.id(), NORMAL_CAPE);
        String urlString = BASE_URL + capeName + ".png";

        Mint.LOGGER.debug("Attempting to load cape for {} from {}", profile.name(), urlString);

        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            Mint.LOGGER.error("Malformed URL for cape: {}", urlString, e);
            return null;
        }
    }

    @Override
    public void callback(GameProfile profile, CapeLoadResult result, Identifier identifier) {
        if (result == CapeLoadResult.Success) {
            Mint.LOGGER.info("Cape loaded for {}", profile != null ? profile.name() : "unknown");
        } else {
            Mint.LOGGER.warn("Failed to load cape for {}: {}", profile != null ? profile.name() : "unknown", result);
        }
    }
}