package com.galacticodyssey.rendering.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

public class ShaderCache implements Disposable {

    private static final String SHADER_BASE = "shaders";

    private final ObjectMap<String, ShaderProgram> cache = new ObjectMap<>();
    private final ObjectMap<String, ShaderProgram> lastWorking = new ObjectMap<>();

    public ShaderProgram get(String vertPath, String fragPath, String... defines) {
        String key = vertPath + "|" + fragPath + "|" + String.join(",", defines);
        ShaderProgram cached = cache.get(key);
        if (cached != null) return cached;

        ShaderProgram shader = compile(vertPath, fragPath, defines);
        cache.put(key, shader);
        lastWorking.put(key, shader);
        return shader;
    }

    public void reloadAll() {
        for (ObjectMap.Entry<String, ShaderProgram> entry : cache) {
            String[] parts = entry.key.split("\\|");
            String vertPath = parts[0];
            String fragPath = parts[1];
            String[] defines = parts.length > 2 && !parts[2].isEmpty()
                ? parts[2].split(",") : new String[0];
            try {
                ShaderProgram newShader = compile(vertPath, fragPath, defines);
                entry.value.dispose();
                cache.put(entry.key, newShader);
                lastWorking.put(entry.key, newShader);
                Gdx.app.log("ShaderCache", "Reloaded: " + entry.key);
            } catch (Exception e) {
                Gdx.app.error("ShaderCache", "Reload failed for " + entry.key
                    + ", keeping last working version: " + e.getMessage());
            }
        }
    }

    private ShaderProgram compile(String vertPath, String fragPath, String... defines) {
        String vertSource = ShaderUtils.loadShader(SHADER_BASE + "/" + vertPath);
        String fragSource = ShaderUtils.loadShader(SHADER_BASE + "/" + fragPath);
        vertSource = ShaderUtils.resolveIncludes(vertSource, SHADER_BASE);
        fragSource = ShaderUtils.resolveIncludes(fragSource, SHADER_BASE);
        if (defines.length > 0) {
            vertSource = ShaderUtils.prependDefines(vertSource, defines);
            fragSource = ShaderUtils.prependDefines(fragSource, defines);
        }
        ShaderProgram shader = new ShaderProgram(vertSource, fragSource);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Shader compilation failed ["
                + vertPath + " / " + fragPath + "]: " + log);
        }
        return shader;
    }

    @Override
    public void dispose() {
        for (ShaderProgram shader : cache.values()) {
            shader.dispose();
        }
        cache.clear();
        lastWorking.clear();
    }
}
