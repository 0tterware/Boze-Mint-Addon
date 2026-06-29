package me.otter.mint.client.impl.hud;

import dev.boze.api.addon.AddonHudModule;
import dev.boze.api.option.ColorOption;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.render.HudLine;
import dev.boze.api.render.HudText;
import me.otter.mint.Mint;

import java.util.List;

public class WatermarkHudModule extends AddonHudModule {

    private final ColorOption color = new ColorOption(this, "Color", "Color of the name.", Mint.CLIENT_COLOR, 1.0f);
    private final ColorOption versionColor = new ColorOption(this, "VersionColor", "Color of the version.", ColorMaker.staticColor(170, 170, 170), 1.0f);

    public WatermarkHudModule() {
        super("MintWaterMark", "Displays the Mint watermark.");
    }

    @Override
    public List<HudLine> getLines() {
        return List.of(HudLine.of(
                new HudText(Mint.NAME, color.getValue().color),
                new HudText(Mint.VERSION, versionColor.getValue().color)));
    }
}
