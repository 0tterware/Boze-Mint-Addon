package me.otter.mint.mixin;

import me.otter.mint.client.core.FeatureManager;
import me.otter.mint.client.impl.extentions.ViewModelExtension;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void overrideHandFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) return;

        ViewModelExtension ext = FeatureManager.getExtension(ViewModelExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return;

        cir.setReturnValue(ext.handFov.getValue().floatValue());
    }
}
