package me.otter.mint.mixin;

import dev.boze.api.BozeInstance;
import me.otter.mint.Mint;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;instance:Lnet/minecraft/client/MinecraftClient;"))
    private void onInit(CallbackInfo ci) {
        BozeInstance.INSTANCE.registerAddon(Mint.INSTANCE);
        Mint.LOGGER.info("Registering " + Mint.NAME + " " + Mint.VERSION);
    }
}
