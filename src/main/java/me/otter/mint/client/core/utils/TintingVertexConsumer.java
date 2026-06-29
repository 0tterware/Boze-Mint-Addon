package me.otter.mint.client.core.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

public class TintingVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float tintR;
    private final float tintG;
    private final float tintB;

    public TintingVertexConsumer(VertexConsumer delegate, float tintR, float tintG, float tintB) {
        this.delegate = delegate;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
    }

    private static int clampColor(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        int tr = clampColor(Math.round(red * this.tintR));
        int tg = clampColor(Math.round(green * this.tintG));
        int tb = clampColor(Math.round(blue * this.tintB));
        delegate.setColor(tr, tg, tb, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        int tr = clampColor(Math.round(ARGB.red(argb) * this.tintR));
        int tg = clampColor(Math.round(ARGB.green(argb) * this.tintG));
        int tb = clampColor(Math.round(ARGB.blue(argb) * this.tintB));
        delegate.setColor(ARGB.color(ARGB.alpha(argb), tr, tg, tb));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
