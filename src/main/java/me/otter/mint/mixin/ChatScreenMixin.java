package me.otter.mint.mixin;

import dev.boze.api.render.ClientColor;
import dev.boze.api.utility.ChatHelper;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.OptionsExtension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected EditBox input;

    @Unique private float outlineAlpha = 0.0F;
    @Unique private int outlineBaseRgb = 0xFFFFFF;
    @Unique private static final float FADE_STEP = 0.05F;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void drawCommandOutline(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OptionsExtension opts = FeatureManager.getExtension(OptionsExtension.class);

        boolean shouldHighlight = false;

        if (opts != null && opts.commandHighlight.getValue()) {
            String text = input.getValue();
            if (text != null && text.startsWith(ChatHelper.getCommandPrefix())) {
                shouldHighlight = true;

                ClientColor color = opts.commandHighlightColor.getValue().color;
                outlineBaseRgb = color.getPacked() & 0xFFFFFF;
            }
        }

        if (shouldHighlight) {
            outlineAlpha = Math.min(1.0F, outlineAlpha + FADE_STEP);
        } else {
            outlineAlpha = Math.max(0.0F, outlineAlpha - FADE_STEP);
        }

        if (outlineAlpha <= 0.01F) {
            return;
        }

        int alpha = (int) (outlineAlpha * 255.0F) & 0xFF;
        int argb = (alpha << 24) | outlineBaseRgb;

        int x = input.getX() - 3;
        int y = input.getY() - 3;
        int w = input.getWidth() + 1;
        int h = input.getHeight() + 1;
        int x2 = x + w;
        int y2 = y + h;

        context.fill(x, y, x2, y + 1, argb);
        context.fill(x, y2 - 1, x2, y2, argb);
        context.fill(x, y, x + 1, y2, argb);
        context.fill(x2 - 1, y, x2, y2, argb);
    }
}
