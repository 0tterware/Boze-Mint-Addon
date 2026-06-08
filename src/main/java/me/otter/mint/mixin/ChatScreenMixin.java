package me.otter.mint.mixin;

import dev.boze.api.render.ClientColor;
import dev.boze.api.utility.ChatHelper;
import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.OptionsExtension;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    @Unique private float outlineAlpha = 0.0F;
    @Unique private int outlineBaseRgb = 0xFFFFFF;
    @Unique private static final float FADE_STEP = 0.05F;

    @Inject(method = "render", at = @At("TAIL"))
    private void drawCommandOutline(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OptionsExtension opts = FeatureManager.getExtension(OptionsExtension.class);

        boolean shouldHighlight = false;

        if (opts != null && opts.commandHighlight.getValue()) {
            String text = chatField.getText();
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

        int alpha = (int)(outlineAlpha * 255.0F) & 0xFF;
        int argb = (alpha << 24) | outlineBaseRgb;

        context.drawStrokedRectangle(
                chatField.getX() - 3,
                chatField.getY() - 3,
                chatField.getWidth() + 1,
                chatField.getHeight() + 1,
                argb
        );
    }
}
