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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestEspClient implements ClientModInitializer {
    private static final int FAST_SCAN_EVERY = 5;     // ticks: client block entities
    private static final int DEEP_SCAN_EVERY = 40;    // ticks: palette scan (client) + integrated server scan
    private static final int DEEP_CLIENT_RADIUS_CAP = 16; // palette scan is heavier, so capped
    private static final String[] MODES = {"BEFORE_TRANSLUCENT", "AFTER_ENTITIES", "BEFORE_DEBUG_RENDER", "END_MAIN"};

    private static boolean espOn = true;
    private static boolean tracersOn = true;
    private static boolean showSigns = true;
    private static boolean test = false;
    private static boolean debug = false;
    private static int mode = 0;
    private static long draws = 0;
    private static String lastError = "";
    private static int tick = 0;

    private static volatile List<Target> beTargets = List.of();
    private static volatile List<Target> stateTargets = List.of();
    private static volatile List<Target> serverTargets = List.of();
    private static volatile List<Target> targets = List.of();

    private static KeyMapping toggleEsp, toggleTracers, toggleSigns, cycleMode, testBox;

    @Override
    public void onInitializeClient() {
        toggleEsp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.toggle", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        toggleTracers = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.tracers", GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
        toggleSigns = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.signs", GLFW.GLFW_KEY_G, KeyMapping.Category.MISC));
        cycleMode = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.mode", GLFW.GLFW_KEY_L, KeyMapping.Category.MISC));
        testBox = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chestesp.test", GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            while (toggleEsp.consumeClick()) { espOn = !espOn; msg(mc, "Chest ESP: " + (espOn ? "ON" : "OFF")); }
            while (toggleTracers.consumeClick()) { tracersOn = !tracersOn; msg(mc, "Tracers: " + (tracersOn ? "ON" : "OFF")); }
            while (toggleSigns.consumeClick()) { showSigns = !showSigns; msg(mc, "Signs: " + (showSigns ? "ON" : "OFF")); }
            while (cycleMode.consumeClick()) { mode = (mode + 1) % MODES.length; msg(mc, "Draw event: " + MODES[mode]); }
            while (testBox.consumeClick()) { test = !test; msg(mc, "Test box: " + (test ? "ON" : "OFF")); }

            if (!espOn || mc.level == null || mc.player == null) return;

            int rd = Math.max(2, Math.min(32, mc.options.renderDistance().get()));

            if (tick % FAST_SCAN_EVERY == 0) {
                beTargets = TargetScanner.scanBlockEntities(mc, rd + 1);
                merge();
            }
            if (tick % DEEP_SCAN_EVERY == 0) {
                stateTargets = TargetScanner.scanBlockStates(mc, Math.min(rd + 1, DEEP_CLIENT_RADIUS_CAP));
                merge();
            }
            if (tick % DEEP_SCAN_EVERY == 20) {
                // singleplayer only: read the integrated server's loaded chunks directly
                TargetScanner.scanServerAsync(mc, Math.min(32, rd + 3), list -> {
                    serverTargets = list;
                    merge();
                });
            }

            if (debug && tick % 20 == 0) {
                msg(mc, "ChestESP | targets: " + targets.size() + " (client " + beTargets.size()
                        + ", palette " + stateTargets.size() + ", server " + serverTargets.size()
                        + ") | draws: " + draws + (lastError.isEmpty() ? "" : " | ERR: " + lastError));
            }
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(c -> render(c, 0));
        WorldRenderEvents.AFTER_ENTITIES.register(c -> render(c, 1));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(c -> render(c, 2));
        WorldRenderEvents.END_MAIN.register(c -> render(c, 3));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> EspRenderer.close());
    }

    /** Combine all sources, one entry per block position. */
    private static synchronized void merge() {
        Map<Long, Target> m = new HashMap<>();
        for (Target t : beTargets) m.putIfAbsent(t.pos().asLong(), t);
        for (Target t : stateTargets) m.putIfAbsent(t.pos().asLong(), t);
        for (Target t : serverTargets) m.putIfAbsent(t.pos().asLong(), t);
        targets = new ArrayList<>(m.values());
    }

    private static void msg(Minecraft mc, String s) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    private static void render(WorldRenderContext ctx, int which) {
        if (which != mode) return;
        Minecraft mc = Minecraft.getInstance();
        if (!espOn || mc.player == null) return;
        if (ctx.matrices() == null) { lastError = "matrices null in " + MODES[which]; return; }

        List<Target> list = new ArrayList<>();
        for (Target t : targets) {
            boolean isSign = t.rgb() == TargetScanner.SIGN || t.rgb() == TargetScanner.HANGING_SIGN;
            if (showSigns || !isSign) list.add(t);
        }

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
