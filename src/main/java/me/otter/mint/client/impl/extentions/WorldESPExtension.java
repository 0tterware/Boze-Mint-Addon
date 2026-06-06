package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.event.EventHudRender;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.SliderOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.Billboard;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.render.TextDrawer;
import dev.boze.api.render.TextType;
import dev.boze.api.utility.EntityHelper;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

import static me.otter.mint.Mint.mc;

public class WorldESPExtension extends ClientModuleExtension {

    private final ToggleOption parentTntTimers = new ToggleOption(parent, "FuseTimers", "Display Tnt fuses", false);
    private final ColorOption textColor = new ColorOption(parent, "Color", "Color of the TNT timer text", ColorMaker.staticColor(255, 255, 255), 0.2f, 0.8f, parentTntTimers);
    private final ToggleOption textShadow = new ToggleOption(parent, "Shadow", "Render shadow for text", false, parentTntTimers);
    private final SliderOption textScale = new SliderOption(parent, "Scale", "Base const scale multiplier for TNT timer text", 1.0, 0.1, 4.0, 0.05, parentTntTimers);
    private final SliderOption maxDistance = new SliderOption(parent, "MaxDistance", "Maximum distance to render TNT timers", 100.0, 0.0, 250.0, 1.0, parentTntTimers);
    private final SliderOption maxEntities = new SliderOption(parent, "MaxEntities", "Maximum number of TNT entities to render", 32.0, 0.0, 250.0, 1.0, parentTntTimers);
    private final SliderOption heightOffset = new SliderOption(parent, "HeightOffset", "Vertical Render offset for text", 1, 0, 3, 0.1, parentTntTimers);

    public WorldESPExtension() {
        super(ModuleManager.getClientModule("WorldESP"));
    }

    @EventHandler
    public void onHudRender(EventHudRender event) {
        if (mc.world == null || mc.player == null || !parentTntTimers.getValue()) {
            return;
        }

        renderTntTimers(event.context, event.tickDelta);
    }

    public void renderTntTimers(DrawContext drawContext, float tickDelta) {
        double maxDist = maxDistance.getValue();
        int maxCount = maxEntities.getValue().intValue();

        if (maxDist <= 0 || maxCount <= 0) {
            return;
        }

        Box searchBox = mc.player.getBoundingBox().expand(maxDist);

        List<TntEntity> tnts = mc.world.getEntitiesByClass(
                TntEntity.class,
                searchBox,
                tnt -> EntityHelper.isWithinRange(tnt, maxDist)
        );

        if (tnts.isEmpty()) return;

        tnts.sort(Comparator.comparingDouble(tnt -> EntityHelper.getDistance(mc.player, tnt)));

        if (tnts.size() > maxCount) {
            tnts = tnts.subList(0, maxCount);
        }

        for (TntEntity tnt : tnts) {
            renderSingleTnt(tnt, drawContext, tickDelta);
        }
    }

    private void renderSingleTnt(TntEntity tnt, DrawContext ctx, float tickDelta) {
        if (tnt.isRemoved()) return;

        if (!EntityHelper.isWithinRange(tnt, maxDistance.getValue())) {
            return;
        }

        Vec3d basePos = EntityHelper.getInterpolatedPos(tnt, tickDelta);
        Vec3d labelPos = basePos.add(0.0, heightOffset.getValue(), 0.0);

        int fuseTicks = tnt.getFuse();
        if (fuseTicks < 0) return;

        double seconds = fuseTicks / 20.0;
        int wholeSeconds = (int) seconds;
        int millis = (int) ((seconds - wholeSeconds) * 1000.0);

        String text = String.format("%02d:%02d", wholeSeconds, millis / 10);

        if (!Billboard.start(labelPos, ctx, textScale.getValue())) {
            return;
        }

        TextDrawer.start(TextType.HUD, 1.0);

        boolean shadow = textShadow.getValue();

        double width = TextDrawer.getWidth(text, shadow);
        double heightPx = TextDrawer.getHeight(shadow);

        double x = -width / 2.0;
        double y = -heightPx / 2.0;

        TextDrawer.render(text, x, y, textColor.getValue(), shadow);

        TextDrawer.draw(ctx);
        Billboard.stop(ctx);
    }
}