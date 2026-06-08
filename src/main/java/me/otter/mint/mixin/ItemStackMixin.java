package me.otter.mint.mixin;

import me.otter.mint.client.core.feature.FeatureManager;
import me.otter.mint.client.impl.extentions.WorldTweaksExtension;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void forceGlint(CallbackInfoReturnable<Boolean> cir) {
        if (((ItemStack) (Object) this).isEmpty()) return;

        WorldTweaksExtension ext = FeatureManager.getExtension(WorldTweaksExtension.class);
        if (ext == null || ext.parent == null || !ext.parent.getState()) return;
        if (!ext.glintParentToggle.getValue() || !ext.glintAlways.getValue()) return;

        cir.setReturnValue(true);
    }
}
