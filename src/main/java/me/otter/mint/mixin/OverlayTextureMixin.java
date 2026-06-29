package me.otter.mint.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.boze.api.render.ClientColor;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {

    @Unique
    private static final int VANILLA_HURT = 0xB2FF0000;
    @Unique
    private static final int HURT_ALPHA = 0xB2;

    @Shadow
    @Final
    private DynamicTexture texture;

    @Unique
    private int applied = VANILLA_HURT;

    @Inject(method = "getTextureView", at = @At("HEAD"))
    private void mint$recolorHurt(CallbackInfoReturnable<GpuTextureView> cir) {
        WorldTweaksExtension ext = FeatureManager.getExtension(WorldTweaksExtension.class);

        int argb;
        if (ext != null && ext.parent != null && ext.parent.getState() && ext.damageTintToggle.getValue()) {
            ClientColor color = ext.damageTintColor.getValue().color;
            argb = (HURT_ALPHA << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        } else {
            argb = VANILLA_HURT;
        }

        if (argb == applied) return;

        NativeImage image = texture.getPixels();
        if (image == null) return;

        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 16; u++) {
                image.setPixel(u, v, argb);
            }
        }

        texture.upload();
        applied = argb;
    }
}
