package com.example.chestesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
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

    private static void line(VertexConsumer v, MatrixStack.Entry e,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b) {
        float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4f) return;
        nx /= len; ny /= len; nz /= len;
        v.vertex(e, (float) x1, (float) y1, (float) z1).color(r, g, b, 1f).normal(e, nx, ny, nz).lineWidth(2f);
        v.vertex(e, (float) x2, (float) y2, (float) z2).color(r, g, b, 1f).normal(e, nx, ny, nz).lineWidth(2f);
    }

    private static void box(VertexConsumer v, MatrixStack.Entry e,
                            double x, double y, double z, float r, float g, float b) {
        double X = x + 1, Y = y + 1, Z = z + 1;
        // bottom
        line(v, e, x, y, z, X, y, z, r, g, b);
        line(v, e, X, y, z, X, y, Z, r, g, b);
        line(v, e, X, y, Z, x, y, Z, r, g, b);
        line(v, e, x, y, Z, x, y, z, r, g, b);
        // top
        line(v, e, x, Y, z, X, Y, z, r, g, b);
        line(v, e, X, Y, z, X, Y, Z, r, g, b);
        line(v, e, X, Y, Z, x, Y, Z, r, g, b);
        line(v, e, x, Y, Z, x, Y, z, r, g, b);
        // verticals
        line(v, e, x, y, z, x, Y, z, r, g, b);
        line(v, e, X, y, z, X, Y, z, r, g, b);
        line(v, e, X, y, Z, X, Y, Z, r, g, b);
        line(v, e, x, y, Z, x, Y, Z, r, g, b);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!espOn || mc.player == null || targets.isEmpty()) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES);

        float delta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d start = mc.player.getRotationVec(delta).multiply(0.5); // camera-relative

        MatrixStack.Entry entry = matrices.peek();

        for (Target t : targets) {
            float r = ((t.rgb() >> 16) & 0xFF) / 255f;
            float g = ((t.rgb() >> 8) & 0xFF) / 255f;
            float b = (t.rgb() & 0xFF) / 255f;

            double x = t.pos().getX() - cam.x;
            double y = t.pos().getY() - cam.y;
            double z = t.pos().getZ() - cam.z;

            box(lines, entry, x, y, z, r, g, b);

            if (tracersOn) {
                line(lines, entry, start.x, start.y, start.z, x + 0.5, y + 0.5, z + 0.5, r, g, b);
            }
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(EspRenderLayers.LINES);
        }
    }
}
