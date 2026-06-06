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
    public final ToggleOption shader;
    public final SliderOption renderTicks;
    public final SliderOption animOpacity;
    public final SliderOption animGrow;
    public final SliderOption animShrink;

    public PlacementRenderGroup(BaseModule owner) {
        this(owner, "Render", null);
    }

    public PlacementRenderGroup(BaseModule owner, String name) {
        this(owner, name, null);
    }

    public PlacementRenderGroup(BaseModule owner, String name, Option<?> parent) {
        this.enabled = new ToggleOption(owner, name, "Render blocks as they are placed.", true, parent);
        this.color = new ColorOption(owner, "Color", "Color for placement render", Mint.CLIENT_COLOR, 0.25f, 1.0f, enabled);
        this.shader = new ToggleOption(owner, "Shader", "Render through the shader instead of plain boxes.", false, enabled);
        this.renderTicks = new SliderOption(owner, "RenderTicks", "Amount of ticks to render placement for", 20, 0, 20, 1, enabled);
        this.animOpacity = new SliderOption(owner, "AnimOpacity", "Opacity during animation (0 = no fade, 1 = fade over entire time)", 1.0, 0.0, 1.0, 0.05, enabled);
        this.animGrow = new SliderOption(owner, "AnimGrow", "Grow animation duration (starts at beginning)", 0.0, 0.0, 1.0, 0.05, enabled);
        this.animShrink = new SliderOption(owner, "AnimShrink", "Shrink animation duration (starts near the end)", 0.0, 0.0, 1.0, 0.05, enabled);
    }

    public void render(BlockPos pos) {
        if (pos == null || !enabled.getValue()) return;

        ColorOption.Value value = color.getValue();
        PlaceRenderer.addPlacement(new PlaceRenderer.PlacementRecord(
                pos,
                System.currentTimeMillis(),
                renderTicks.getValue().longValue() * 50L,
                value.color,
                value.fillOpacity,
                value.outlineOpacity,
                animOpacity.getValue().floatValue(),
                animGrow.getValue().floatValue(),
                animShrink.getValue().floatValue(),
                shader.getValue()
        ));
    }

    public void render(BlockHitResult hit) {
        if (hit == null) return;
        render(PlaceRenderer.getRenderPos(hit));
    }
}
