package com.example.chestesp;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws filled boxes + tracer ribbons through walls.
 * Based on the official Fabric 1.21.11 "Rendering in the World" example.
 */
public final class EspRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("chestesp", "pipeline/esp_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(262144);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    private static final float FILL_ALPHA = 0.40f;

    private EspRenderer() {}

    /** Extraction + drawing in one go. Positions are camera-relative for float precision. */
    public static void render(WorldRenderContext ctx, List<Target> targets, boolean tracers, Vec3 look) {
        if (targets.isEmpty()) return;

        PoseStack matrices = ctx.matrices();
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        Matrix4fc pose = matrices.last().pose();

        BufferBuilder buf = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());

        for (Target t : targets) {
            float r = ((t.rgb() >> 16) & 0xFF) / 255f;
            float g = ((t.rgb() >> 8) & 0xFF) / 255f;
            float b = (t.rgb() & 0xFF) / 255f;

            double x = t.pos().getX() - cam.x;
            double y = t.pos().getY() - cam.y;
            double z = t.pos().getZ() - cam.z;
            double e = 0.002;

            filledBox(buf, pose,
                    (float) (x - e), (float) (y - e), (float) (z - e),
                    (float) (x + 1 + e), (float) (y + 1 + e), (float) (z + 1 + e),
                    r, g, b, FILL_ALPHA);

            if (tracers) {
                ribbon(buf, pose, look.x, look.y, look.z, x + 0.5, y + 0.5, z + 0.5, r, g, b);
            }
        }

        draw(Minecraft.getInstance(), buf);
    }

    private static void v(BufferBuilder buf, Matrix4fc m, float x, float y, float z,
                          float r, float g, float b, float a) {
        buf.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    private static void ribbon(BufferBuilder buf, Matrix4fc m,
                               double sx, double sy, double sz,
                               double ex, double ey, double ez,
                               float r, float g, float b) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double mx = -(sx + ex) / 2, my = -(sy + ey) / 2, mz = -(sz + ez) / 2;
        double cx = dy * mz - dz * my;
        double cy = dz * mx - dx * mz;
        double cz = dx * my - dy * mx;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-9) return;
        cx /= len; cy /= len; cz /= len;

        double ws = 0.0035 * Math.sqrt(sx * sx + sy * sy + sz * sz);
        double we = 0.0035 * Math.sqrt(ex * ex + ey * ey + ez * ez);

        float a1x = (float) (sx - cx * ws), a1y = (float) (sy - cy * ws), a1z = (float) (sz - cz * ws);
        float a2x = (float) (sx + cx * ws), a2y = (float) (sy + cy * ws), a2z = (float) (sz + cz * ws);
        float b2x = (float) (ex + cx * we), b2y = (float) (ey + cy * we), b2z = (float) (ez + cz * we);
        float b1x = (float) (ex - cx * we), b1y = (float) (ey - cy * we), b1z = (float) (ez - cz * we);

        v(buf, m, a1x, a1y, a1z, r, g, b, 1f);
        v(buf, m, a2x, a2y, a2z, r, g, b, 1f);
        v(buf, m, b2x, b2y, b2z, r, g, b, 1f);
        v(buf, m, b1x, b1y, b1z, r, g, b, 1f);
        v(buf, m, b1x, b1y, b1z, r, g, b, 1f);
        v(buf, m, b2x, b2y, b2z, r, g, b, 1f);
        v(buf, m, a2x, a2y, a2z, r, g, b, 1f);
        v(buf, m, a1x, a1y, a1z, r, g, b, 1f);
    }

    private static void filledBox(BufferBuilder buf, Matrix4fc m,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float r, float g, float b, float a) {
        v(buf, m, minX, minY, maxZ, r, g, b, a);
        v(buf, m, maxX, minY, maxZ, r, g, b, a);
        v(buf, m, maxX, maxY, maxZ, r, g, b, a);
        v(buf, m, minX, maxY, maxZ, r, g, b, a);

        v(buf, m, maxX, minY, minZ, r, g, b, a);
        v(buf, m, minX, minY, minZ, r, g, b, a);
        v(buf, m, minX, maxY, minZ, r, g, b, a);
        v(buf, m, maxX, maxY, minZ, r, g, b, a);

        v(buf, m, minX, minY, minZ, r, g, b, a);
        v(buf, m, minX, minY, maxZ, r, g, b, a);
        v(buf, m, minX, maxY, maxZ, r, g, b, a);
        v(buf, m, minX, maxY, minZ, r, g, b, a);

        v(buf, m, maxX, minY, maxZ, r, g, b, a);
        v(buf, m, maxX, minY, minZ, r, g, b, a);
        v(buf, m, maxX, maxY, minZ, r, g, b, a);
        v(buf, m, maxX, maxY, maxZ, r, g, b, a);

        v(buf, m, minX, maxY, maxZ, r, g, b, a);
        v(buf, m, maxX, maxY, maxZ, r, g, b, a);
        v(buf, m, maxX, maxY, minZ, r, g, b, a);
        v(buf, m, minX, maxY, minZ, r, g, b, a);

        v(buf, m, minX, minY, minZ, r, g, b, a);
        v(buf, m, maxX, minY, minZ, r, g, b, a);
        v(buf, m, maxX, minY, maxZ, r, g, b, a);
        v(buf, m, minX, minY, maxZ, r, g, b, a);
    }

    private static void draw(Minecraft client, BufferBuilder buffer) {
        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "chestesp render",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
    }

    private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "chestesp vertex buffer",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }
        return vertexBuffer.currentBuffer();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
              }
