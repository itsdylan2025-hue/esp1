package com.example.chestesp;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Reads block entities from chunks the client already has loaded. No network, no packets. */
public final class TargetScanner {
    public static final int CHEST        = 0x00FF00; // green
    public static final int TRAPPED      = 0xFF0000; // red
    public static final int ENDER_CHEST  = 0xFFFF00; // yellow
    public static final int BARREL       = 0x3399FF; // blue
    public static final int HOPPER       = 0xFFFFFF; // white
    public static final int SPAWNER      = 0xFF00FF; // magenta
    public static final int DISPENSER    = 0x00FFFF; // cyan (also droppers)
    public static final int COPPER_CHEST = 0xFF7A00; // orange

    private TargetScanner() {}

    public static List<Target> scan(MinecraftClient mc, int chunkRadius) {
        List<Target> out = new ArrayList<>();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return out;

        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                var chunk = world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (!(chunk instanceof WorldChunk wc)) continue;
                for (BlockEntity be : wc.getBlockEntities().values()) {
                    int color = colorFor(be);
                    if (color != -1) out.add(new Target(be.getPos().toImmutable(), color));
                }
            }
        }
        return out;
    }

    private static int colorFor(BlockEntity be) {
        String id = Registries.BLOCK.getId(be.getCachedState().getBlock()).getPath();
        if (id.contains("copper_chest")) return COPPER_CHEST;
        if (id.equals("ender_chest"))    return ENDER_CHEST;
        if (id.equals("trapped_chest"))  return TRAPPED;
        if (id.equals("chest"))          return CHEST;
        if (id.equals("barrel"))         return BARREL;
        if (id.equals("hopper"))         return HOPPER;
        if (id.equals("spawner"))        return SPAWNER;
        if (id.equals("dispenser") || id.equals("dropper")) return DISPENSER;
        return -1;
    }
}
