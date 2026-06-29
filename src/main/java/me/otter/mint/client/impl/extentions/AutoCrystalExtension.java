package me.otter.mint.client.impl.extentions;

import dev.boze.api.client.ModuleManager;
import dev.boze.api.client.module.ClientModuleExtension;
import dev.boze.api.client.module.helper.AutoCrystalHelper;
import dev.boze.api.event.EventWorldRender;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.Option;
import dev.boze.api.option.PageOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.WorldDrawer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static me.otter.mint.Mint.mc;

public class AutoCrystalExtension extends ClientModuleExtension {

    private ToggleOption targetRenderEnabled;
    private ToggleOption targetLineEnabled;

    private ColorOption autoCrystalColor;

    public AutoCrystalExtension() {
        super(ModuleManager.getClientModule("AutoCrystal"));

        Option<?> renderPage = null;
        for (Option<?> option : parent.getOptions()) {
            if (option instanceof PageOption && option.name.equalsIgnoreCase("Render")) {
                renderPage = option;
                break;
            }
        }

        if (renderPage != null) {
            targetRenderEnabled = new ToggleOption(parent, "TargetRender", "Render a box around AutoCrystal target.", true, renderPage);
            // targetLineEnabled = new ToggleOption(parent, "TargetLine", "Render a line from the AutoCrystal place position to the target.", false, renderPage);
        }

        for (Option<?> option : parent.getOptions()) {
            if (option instanceof ColorOption && option.name.equalsIgnoreCase("Color")) {
                autoCrystalColor = (ColorOption) option;
            }
        }
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        if (mc.player == null || mc.level == null) return;

        final LivingEntity target = AutoCrystalHelper.getTarget();
        if (target == null) return;

        final AABB targetBox = target.getBoundingBox();

        if (targetRenderEnabled != null && targetRenderEnabled.getValue()) {
            WorldDrawer.start();
            WorldDrawer.dynamicBox(autoCrystalColor.getValue(), false, targetBox, 0.0f);
            WorldDrawer.draw(event.matrices);
        }

        if (targetLineEnabled != null && targetLineEnabled.getValue()) {
            final BlockPos crystalPos = AutoCrystalHelper.getPos();
            if (crystalPos != null) {
                renderTargetLine(event, crystalPos, targetBox);
            }
        }
    }

    private void renderTargetLine(EventWorldRender event, BlockPos crystalPos, AABB targetBox) {
        final Vec3 start = new Vec3(crystalPos.getX() + 0.5, crystalPos.getY() + 1.0, crystalPos.getZ() + 0.5);
        final Vec3 end = new Vec3((targetBox.minX + targetBox.maxX) / 2.0, targetBox.minY, (targetBox.minZ + targetBox.maxZ) / 2.0);

        // TODO: complete onnce rrender method. fuck my keybboarrd is broken and double tying lletters
    }
}
