package com.theflex5710.oresim.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.theflex5710.oresim.render.OreSimRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class OreSimLevelRendererMixin {
    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("RETURN"))
    private void oresim$drawOreBoxes(GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter, boolean renderBlockOutline,
        CameraRenderState cameraState, Matrix4fc positionMatrix,
        GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
        boolean shouldRenderSky, CallbackInfo ci) {

        OreSimRenderer.flush(cameraState.pos, positionMatrix);
    }
}
