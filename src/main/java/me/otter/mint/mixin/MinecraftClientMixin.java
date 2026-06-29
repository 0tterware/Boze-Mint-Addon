package me.otter.mint.mixin;

import dev.boze.api.BozeInstance;
import me.otter.mint.Mint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;instance:Lnet/minecraft/client/Minecraft;"))
    private void onInit(GameConfig args, CallbackInfo ci) {
        BozeInstance.INSTANCE.registerAddon(Mint.INSTANCE);
        Mint.LOGGER.info("Registering {} {}", Mint.NAME, Mint.VERSION);
    }
}
