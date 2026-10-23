package com.example.chestesp;

import net.minecraft.core.BlockPos;

/** One highlighted block: where it is and what color it gets (0xRRGGBB). */
public record Target(BlockPos pos, int rgb) {}
