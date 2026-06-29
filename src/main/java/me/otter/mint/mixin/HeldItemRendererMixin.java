package me.otter.mint.mixin;

import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.ViewModelExtension;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float overrideSwingProgress(float swingProgress, AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand) {
        ViewModelExtension ext = FeatureManager.getExtension(ViewModelExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return swingProgress;

        double offset = hand == InteractionHand.MAIN_HAND ? ext.mainSwing.getValue() : ext.offSwing.getValue();
        return swingProgress + (float) offset;
    }
}
