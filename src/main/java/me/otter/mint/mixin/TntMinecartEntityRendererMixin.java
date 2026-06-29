package me.otter.mint.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.boze.api.render.ClientColor;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(net.minecraft.client.renderer.entity.TntMinecartRenderer.class)
public abstract class TntMinecartEntityRendererMixin {

    @Inject(method = "submitWhiteSolidBlock", at = @At("HEAD"), cancellable = true)
    private static void replaceFlashTint(BlockModelRenderState blockModel, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, boolean white, int outlineColor, CallbackInfo ci) {

        WorldTweaksExtension ext = FeatureManager.getExtension(WorldTweaksExtension.class);
        if (ext == null || ext.parent == null || !ext.tntParentToggle.getValue() || !ext.parent.getState()) {
            return;
        }

        final int argb;
        if (white) {
            ClientColor color = ext.tntColor.getValue().color;
            argb = 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        } else {
            argb = -1; // unmodified white
        }

        BlockModelRenderStateAccessor acc = (BlockModelRenderStateAccessor) (Object) blockModel;
        List<BlockStateModelPart> parts = acc.mint$modelParts();
        RenderType renderType = acc.mint$renderType();
        if (parts == null || parts.isEmpty() || renderType == null) {
            return;
        }

        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            instance.setColor(argb);

            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    for (BakedQuad quad : part.getQuads(direction)) {
                        buffer.putBakedQuad(pose, quad, instance);
                    }
                }
                for (BakedQuad quad : part.getQuads(null)) {
                    buffer.putBakedQuad(pose, quad, instance);
                }
            }
        });

        ci.cancel();
    }
}
