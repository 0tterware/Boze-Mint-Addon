package me.otter.mint.client.core;

import dev.boze.api.client.module.BaseModule;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.Option;
import dev.boze.api.option.SliderOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.PlaceRenderer;
import me.otter.mint.Mint;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class PlacementRenderGroup {

    public final ToggleOption enabled;
    public final ColorOption color;
    public final SliderOption duration;
    public final SliderOption fade;
    public final SliderOption grow;
    public final SliderOption shrink;
    public final ToggleOption shader;

    public PlacementRenderGroup(BaseModule owner) {
        this(owner, "Placements", null);
    }

    public PlacementRenderGroup(BaseModule owner, String name) {
        this(owner, name, null);
    }

    public PlacementRenderGroup(BaseModule owner, String name, Option<?> parent) {
        this.enabled = new ToggleOption(owner, name, "Render blocks as they are placed.", true, parent);
        this.color = new ColorOption(owner, "Color", "Color of the placement render.", Mint.CLIENT_COLOR, 0.25f, 1.0f, enabled);
        this.duration = new SliderOption(owner, "Duration", "How long each placement is shown.", 1000, 50, 5000, 50, enabled);
        this.fade = new SliderOption(owner, "Fade", "Portion of the duration spent fading out.", 1.0, 0.0, 1.0, 0.05, enabled);
        this.grow = new SliderOption(owner, "Grow", "Portion of the duration spent growing in.", 0.0, 0.0, 1.0, 0.05, enabled);
        this.shrink = new SliderOption(owner, "Shrink", "Portion of the duration spent shrinking out (0 = none).", 0.0, 0.0, 1.0, 0.05, enabled);
        this.shader = new ToggleOption(owner, "Shader", "Render through the shader instead of plain boxes.", false, enabled);
    }

    public void render(BlockPos pos) {
        if (pos == null || !enabled.getValue()) return;

        ColorOption.Value value = color.getValue();
        PlaceRenderer.addPlacement(new PlaceRenderer.PlacementRecord(
                pos,
                System.currentTimeMillis(),
                duration.getValue().longValue(),
                value.color,
                value.fillOpacity,
                value.outlineOpacity,
                fade.getValue().floatValue(),
                grow.getValue().floatValue(),
                shrink.getValue().floatValue(),
                shader.getValue()
        ));
    }

    public void render(BlockHitResult hit) {
        if (hit == null) return;
        render(PlaceRenderer.getRenderPos(hit));
    }
}
