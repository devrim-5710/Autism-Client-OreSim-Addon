package com.theflex5710.oresim.render;

import autismclient.util.AutismBufferSource;
import autismclient.util.AutismWorldGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.theflex5710.oresim.modules.OreSimModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

public final class OreSimRenderer {
    private static float lineWidth = 1.5f;

    private OreSimRenderer() {
    }

    public static void setLineWidth(float width) {
        lineWidth = Math.max(0.5f, Math.min(6.0f, width));
    }

    public static void flush(Vec3 camera, Matrix4fc positionMatrix) {
        if (camera == null || positionMatrix == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) return;

        OreSimModule module = OreSimModule.active();
        if (module == null || !module.isEnabled()) return;

        int range = module.chunkRange();
        int centerX = mc.player.chunkPosition().x();
        int centerZ = mc.player.chunkPosition().z();

        List<double[]> pending = new ArrayList<>();
        module.forEachVisibleOre(centerX, centerZ, range,
            (x1, y1, z1, x2, y2, z2, color) -> pending.add(new double[] {
                x1 - camera.x, y1 - camera.y, z1 - camera.z,
                x2 - camera.x, y2 - camera.y, z2 - camera.z, color}));
        if (pending.isEmpty()) return;

        AutismBufferSource bufferSource = new AutismBufferSource();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(positionMatrix);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.tracerEspLines());

        for (double[] box : pending) {
            double x1 = box[0];
            double y1 = box[1];
            double z1 = box[2];
            double x2 = box[3];
            double y2 = box[4];
            double z2 = box[5];
            int color = (int) box[6];

            line(pose, buffer, x1, y1, z1, x2, y1, z1, color);
            line(pose, buffer, x2, y1, z1, x2, y1, z2, color);
            line(pose, buffer, x2, y1, z2, x1, y1, z2, color);
            line(pose, buffer, x1, y1, z2, x1, y1, z1, color);
            line(pose, buffer, x1, y2, z1, x2, y2, z1, color);
            line(pose, buffer, x2, y2, z1, x2, y2, z2, color);
            line(pose, buffer, x2, y2, z2, x1, y2, z2, color);
            line(pose, buffer, x1, y2, z2, x1, y2, z1, color);
            line(pose, buffer, x1, y1, z1, x1, y2, z1, color);
            line(pose, buffer, x2, y1, z1, x2, y2, z1, color);
            line(pose, buffer, x2, y1, z2, x2, y2, z2, color);
            line(pose, buffer, x1, y1, z2, x1, y2, z2, color);
        }
        bufferSource.uploadAndDraw();
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2, int color) {
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y2, z2, color, lineWidth);
    }
}
