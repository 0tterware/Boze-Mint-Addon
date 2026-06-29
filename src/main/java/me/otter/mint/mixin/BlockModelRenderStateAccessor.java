package me.otter.mint.mixin;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BlockModelRenderState.class)
public interface BlockModelRenderStateAccessor {

    @Accessor("modelParts")
    List<BlockStateModelPart> mint$modelParts();

    @Accessor("renderType")
    RenderType mint$renderType();
}
