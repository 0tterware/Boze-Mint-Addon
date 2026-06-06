package me.otter.mint.client.core.utils;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.ColorHelper;

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
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int tr = clampColor(Math.round(red * this.tintR));
        int tg = clampColor(Math.round(green * this.tintG));
        int tb = clampColor(Math.round(blue * this.tintB));
        delegate.color(tr, tg, tb, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        int tr = clampColor(Math.round(ColorHelper.getRed(argb) * this.tintR));
        int tg = clampColor(Math.round(ColorHelper.getGreen(argb) * this.tintG));
        int tb = clampColor(Math.round(ColorHelper.getBlue(argb) * this.tintB));
        delegate.color(ColorHelper.getArgb(ColorHelper.getAlpha(argb), tr, tg, tb));
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        delegate.lineWidth(width);
        return this;
    }
}
