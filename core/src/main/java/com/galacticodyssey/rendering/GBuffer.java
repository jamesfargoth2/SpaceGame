package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import java.nio.IntBuffer;
import com.badlogic.gdx.utils.BufferUtils;

public class GBuffer implements Disposable {

    public static final int COLOR_ATTACHMENT_COUNT = 3;
    public static final Integer RT0_FORMAT = GL30.GL_RGBA8;
    public static final Integer RT1_FORMAT = GL30.GL_RGBA16F;
    public static final Integer RT2_FORMAT = GL30.GL_RGBA16F;

    private final IntBuffer drawBuffers;
    private FrameBuffer fbo;
    private int width;
    private int height;

    public GBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.drawBuffers = BufferUtils.newIntBuffer(3);
        drawBuffers.put(GL30.GL_COLOR_ATTACHMENT0).put(GL30.GL_COLOR_ATTACHMENT1).put(GL30.GL_COLOR_ATTACHMENT2).flip();
        createFBO();
    }

    private void createFBO() {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_RGBA8, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        builder.addDepthRenderBuffer(GL30.GL_DEPTH24_STENCIL8);
        fbo = builder.build();

        for (Texture tex : fbo.getTextureAttachments()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        }
    }

    public void begin() {
        fbo.begin();
        Gdx.gl30.glDrawBuffers(3, drawBuffers);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        Gdx.gl.glStencilMask(0xFF);
    }

    public void end() {
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        fbo.end();
    }

    public Texture getAlbedoMetallic() { return fbo.getTextureAttachments().get(0); }
    public Texture getNormalRoughnessAO() { return fbo.getTextureAttachments().get(1); }
    public Texture getEmissive() { return fbo.getTextureAttachments().get(2); }

    public int getDepthStencilHandle() {
        return fbo.getDepthBufferHandle();
    }

    public FrameBuffer getFbo() { return fbo; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        fbo.dispose();
        createFBO();
    }

    @Override
    public void dispose() {
        fbo.dispose();
    }
}
