package com.example.chestesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ChestEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 8;
    private static final int SCAN_EVERY_TICKS = 10;
    private static final String[] MODES = {"BEFORE_TRANSLUCENT", "AFTER_ENTITIES", "BEFORE_DEBUG_RENDER", "END_MAIN"};

    private static boolean espOn = true;
    private static boolean tracersOn = true;
    private static boolean test = true;   // magenta test box 4 blocks in front of you
    private static boolean debug = true;  // status text in the action bar
    private static int mode = 0;
    private static long draws = 0;
    private static String lastError = "";
    private static volatile List<Target> targets = List.of();
    private static int tick = 0;

    private static KeyMapping toggleEsp, toggleTracers, cycleMode, testBox;

    @Override
    public void onInitializeClient() {
        toggleEsp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.toggle", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        toggleTracers = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.tracers", GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
        cycleMode = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.mode", GLFW.GLFW_KEY_L, KeyMapping.Category.MISC));
        testBox = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.test", GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            while (toggleEsp.consumeClick()) { espOn = !espOn; msg(mc, "Chest ESP: " + (espOn ? "ON" : "OFF")); }
            while (toggleTracers.consumeClick()) { tracersOn = !tracersOn; msg(mc, "Tracers: " + (tracersOn ? "ON" : "OFF")); }
            while (cycleMode.consumeClick()) { mode = (mode + 1) % MODES.length; msg(mc, "Draw event: " + MODES[mode]); }
            while (testBox.consumeClick()) { test = !test; msg(mc, "Test box: " + (test ? "ON" : "OFF")); }

            if (espOn && mc.level != null && tick % SCAN_EVERY_TICKS == 0) {
                targets = TargetScanner.scan(mc, CHUNK_RADIUS);
            }
            if (debug && espOn && mc.player != null && tick % 20 == 0) {
                msg(mc, "ChestESP | targets: " + targets.size() + " | event: " + MODES[mode]
                        + " | draws: " + draws + (lastError.isEmpty() ? "" : " | ERR: " + lastError));
            }
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(c -> render(c, 0));
        WorldRenderEvents.AFTER_ENTITIES.register(c -> render(c, 1));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(c -> render(c, 2));
        WorldRenderEvents.END_MAIN.register(c -> render(c, 3));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> EspRenderer.close());
    }

    private static void msg(Minecraft mc, String s) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    private static void render(WorldRenderContext ctx, int which) {
        if (which != mode) return;
        Minecraft mc = Minecraft.getInstance();
        if (!espOn || mc.player == null) return;
        if (ctx.matrices() == null) { lastError = "matrices null in " + MODES[which]; return; }

        List<Target> list = new ArrayList<>(targets);
        Vec3 look = mc.player.getViewVector(1.0F);
        if (test) {
            Vec3 p = mc.player.getEyePosition().add(look.scale(4));
            list.add(new Target(BlockPos.containing(p.x, p.y, p.z), 0xFF00FF));
        }
        if (list.isEmpty()) return;

        try {
            EspRenderer.render(ctx, list, tracersOn, look);
            draws++;
            lastError = "";
        } catch (Throwable t) {
            lastError = t.toString();
        }
    }
}
