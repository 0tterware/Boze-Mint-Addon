package me.otter.mint.mixin;

import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.ViewModelExtension;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"
        ),
        index = 2
    )
    private float overrideHandFov(float fov) {
        ViewModelExtension ext = FeatureManager.getExtension(ViewModelExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return fov;

        return ext.handFov.getValue().floatValue();
    }
}
