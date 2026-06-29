package me.otter.mint.mixin;

import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.HandTweaksExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public boolean swinging;

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void mint$noInterruptSwing(InteractionHand hand, boolean fromServerPlayer, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;

        HandTweaksExtension ext = FeatureManager.getExtension(HandTweaksExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return;
        if (!ext.noInterrupt.getValue()) return;

        if (this.swinging) {
            ci.cancel();
        }
    }
}
