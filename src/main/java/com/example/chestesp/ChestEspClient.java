package com.example.chestesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ChestEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 8;
    private static final int SCAN_EVERY_TICKS = 10;

    private static boolean espOn = true;
    private static boolean tracersOn = true;
    private static volatile List<Target> targets = List.of();
    private static int tick = 0;

    private static KeyBinding toggleEsp, toggleTracers;

    @Override
    public void onInitializeClient() {
        toggleEsp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestesp.toggle", GLFW.GLFW_KEY_K, KeyBinding.Category.MISC));
        toggleTracers = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestesp.tracers", GLFW.GLFW_KEY_J, KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleEsp.wasPressed()) {
                espOn = !espOn;
                msg(mc, "Chest ESP: " + (espOn ? "ON" : "OFF"));
            }
            while (toggleTracers.wasPressed()) {
                tracersOn = !tracersOn;
                msg(mc, "Tracers: " + (tracersOn ? "ON" : "OFF"));
            }
            if (espOn && mc.world != null && ++tick % SCAN_EVERY_TICKS == 0) {
                targets = TargetScanner.scan(mc, CHUNK_RADIUS);
            }
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ChestEspClient::render);
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!espOn || mc.player == null || targets.isEmpty()) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES);

        float delta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d look = mc.player.getRotationVec(delta);
        Vec3d start = look.multiply(0.5); // camera-relative, in front of crosshair

        MatrixStack.Entry entry = matrices.peek();

        for (Target t : targets) {
            float r = ((t.rgb() >> 16) & 0xFF) / 255f;
            float g = ((t.rgb() >> 8) & 0xFF) / 255f;
            float b = (t.rgb() & 0xFF) / 255f;

            double x = t.pos().getX() - cam.x;
            double y = t.pos().getY() - cam.y;
            double z = t.pos().getZ() - cam.z;

            VertexRendering.drawBox(matrices, lines, x, y, z, x + 1, y + 1, z + 1, r, g, b, 1f);

            if (tracersOn) {
                double ex = x + 0.5, ey = y + 0.5, ez = z + 0.5;
                float nx = (float) (ex - start.x), ny = (float) (ey - start.y), nz = (float) (ez - start.z);
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-4f) continue;
                nx /= len; ny /= len; nz /= len;
                lines.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
                        .color(r, g, b, 1f).normal(entry, nx, ny, nz).lineWidth(2f);
                lines.vertex(entry, (float) ex, (float) ey, (float) ez)
                        .color(r, g, b, 1f).normal(entry, nx, ny, nz).lineWidth(2f);
            }
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(EspRenderLayers.LINES);
        }
    }
}
