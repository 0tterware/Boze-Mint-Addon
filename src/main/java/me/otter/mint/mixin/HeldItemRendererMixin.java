package me.otter.mint.mixin;

import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.ViewModelExtension;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float overrideSwingProgress(float swingProgress, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand) {
        ViewModelExtension ext = FeatureManager.getExtension(ViewModelExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return swingProgress;

        double offset = hand == Hand.MAIN_HAND ? ext.mainSwing.getValue() : ext.offSwing.getValue();
        return swingProgress + (float) offset;
    }
}
