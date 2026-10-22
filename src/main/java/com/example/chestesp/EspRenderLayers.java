package com.example.chestesp;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

/** Line render layer with depth test OFF so boxes/tracers show through walls. */
public final class EspRenderLayers {
    private static final RenderPipeline LINES_NO_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("chestesp", "pipeline/lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());

    public static final RenderLayer LINES = RenderLayer.of(
            "chestesp_lines",
            RenderSetup.builder(LINES_NO_DEPTH).build());

    private EspRenderLayers() {}
}
