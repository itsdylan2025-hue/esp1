package com.example.chestesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

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

    public static List<Target> scan(Minecraft mc, int chunkRadius) {
        List<Target> out = new ArrayList<>();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return out;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    int color = colorFor(be);
                    if (color != -1) out.add(new Target(be.getBlockPos().immutable(), color));
                }
            }
        }
        return out;
    }

    private static int colorFor(BlockEntity be) {
        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
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
