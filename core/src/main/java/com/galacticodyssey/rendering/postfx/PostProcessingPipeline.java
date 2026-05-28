package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.GBuffer;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class PostProcessingPipeline implements Disposable {

    private final SSAOEffect ssao;
    private final SSREffect ssr;
    private final BloomEffect bloom;
    private final ToneMappingEffect toneMapping;
    private final FXAAEffect fxaa;
    private final FullscreenQuad quad;
    private final ShaderCache shaderCache;

    public PostProcessingPipeline(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.ssao = new SSAOEffect(shaderCache, quad, width, height);
        this.ssr = new SSREffect(shaderCache, quad, width, height);
        this.bloom = new BloomEffect(shaderCache, quad, width, height);
        this.toneMapping = new ToneMappingEffect(shaderCache, quad, width, height);
        this.fxaa = new FXAAEffect(shaderCache, quad, width, height);
    }

    public void applySSAO(GBuffer gBuffer, PerspectiveCamera camera) {
        ssao.apply(gBuffer.getNormalRoughnessAO(), gBuffer.getEmissive(), camera);
    }

    public Texture getSSAOTexture() { return ssao.getResult(); }

    public void apply(FrameBuffer hdrBuffer, GBuffer gBuffer, PerspectiveCamera camera) {
        Texture hdrTex = hdrBuffer.getColorBufferTexture();

        ssr.apply(hdrTex, gBuffer.getAlbedoMetallic(), gBuffer.getNormalRoughnessAO(),
                  gBuffer.getEmissive(), camera);
        hdrBuffer.begin();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShaderProgram blitShader = shaderCache.get("fullscreen.vert", "bloom_upsample.frag");
        blitShader.bind();
        ssr.getResult().bind(0);
        blitShader.setUniformi("u_inputTex", 0);
        blitShader.setUniformf("u_texelSize", 1f / ssr.getResult().getWidth(), 1f / ssr.getResult().getHeight());
        blitShader.setUniformf("u_intensity", 1.0f);
        quad.render(blitShader);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        hdrBuffer.end();

        bloom.apply(hdrBuffer.getColorBufferTexture());
        hdrBuffer.begin();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        blitShader.bind();
        bloom.getResult().bind(0);
        blitShader.setUniformi("u_inputTex", 0);
        blitShader.setUniformf("u_texelSize", 1f / bloom.getResult().getWidth(), 1f / bloom.getResult().getHeight());
        blitShader.setUniformf("u_intensity", bloom.intensity);
        quad.render(blitShader);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        hdrBuffer.end();

        toneMapping.apply(hdrBuffer.getColorBufferTexture());

        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        fxaa.apply(toneMapping.getResult());
    }

    public SSAOEffect getSSAO() { return ssao; }
    public BloomEffect getBloom() { return bloom; }
    public ToneMappingEffect getToneMapping() { return toneMapping; }

    public void resize(int width, int height) {
        ssao.resize(width, height);
        ssr.resize(width, height);
        bloom.resize(width, height);
        toneMapping.resize(width, height);
        fxaa.resize(width, height);
    }

    @Override
    public void dispose() {
        ssao.dispose();
        ssr.dispose();
        bloom.dispose();
        toneMapping.dispose();
        fxaa.dispose();
    }
}
