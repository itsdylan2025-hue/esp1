package com.example.chestesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Finds storage blocks and signs. Client scan + (in singleplayer) integrated-server scan. */
public final class TargetScanner {
    public static final int CHEST         = 0x00FF00; // green
    public static final int TRAPPED       = 0xFF0000; // red
    public static final int ENDER_CHEST   = 0xFFFF00; // yellow
    public static final int BARREL        = 0x3399FF; // blue
    public static final int HOPPER        = 0xBBBBBB; // light gray
    public static final int SPAWNER       = 0xFF00FF; // magenta
    public static final int DISPENSER     = 0x00FFFF; // cyan (also droppers)
    public static final int COPPER_CHEST  = 0xFF7A00; // orange
    public static final int SIGN          = 0xFFFFFF; // white
    public static final int HANGING_SIGN  = 0x00FFAA; // mint

    private TargetScanner() {}

    /** Block -> color, built once from the registry (covers every wood/copper variant). */
    private static final class Holder {
        static final Map<Block, Integer> COLORS = build();

        private static Map<Block, Integer> build() {
            Map<Block, Integer> m = new HashMap<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                Integer c = colorForPath(BuiltInRegistries.BLOCK.getKey(b).getPath());
                if (c != null) m.put(b, c);
            }
            return m;
        }
    }

    private static Integer colorForPath(String p) {
        if (p.contains("copper_chest")) return COPPER_CHEST;
        if (p.equals("ender_chest"))    return ENDER_CHEST;
        if (p.equals("trapped_chest"))  return TRAPPED;
        if (p.equals("chest"))          return CHEST;
        if (p.equals("barrel"))         return BARREL;
        if (p.equals("hopper"))         return HOPPER;
        if (p.equals("spawner"))        return SPAWNER;
        if (p.equals("dispenser") || p.equals("dropper")) return DISPENSER;
        if (p.endsWith("hanging_sign")) return HANGING_SIGN; // oak_hanging_sign, oak_wall_hanging_sign...
        if (p.endsWith("_sign"))        return SIGN;         // oak_sign, oak_wall_sign...
        return null;
    }

    // ------------------------------------------------------------------ client side

    /** Fast: block entities of loaded client chunks. */
    public static List<Target> scanBlockEntities(Minecraft mc, int radius) {
        return scanClient(mc, radius, true, false);
    }

    /** Slower: looks at the block palette of every chunk section (catches missing block entities). */
    public static List<Target> scanBlockStates(Minecraft mc, int radius) {
        return scanClient(mc, radius, false, true);
    }

    private static List<Target> scanClient(Minecraft mc, int radius, boolean blockEntities, boolean blockStates) {
        Map<Long, Target> out = new HashMap<>();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return new ArrayList<>();

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int minSec = level.dimensionType().minY() >> 4;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(pcx + dx, pcz + dz);
                if (chunk == null || chunk instanceof EmptyLevelChunk) continue;
                collect(chunk, pcx + dx, pcz + dz, minSec, blockEntities, blockStates, out);
            }
        }
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------ singleplayer (integrated server)

    /** Runs on the server thread, then hands the result to {@code sink}. Does nothing in multiplayer. */
    public static void scanServerAsync(Minecraft mc, int radius, Consumer<List<Target>> sink) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || mc.player == null) return;
        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        final int pcx = mc.player.blockPosition().getX() >> 4;
        final int pcz = mc.player.blockPosition().getZ() >> 4;
        final int minSec = serverLevel.dimensionType().minY() >> 4;

        server.execute(() -> {
            try {
                Map<Long, Target> out = new HashMap<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                        if (chunk == null) continue; // not loaded on the server right now
                        collect(chunk, pcx + dx, pcz + dz, minSec, true, true, out);
                    }
                }
                sink.accept(new ArrayList<>(out.values()));
            } catch (Throwable ignored) {
                // never let a scan problem crash the server thread
            }
        });
    }

    // ------------------------------------------------------------------ shared

    private static void collect(LevelChunk chunk, int cx, int cz, int minSectionY,
                                boolean blockEntities, boolean blockStates, Map<Long, Target> out) {
        Map<Block, Integer> colors = Holder.COLORS;

        if (blockEntities) {
            for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                Integer c = colors.get(be.getBlockState().getBlock());
                if (c == null) continue;
                BlockPos pos = be.getBlockPos().immutable();
                out.putIfAbsent(pos.asLong(), new Target(pos, c));
            }
        }

        if (blockStates) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir()) continue;
                if (!section.maybeHas(st -> colors.containsKey(st.getBlock()))) continue;

                int baseY = (minSectionY + i) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState st = section.getBlockState(x, y, z);
                            Integer c = colors.get(st.getBlock());
                            if (c == null) continue;
                            BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                            out.putIfAbsent(pos.asLong(), new Target(pos, c));
                        }
                    }
                }
            }
        }
    }
}
