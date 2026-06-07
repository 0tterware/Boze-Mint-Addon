package me.otter.mint.mixin;

import dev.boze.api.render.ClientColor;
import me.otter.mint.client.core.FeatureManager;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin {

    @ModifyArg(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/DynamicUniforms;write(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
        index = 1
    )
    private Vector4fc recolorGlint(Vector4fc colorModulator) {
        RenderLayer self = (RenderLayer) (Object) this;
        if (self != RenderLayers.glint() && self != RenderLayers.entityGlint()
            && self != RenderLayers.glintTranslucent() && self != RenderLayers.armorEntityGlint()) {
            return colorModulator;
        }

        WorldTweaksExtension ext = FeatureManager.getExtension(WorldTweaksExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState() || !ext.glintParentToggle.getValue()) {
            return colorModulator;
        }

        ClientColor color = ext.glintColor.getValue().color;
        return new Vector4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 1.0f);
    }
}
