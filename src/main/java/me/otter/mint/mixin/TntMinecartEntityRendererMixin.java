package me.otter.mint.mixin;

import dev.boze.api.render.ClientColor;
import me.otter.mint.client.core.FeatureManager;
import me.otter.mint.client.core.utils.TintingVertexConsumer;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.TntMinecartEntityRenderer;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntMinecartEntityRenderer.class)
public abstract class TntMinecartEntityRendererMixin {

    @Inject(method = "renderFlashingBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IZI)V", at = @At("HEAD"), cancellable = true)
    private static void replaceFlashTint(BlockState state, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, boolean drawFlash, int outlineColor, CallbackInfo ci) {

        WorldTweaksExtension worldTweaksExtension = FeatureManager.getExtension(WorldTweaksExtension.class);

        if (worldTweaksExtension == null || !worldTweaksExtension.tntParentToggle.getValue() || !worldTweaksExtension.parent.getState()) {
            return;
        }

        final float r;
        final float g;
        final float b;
        if (drawFlash) {
            ClientColor color = worldTweaksExtension.tntColor.getValue().color;
            r = color.getRed() / 255.0f;
            g = color.getGreen() / 255.0f;
            b = color.getBlue() / 255.0f;
        } else {
            r = 1.0f;
            g = 1.0f;
            b = 1.0f;
        }

        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        BlockStateModel model = blockRenderManager.getModel(state);
        RenderLayer layer = BlockRenderLayers.getEntityBlockLayer(state);

        queue.submitCustom(matrices, layer, (entry, vertexConsumer) -> {
            VertexConsumer tinted = new TintingVertexConsumer(vertexConsumer, r, g, b);
            BlockModelRenderer.render(entry, tinted, model, 1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV);
        });

        ci.cancel();
    }
}
