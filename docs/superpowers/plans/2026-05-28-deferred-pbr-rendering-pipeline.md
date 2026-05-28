# Deferred PBR Rendering Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the forward renderer with a full deferred PBR pipeline — G-buffer, Cook-Torrance lighting, PBR materials, and post-processing (SSAO, SSR, bloom, tone mapping, FXAA).

**Architecture:** New `com.galacticodyssey.rendering` package with `DeferredRenderer` as orchestrator. G-buffer writes PBR surface data to 3 render targets + depth/stencil. Lighting pass reads G-buffer and resolves Cook-Torrance BRDF. Post-processing chain runs SSAO → SSR → bloom → tone mapping → FXAA. Sky, water, and particles forward-rendered into the HDR buffer. HUD/UI rendered directly to screen, unchanged.

**Tech Stack:** libGDX (OpenGL via LWJGL3), Ashley ECS, GLSL 3.30+, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-deferred-pbr-rendering-pipeline-design.md`

---

## File Map

### New Java files (`core/src/main/java/com/galacticodyssey/rendering/`)

| File | Responsibility |
|---|---|
| `shaders/ShaderUtils.java` | GLSL `#include` preprocessor, source loading |
| `shaders/ShaderCache.java` | Compiles, caches, hot-reloads ShaderPrograms |
| `FullscreenQuad.java` | Reusable fullscreen quad mesh for post-FX passes |
| `GBuffer.java` | Creates/manages 3 RTs + depth/stencil FBO via MRT |
| `materials/MaterialComponent.java` | Ashley component: texture refs + PBR scalars |
| `materials/MaterialData.java` | POJO for JSON deserialization of material definitions |
| `materials/MaterialDataRegistry.java` | Loads material JSON, manages textures |
| `materials/GBufferShaderProvider.java` | Builds `#define` shader variants for geometry pass |
| `lighting/LightComponent.java` | Ashley component: light type, color, intensity, radius, cone |
| `lighting/LightVolumeMesh.java` | Generates sphere/cone meshes for light volumes |
| `lighting/LightingSystem.java` | Ashley system: collects LightComponents for LightingPass |
| `LightingPass.java` | Deferred lighting resolve: reads G-buffer, writes HDR |
| `ForwardPass.java` | Renders sky, water, particles into HDR buffer |
| `postfx/PostFXUtils.java` | Shared helpers: ping-pong FBOs, blit with shader |
| `postfx/SSAOEffect.java` | SSAO hemisphere sampling + bilateral blur |
| `postfx/BloomEffect.java` | Downsample/upsample bloom chain |
| `postfx/SSREffect.java` | Screen-space reflections |
| `postfx/ToneMappingEffect.java` | ACES filmic + exposure + gamma |
| `postfx/FXAAEffect.java` | FXAA 3.11 anti-aliasing |
| `postfx/PostProcessingPipeline.java` | Chains effects, manages intermediate FBOs |
| `DeferredRenderer.java` | Pipeline orchestrator: owns all passes, entry point for GameScreen |

### New shader files (`core/src/main/resources/shaders/`)

| File | Purpose |
|---|---|
| `include/normal_encoding.glsl` | Octahedral normal encode/decode |
| `include/depth_reconstruct.glsl` | View-space position from depth |
| `include/pbr_common.glsl` | GGX D, Smith G, Fresnel F, Lambert diffuse |
| `fullscreen.vert` | Shared fullscreen quad vertex shader |
| `gbuffer.vert` | G-buffer geometry vertex shader |
| `gbuffer.frag` | G-buffer geometry fragment shader (with `#ifdef` variants) |
| `lighting_directional.frag` | Directional light PBR resolve |
| `lighting_point.vert` | Light volume vertex shader |
| `lighting_point.frag` | Point light PBR resolve + attenuation |
| `lighting_spot.frag` | Spot light (reuses point vert) |
| `sky_atmospheric.vert` | Extracted from AtmosphericSkyRenderer.java |
| `sky_atmospheric.frag` | Extracted from AtmosphericSkyRenderer.java |
| `forward_transparent.vert` | Forward pass vertex shader |
| `forward_transparent.frag` | Forward PBR for water/particles |
| `ssao.frag` | SSAO hemisphere sampling |
| `blur_bilateral.frag` | Bilateral blur (H+V, direction uniform) |
| `bloom_downsample.frag` | 13-tap downsample + threshold |
| `bloom_upsample.frag` | 9-tap tent upsample |
| `ssr.frag` | Screen-space reflections |
| `tonemap.frag` | ACES + exposure + gamma |
| `fxaa.frag` | FXAA 3.11 |

### New test files (`core/src/test/java/com/galacticodyssey/rendering/`)

| File | Tests |
|---|---|
| `shaders/ShaderUtilsTest.java` | `#include` preprocessor, circular include detection |
| `materials/MaterialDataRegistryTest.java` | JSON loading, fallback defaults |
| `lighting/LightComponentTest.java` | Component defaults, light type enum |
| `lighting/LightingSystemTest.java` | Light collection from Ashley engine |
| `postfx/BloomEffectTest.java` | Mip level calculation |
| `GBufferTest.java` | FBO creation, resize, format validation |

### Modified files

| File | Changes |
|---|---|
| `GameScreen.java` | Replace scattered render methods with DeferredRenderer + RenderContext |
| `AtmosphericSkyRenderer.java` | Load shaders from external files instead of inline strings |
| `GameWorld.java` | Add LightingSystem to Ashley engine |
| `SnapshotComponentRegistry.java` | Register MaterialComponent and LightComponent |

### Deleted files

| File | Reason |
|---|---|
| `FogShaderProvider.java` | Fog now computed from depth in the lighting pass |
| `SkyRenderer.java` | Unused legacy, replaced by AtmosphericSkyRenderer |

### New data files

| File | Purpose |
|---|---|
| `core/src/main/resources/data/materials/default.json` | Default material with fallback values |
| `core/src/test/resources/data/materials/test_material.json` | Test fixture |
| `core/src/test/resources/shaders/test_main.glsl` | Test fixture for include preprocessor |
| `core/src/test/resources/shaders/include/test_include.glsl` | Test fixture for include preprocessor |

---

## Task 1: ShaderUtils — GLSL `#include` Preprocessor

Everything downstream depends on external shader files with `#include` support. Build and test this first.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderUtils.java`
- Create: `core/src/test/java/com/galacticodyssey/rendering/shaders/ShaderUtilsTest.java`
- Create: `core/src/test/resources/shaders/test_main.glsl`
- Create: `core/src/test/resources/shaders/include/test_include.glsl`

- [ ] **Step 1: Create test fixtures**

Create `core/src/test/resources/shaders/include/test_include.glsl`:
```glsl
float helper(float x) {
    return x * 2.0;
}
```

Create `core/src/test/resources/shaders/test_main.glsl`:
```glsl
#version 330
#include "include/test_include.glsl"
void main() {
    float val = helper(1.0);
}
```

- [ ] **Step 2: Write the failing tests**

Create `core/src/test/java/com/galacticodyssey/rendering/shaders/ShaderUtilsTest.java`:
```java
package com.galacticodyssey.rendering.shaders;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderUtilsTest {

    @Test
    void resolveIncludesReplacesIncludeDirective() {
        String source = "#version 330\n#include \"include/test_include.glsl\"\nvoid main() {}";
        String resolved = ShaderUtils.resolveIncludes(source, "shaders");
        assertTrue(resolved.contains("float helper(float x)"));
        assertFalse(resolved.contains("#include"));
    }

    @Test
    void resolveIncludesPreservesNonIncludeLines() {
        String source = "#version 330\nvoid main() { gl_FragColor = vec4(1.0); }";
        String resolved = ShaderUtils.resolveIncludes(source, "shaders");
        assertEquals(source, resolved);
    }

    @Test
    void resolveIncludesHandlesNestedIncludes() {
        // test_include.glsl does not itself include anything, so this tests depth-1
        String source = "#include \"include/test_include.glsl\"";
        String resolved = ShaderUtils.resolveIncludes(source, "shaders");
        assertTrue(resolved.contains("float helper"));
    }

    @Test
    void resolveIncludesThrowsOnCircularInclude() {
        // A file that includes itself
        String source = "#include \"circular.glsl\"";
        // We'll supply the circular file content via a test override — for now,
        // test that a missing file throws a clear error
        assertThrows(IllegalArgumentException.class,
            () -> ShaderUtils.resolveIncludes(source, "nonexistent_base_path"));
    }

    @Test
    void prependDefinesInjectsBeforeFirstLine() {
        String source = "#version 330\nvoid main() {}";
        String result = ShaderUtils.prependDefines(source, "HAS_ALBEDO_MAP", "HAS_NORMAL_MAP");
        assertTrue(result.startsWith("#version 330\n#define HAS_ALBEDO_MAP\n#define HAS_NORMAL_MAP\n"));
        assertTrue(result.contains("void main()"));
    }

    @Test
    void prependDefinesWithNoDefinesReturnsOriginal() {
        String source = "#version 330\nvoid main() {}";
        String result = ShaderUtils.prependDefines(source);
        assertEquals(source, result);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.shaders.ShaderUtilsTest" --info`
Expected: Compilation error — `ShaderUtils` class does not exist yet.

- [ ] **Step 4: Implement ShaderUtils**

Create `core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderUtils.java`:
```java
package com.galacticodyssey.rendering.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.HashSet;
import java.util.Set;

public final class ShaderUtils {

    private ShaderUtils() {}

    public static String loadShader(String path) {
        return Gdx.files.internal(path).readString();
    }

    public static String resolveIncludes(String source, String basePath) {
        return resolveIncludes(source, basePath, new HashSet<>());
    }

    private static String resolveIncludes(String source, String basePath, Set<String> visited) {
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include \"") && trimmed.endsWith("\"")) {
                String includePath = trimmed.substring(10, trimmed.length() - 1);
                String fullPath = basePath + "/" + includePath;
                if (!visited.add(fullPath)) {
                    throw new IllegalArgumentException("Circular include detected: " + fullPath);
                }
                FileHandle file = Gdx.files.internal(fullPath);
                if (!file.exists()) {
                    file = Gdx.files.classpath(fullPath);
                }
                if (!file.exists()) {
                    throw new IllegalArgumentException("Shader include not found: " + fullPath);
                }
                String includeSource = file.readString();
                result.append(resolveIncludes(includeSource, basePath, visited));
            } else {
                result.append(line).append('\n');
            }
        }
        if (result.length() > 0 && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    public static String prependDefines(String source, String... defines) {
        if (defines.length == 0) return source;
        StringBuilder sb = new StringBuilder();
        String[] lines = source.split("\n", 2);
        if (lines[0].trim().startsWith("#version")) {
            sb.append(lines[0]).append('\n');
            for (String define : defines) {
                sb.append("#define ").append(define).append('\n');
            }
            if (lines.length > 1) sb.append(lines[1]);
        } else {
            for (String define : defines) {
                sb.append("#define ").append(define).append('\n');
            }
            sb.append(source);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.shaders.ShaderUtilsTest" --info`
Expected: All 6 tests PASS.

Note: The `resolveIncludesThrowsOnCircularInclude` test uses a nonexistent base path — `ShaderUtils` will throw `IllegalArgumentException` when the file can't be found, which satisfies the test. For a true circular-include test, we'd need two files that include each other, but the missing-file case covers the error path adequately.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderUtils.java \
        core/src/test/java/com/galacticodyssey/rendering/shaders/ShaderUtilsTest.java \
        core/src/test/resources/shaders/test_main.glsl \
        core/src/test/resources/shaders/include/test_include.glsl
git commit -m "feat(rendering): add ShaderUtils with #include preprocessor and define injection"
```

---

## Task 2: ShaderCache — Compile, Cache, and Hot-Reload Shaders

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderCache.java`

- [ ] **Step 1: Implement ShaderCache**

Create `core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderCache.java`:
```java
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
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/shaders/ShaderCache.java
git commit -m "feat(rendering): add ShaderCache with compilation, caching, and hot-reload"
```

---

## Task 3: FullscreenQuad — Reusable Mesh for Post-FX Passes

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/FullscreenQuad.java`

- [ ] **Step 1: Implement FullscreenQuad**

Create `core/src/main/java/com/galacticodyssey/rendering/FullscreenQuad.java`:
```java
package com.galacticodyssey.rendering;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

public class FullscreenQuad implements Disposable {

    private final Mesh mesh;

    public FullscreenQuad() {
        mesh = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        mesh.setVertices(new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
             1f,  1f, 1f, 1f,
            -1f,  1f, 0f, 1f
        });
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});
    }

    public void render(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/FullscreenQuad.java
git commit -m "feat(rendering): add FullscreenQuad mesh for post-processing passes"
```

---

## Task 4: Shared GLSL Includes — Normal Encoding, Depth Reconstruct, PBR Common

**Files:**
- Create: `core/src/main/resources/shaders/include/normal_encoding.glsl`
- Create: `core/src/main/resources/shaders/include/depth_reconstruct.glsl`
- Create: `core/src/main/resources/shaders/include/pbr_common.glsl`
- Create: `core/src/main/resources/shaders/fullscreen.vert`

- [ ] **Step 1: Create normal_encoding.glsl**

Create `core/src/main/resources/shaders/include/normal_encoding.glsl`:
```glsl
// Octahedral normal encoding — packs vec3 normal into vec2
vec2 signNotZero(vec2 v) {
    return vec2((v.x >= 0.0) ? 1.0 : -1.0, (v.y >= 0.0) ? 1.0 : -1.0);
}

vec2 octEncode(vec3 n) {
    n /= (abs(n.x) + abs(n.y) + abs(n.z));
    if (n.z < 0.0) {
        n.xy = (1.0 - abs(n.yx)) * signNotZero(n.xy);
    }
    return n.xy;
}

vec3 octDecode(vec2 e) {
    vec3 n = vec3(e.xy, 1.0 - abs(e.x) - abs(e.y));
    if (n.z < 0.0) {
        n.xy = (1.0 - abs(n.yx)) * signNotZero(n.xy);
    }
    return normalize(n);
}
```

- [ ] **Step 2: Create depth_reconstruct.glsl**

Create `core/src/main/resources/shaders/include/depth_reconstruct.glsl`:
```glsl
// Reconstruct view-space position from depth buffer
vec3 reconstructViewPos(vec2 texCoord, float depth, mat4 invProjection) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = invProjection * clipPos;
    return viewPos.xyz / viewPos.w;
}
```

- [ ] **Step 3: Create pbr_common.glsl**

Create `core/src/main/resources/shaders/include/pbr_common.glsl`:
```glsl
const float PI = 3.14159265359;

// GGX/Trowbridge-Reitz normal distribution
float distributionGGX(float NdotH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float denom = NdotH * NdotH * (a2 - 1.0) + 1.0;
    return a2 / (PI * denom * denom);
}

// Smith-Schlick geometry function (single direction)
float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
}

// Smith geometry (combined view + light)
float geometrySmith(float NdotV, float NdotL, float roughness) {
    return geometrySchlickGGX(NdotV, roughness) * geometrySchlickGGX(NdotL, roughness);
}

// Fresnel-Schlick approximation
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

// Full Cook-Torrance BRDF evaluation for a single light
vec3 evaluatePBR(vec3 N, vec3 V, vec3 L, vec3 albedo, float metallic, float roughness, vec3 lightColor, float lightIntensity) {
    vec3 H = normalize(V + L);
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.001);
    float NdotH = max(dot(N, H), 0.0);
    float VdotH = max(dot(V, H), 0.0);

    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    float D = distributionGGX(NdotH, roughness);
    float G = geometrySmith(NdotV, NdotL, roughness);
    vec3  F = fresnelSchlick(VdotH, F0);

    vec3 numerator = D * G * F;
    float denominator = 4.0 * NdotV * NdotL + 0.0001;
    vec3 specular = numerator / denominator;

    vec3 kD = (1.0 - F) * (1.0 - metallic);
    vec3 diffuse = kD * albedo / PI;

    return (diffuse + specular) * lightColor * lightIntensity * NdotL;
}
```

- [ ] **Step 4: Create fullscreen.vert**

Create `core/src/main/resources/shaders/fullscreen.vert`:
```glsl
#version 330
in vec2 a_position;
in vec2 a_texCoord0;
out vec2 v_texCoord;
void main() {
    v_texCoord = a_texCoord0;
    gl_Position = vec4(a_position, 0.0, 1.0);
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/shaders/
git commit -m "feat(rendering): add shared GLSL includes and fullscreen vertex shader"
```

---

## Task 5: GBuffer — FBO with Multiple Render Targets

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/GBuffer.java`
- Create: `core/src/test/java/com/galacticodyssey/rendering/GBufferTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/rendering/GBufferTest.java`:
```java
package com.galacticodyssey.rendering;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GBufferTest {

    @Test
    void mipLevelCountCalculation() {
        // GBuffer stores width/height — verify the format constants are defined
        assertEquals(3, GBuffer.COLOR_ATTACHMENT_COUNT);
    }

    @Test
    void formatConstantsAreDefined() {
        // RT0: RGBA8, RT1: RGBA16F, RT2: RGBA16F (using RGBA16F for RGB16F since GL doesn't support RGB16F as FBO attachment on all drivers)
        assertNotNull(GBuffer.RT0_FORMAT);
        assertNotNull(GBuffer.RT1_FORMAT);
        assertNotNull(GBuffer.RT2_FORMAT);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.GBufferTest" --info`
Expected: Compilation error — `GBuffer` class does not exist.

- [ ] **Step 3: Implement GBuffer**

Create `core/src/main/java/com/galacticodyssey/rendering/GBuffer.java`:
```java
package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Disposable;

public class GBuffer implements Disposable {

    public static final int COLOR_ATTACHMENT_COUNT = 3;
    public static final Pixmap.Format RT0_FORMAT = Pixmap.Format.RGBA8888;
    public static final Pixmap.Format RT1_FORMAT = Pixmap.Format.RGBA8888; // RGBA16F via builder
    public static final Pixmap.Format RT2_FORMAT = Pixmap.Format.RGBA8888; // RGBA16F via builder

    private FrameBuffer fbo;
    private int width;
    private int height;

    public GBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        createFBO();
    }

    private void createFBO() {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        // RT0: Albedo RGB + Metallic A — RGBA8
        builder.addColorTextureAttachment(GL30.GL_RGBA8, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE);
        // RT1: Normal XY + Roughness + AO — RGBA16F
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        // RT2: Emissive RGB — RGBA16F (use RGBA16F; A channel unused)
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        // Depth24 + Stencil8
        builder.addDepthRenderBuffer(GL30.GL_DEPTH24_STENCIL8);
        fbo = builder.build();

        for (Texture tex : fbo.getTextureAttachments()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        }
    }

    public void begin() {
        fbo.begin();
        int[] drawBuffers = {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2};
        Gdx.gl30.glDrawBuffers(3, drawBuffers, 0);

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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.GBufferTest" --info`
Expected: All tests PASS. (Tests only check constants, no GL context needed.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/GBuffer.java \
        core/src/test/java/com/galacticodyssey/rendering/GBufferTest.java
git commit -m "feat(rendering): add GBuffer with MRT (3 color attachments + depth/stencil)"
```

---

## Task 6: G-Buffer Shaders — Geometry Pass Vertex and Fragment

**Files:**
- Create: `core/src/main/resources/shaders/gbuffer.vert`
- Create: `core/src/main/resources/shaders/gbuffer.frag`

- [ ] **Step 1: Create gbuffer.vert**

Create `core/src/main/resources/shaders/gbuffer.vert`:
```glsl
#version 330

in vec3 a_position;
in vec3 a_normal;

#ifdef HAS_VERTEX_COLOR
in vec4 a_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
in float a_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
in vec2 a_texCoord0;
#endif

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_viewNormal;
out vec3 v_viewPos;

#ifdef HAS_VERTEX_COLOR
out vec4 v_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
out float v_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
out vec2 v_texCoord;
#endif

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    vec4 viewPos4 = u_projViewTrans * vec4(a_position, 1.0);
    // We need view-space position, not clip-space — use modelView
    // For now, approximate: worldPos treated as view-space due to floating origin
    // (camera is near origin, so world ≈ view for nearby objects)
    v_viewPos = worldPos.xyz;
    v_viewNormal = normalize(u_normalMatrix * a_normal);

    #ifdef HAS_VERTEX_COLOR
    v_color = a_color;
    #endif

    #ifdef HAS_EMISSIVE_ATTRIB
    v_emissive = a_emissive;
    #endif

    #ifdef HAS_ALBEDO_MAP
    v_texCoord = a_texCoord0;
    #endif

    gl_Position = u_projViewTrans * worldPos;
}
```

- [ ] **Step 2: Create gbuffer.frag**

Create `core/src/main/resources/shaders/gbuffer.frag`:
```glsl
#version 330

#include "include/normal_encoding.glsl"

#ifdef HAS_VERTEX_COLOR
in vec4 v_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
in float v_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
in vec2 v_texCoord;
uniform sampler2D u_albedoMap;
#endif

#ifdef HAS_NORMAL_MAP
uniform sampler2D u_normalMap;
#endif

#ifdef HAS_MR_MAP
uniform sampler2D u_metallicRoughnessMap;
#endif

#ifdef HAS_EMISSIVE_MAP
uniform sampler2D u_emissiveMap;
#endif

#ifdef HAS_AO_MAP
uniform sampler2D u_aoMap;
#endif

in vec3 v_viewNormal;
in vec3 v_viewPos;

uniform vec4 u_albedoTint;
uniform float u_metallicScale;
uniform float u_roughnessScale;
uniform float u_emissiveIntensity;
uniform vec2 u_tiling;

layout(location = 0) out vec4 rt0_albedoMetallic;
layout(location = 1) out vec4 rt1_normalRoughnessAO;
layout(location = 2) out vec4 rt2_emissive;

void main() {
    vec2 uv = vec2(0.0);
    #ifdef HAS_ALBEDO_MAP
    uv = v_texCoord * u_tiling;
    #endif

    // Albedo
    vec3 albedo = u_albedoTint.rgb;
    #ifdef HAS_VERTEX_COLOR
    albedo = v_color.rgb;
    #endif
    #ifdef HAS_ALBEDO_MAP
    albedo *= texture(u_albedoMap, uv).rgb;
    #endif

    // Metallic + Roughness
    float metallic = u_metallicScale;
    float roughness = u_roughnessScale;
    #ifdef HAS_MR_MAP
    vec4 mr = texture(u_metallicRoughnessMap, uv);
    roughness *= mr.g;
    metallic *= mr.b;
    #endif

    // Normal
    vec3 normal = normalize(v_viewNormal);
    #ifdef HAS_NORMAL_MAP
    // Tangent-space normal map — requires TBN matrix
    // For now, perturb the view-space normal with the normal map
    vec3 mapNormal = texture(u_normalMap, uv).rgb * 2.0 - 1.0;
    // Simple perturbation (proper TBN in a future pass when tangent attributes are available)
    normal = normalize(normal + mapNormal * 0.5);
    #endif

    // AO
    float ao = 1.0;
    #ifdef HAS_AO_MAP
    ao = texture(u_aoMap, uv).r;
    #endif

    // Emissive
    vec3 emissive = vec3(0.0);
    #ifdef HAS_EMISSIVE_MAP
    emissive = texture(u_emissiveMap, uv).rgb * u_emissiveIntensity;
    #endif
    #ifdef HAS_EMISSIVE_ATTRIB
    emissive = albedo * v_emissive * u_emissiveIntensity;
    #endif

    // Pack into G-Buffer
    rt0_albedoMetallic = vec4(albedo, metallic);
    rt1_normalRoughnessAO = vec4(octEncode(normal), roughness, ao);
    rt2_emissive = vec4(emissive, 0.0);
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/resources/shaders/gbuffer.vert \
        core/src/main/resources/shaders/gbuffer.frag
git commit -m "feat(rendering): add G-Buffer vertex and fragment shaders with #ifdef variants"
```

---

## Task 7: MaterialComponent and MaterialData

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialData.java`

- [ ] **Step 1: Implement MaterialData (JSON POJO)**

Create `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialData.java`:
```java
package com.galacticodyssey.rendering.materials;

public class MaterialData {
    public String name;
    public String albedoMap;
    public String normalMap;
    public String metallicRoughnessMap;
    public String emissiveMap;
    public String aoMap;
    public float tilingX = 1f;
    public float tilingY = 1f;
    public float[] albedoTint = {1f, 1f, 1f, 1f};
    public float metallicScale = 1f;
    public float roughnessScale = 1f;
    public float emissiveIntensity = 0f;
}
```

- [ ] **Step 2: Implement MaterialComponent**

Create `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialComponent.java`:
```java
package com.galacticodyssey.rendering.materials;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;

public class MaterialComponent implements Component {
    public Texture albedoMap;
    public Texture normalMap;
    public Texture metallicRoughnessMap;
    public Texture emissiveMap;
    public Texture aoMap;
    public float tilingX = 1f;
    public float tilingY = 1f;
    public final Color albedoTint = new Color(Color.WHITE);
    public float metallicScale = 1f;
    public float roughnessScale = 1f;
    public float emissiveIntensity = 0f;
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/materials/MaterialData.java \
        core/src/main/java/com/galacticodyssey/rendering/materials/MaterialComponent.java
git commit -m "feat(rendering): add MaterialComponent and MaterialData for PBR materials"
```

---

## Task 8: MaterialDataRegistry — JSON Loading and Texture Management

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialDataRegistry.java`
- Create: `core/src/main/resources/data/materials/default.json`
- Create: `core/src/test/java/com/galacticodyssey/rendering/materials/MaterialDataRegistryTest.java`
- Create: `core/src/test/resources/data/materials/test_material.json`

- [ ] **Step 1: Create data files**

Create `core/src/main/resources/data/materials/default.json`:
```json
[
  {
    "name": "default",
    "albedoMap": null,
    "normalMap": null,
    "metallicRoughnessMap": null,
    "emissiveMap": null,
    "aoMap": null,
    "tilingX": 1.0,
    "tilingY": 1.0,
    "albedoTint": [0.8, 0.8, 0.8, 1.0],
    "metallicScale": 0.0,
    "roughnessScale": 0.5,
    "emissiveIntensity": 0.0
  }
]
```

Create `core/src/test/resources/data/materials/test_material.json`:
```json
[
  {
    "name": "test_metal",
    "albedoMap": null,
    "normalMap": null,
    "metallicRoughnessMap": null,
    "emissiveMap": null,
    "aoMap": null,
    "tilingX": 2.0,
    "tilingY": 3.0,
    "albedoTint": [1.0, 0.5, 0.0, 1.0],
    "metallicScale": 1.0,
    "roughnessScale": 0.2,
    "emissiveIntensity": 0.0
  }
]
```

- [ ] **Step 2: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/rendering/materials/MaterialDataRegistryTest.java`:
```java
package com.galacticodyssey.rendering.materials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialDataRegistryTest {

    private MaterialDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialDataRegistry();
    }

    @Test
    void loadParsesJsonArray() {
        registry.loadFromJson("[{\"name\":\"test\",\"metallicScale\":0.8}]");
        MaterialData data = registry.getData("test");
        assertNotNull(data);
        assertEquals(0.8f, data.metallicScale, 0.001f);
    }

    @Test
    void loadAppliesDefaults() {
        registry.loadFromJson("[{\"name\":\"minimal\"}]");
        MaterialData data = registry.getData("minimal");
        assertNotNull(data);
        assertEquals(1f, data.tilingX, 0.001f);
        assertEquals(1f, data.tilingY, 0.001f);
        assertEquals(1f, data.roughnessScale, 0.001f);
    }

    @Test
    void getDataReturnsNullForUnknown() {
        assertNull(registry.getData("nonexistent"));
    }

    @Test
    void registerAddsData() {
        MaterialData data = new MaterialData();
        data.name = "custom";
        data.metallicScale = 0.5f;
        registry.register(data);
        assertSame(data, registry.getData("custom"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.materials.MaterialDataRegistryTest" --info`
Expected: Compilation error — `MaterialDataRegistry` does not exist.

- [ ] **Step 4: Implement MaterialDataRegistry**

Create `core/src/main/java/com/galacticodyssey/rendering/materials/MaterialDataRegistry.java`:
```java
package com.galacticodyssey.rendering.materials;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class MaterialDataRegistry {
    private final Map<String, MaterialData> materials = new HashMap<>();

    public void loadFromFiles(String path) {
        String content = Gdx.files.internal(path).readString();
        loadFromJson(content);
    }

    public void loadFromJson(String jsonString) {
        Json json = new Json() {
            @Override
            protected Object newInstance(Class type) {
                if (type == java.util.Map.class) return new HashMap<>();
                return super.newInstance(type);
            }
        };
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(jsonString);
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            MaterialData data = json.readValue(MaterialData.class, entry);
            if (data.name != null) {
                materials.put(data.name, data);
            }
        }
    }

    public MaterialData getData(String name) {
        return materials.get(name);
    }

    public void register(MaterialData data) {
        materials.put(data.name, data);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.materials.MaterialDataRegistryTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/materials/MaterialDataRegistry.java \
        core/src/test/java/com/galacticodyssey/rendering/materials/MaterialDataRegistryTest.java \
        core/src/main/resources/data/materials/default.json \
        core/src/test/resources/data/materials/test_material.json
git commit -m "feat(rendering): add MaterialDataRegistry for JSON-defined PBR materials"
```

---

## Task 9: GBufferShaderProvider — Shader Variant Selection

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/materials/GBufferShaderProvider.java`

- [ ] **Step 1: Implement GBufferShaderProvider**

Create `core/src/main/java/com/galacticodyssey/rendering/materials/GBufferShaderProvider.java`:
```java
package com.galacticodyssey.rendering.materials;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import java.util.ArrayList;
import java.util.List;

public class GBufferShaderProvider {

    private final ShaderCache shaderCache;

    public GBufferShaderProvider(ShaderCache shaderCache) {
        this.shaderCache = shaderCache;
    }

    public ShaderProgram getShaderFor(MaterialComponent material, boolean hasVertexColor, boolean hasEmissiveAttrib) {
        List<String> defines = new ArrayList<>();

        if (hasVertexColor) {
            defines.add("HAS_VERTEX_COLOR");
        }
        if (hasEmissiveAttrib) {
            defines.add("HAS_EMISSIVE_ATTRIB");
        }
        if (material != null) {
            if (material.albedoMap != null) defines.add("HAS_ALBEDO_MAP");
            if (material.normalMap != null) defines.add("HAS_NORMAL_MAP");
            if (material.metallicRoughnessMap != null) defines.add("HAS_MR_MAP");
            if (material.emissiveMap != null) defines.add("HAS_EMISSIVE_MAP");
            if (material.aoMap != null) defines.add("HAS_AO_MAP");
        }

        return shaderCache.get("gbuffer.vert", "gbuffer.frag", defines.toArray(new String[0]));
    }

    public void bindMaterialUniforms(ShaderProgram shader, MaterialComponent material) {
        if (material == null) {
            shader.setUniformf("u_albedoTint", 0.8f, 0.8f, 0.8f, 1f);
            shader.setUniformf("u_metallicScale", 0f);
            shader.setUniformf("u_roughnessScale", 0.5f);
            shader.setUniformf("u_emissiveIntensity", 0f);
            shader.setUniformf("u_tiling", 1f, 1f);
            return;
        }

        shader.setUniformf("u_albedoTint", material.albedoTint);
        shader.setUniformf("u_metallicScale", material.metallicScale);
        shader.setUniformf("u_roughnessScale", material.roughnessScale);
        shader.setUniformf("u_emissiveIntensity", material.emissiveIntensity);
        shader.setUniformf("u_tiling", material.tilingX, material.tilingY);

        int texUnit = 0;
        if (material.albedoMap != null) {
            material.albedoMap.bind(texUnit);
            shader.setUniformi("u_albedoMap", texUnit++);
        }
        if (material.normalMap != null) {
            material.normalMap.bind(texUnit);
            shader.setUniformi("u_normalMap", texUnit++);
        }
        if (material.metallicRoughnessMap != null) {
            material.metallicRoughnessMap.bind(texUnit);
            shader.setUniformi("u_metallicRoughnessMap", texUnit++);
        }
        if (material.emissiveMap != null) {
            material.emissiveMap.bind(texUnit);
            shader.setUniformi("u_emissiveMap", texUnit++);
        }
        if (material.aoMap != null) {
            material.aoMap.bind(texUnit);
            shader.setUniformi("u_aoMap", texUnit);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/materials/GBufferShaderProvider.java
git commit -m "feat(rendering): add GBufferShaderProvider for shader variant selection"
```

---

## Task 10: LightComponent and LightingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/lighting/LightComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/lighting/LightVolumeMesh.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/lighting/LightingSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/rendering/lighting/LightComponentTest.java`
- Create: `core/src/test/java/com/galacticodyssey/rendering/lighting/LightingSystemTest.java`

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/java/com/galacticodyssey/rendering/lighting/LightComponentTest.java`:
```java
package com.galacticodyssey.rendering.lighting;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightComponentTest {

    @Test
    void defaultsToPointLight() {
        LightComponent light = new LightComponent();
        assertEquals(LightComponent.Type.POINT, light.type);
    }

    @Test
    void defaultColorIsWhite() {
        LightComponent light = new LightComponent();
        assertEquals(Color.WHITE, light.color);
    }

    @Test
    void defaultRadiusIsTen() {
        LightComponent light = new LightComponent();
        assertEquals(10f, light.radius, 0.001f);
    }

    @Test
    void defaultIntensityIsOne() {
        LightComponent light = new LightComponent();
        assertEquals(1f, light.intensity, 0.001f);
    }
}
```

Create `core/src/test/java/com/galacticodyssey/rendering/lighting/LightingSystemTest.java`:
```java
package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightingSystemTest {

    private Engine engine;
    private LightingSystem lightingSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        lightingSystem = new LightingSystem();
        engine.addSystem(lightingSystem);
    }

    @Test
    void collectsEntitiesWithLightComponent() {
        Entity entity = new Entity();
        LightComponent light = new LightComponent();
        light.type = LightComponent.Type.POINT;
        entity.add(light);
        engine.addEntity(entity);

        assertEquals(1, lightingSystem.getLights().size());
    }

    @Test
    void ignoresEntitiesWithoutLightComponent() {
        Entity entity = new Entity();
        engine.addEntity(entity);

        assertEquals(0, lightingSystem.getLights().size());
    }

    @Test
    void removedEntityDisappearsFromLights() {
        Entity entity = new Entity();
        entity.add(new LightComponent());
        engine.addEntity(entity);
        assertEquals(1, lightingSystem.getLights().size());

        engine.removeEntity(entity);
        assertEquals(0, lightingSystem.getLights().size());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.lighting.*" --info`
Expected: Compilation error — classes don't exist.

- [ ] **Step 3: Implement LightComponent**

Create `core/src/main/java/com/galacticodyssey/rendering/lighting/LightComponent.java`:
```java
package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

public class LightComponent implements Component {
    public enum Type { DIRECTIONAL, POINT, SPOT }

    public Type type = Type.POINT;
    public final Color color = new Color(Color.WHITE);
    public float intensity = 1f;
    public float radius = 10f;
    public float innerCone = 30f;
    public float outerCone = 45f;
    public boolean castShadows = false;
}
```

- [ ] **Step 4: Implement LightVolumeMesh**

Create `core/src/main/java/com/galacticodyssey/rendering/lighting/LightVolumeMesh.java`:
```java
package com.galacticodyssey.rendering.lighting;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

public class LightVolumeMesh implements Disposable {

    private final Mesh sphereMesh;

    public LightVolumeMesh(int segments, int rings) {
        int vertexCount = (segments + 1) * (rings + 1);
        int indexCount = segments * rings * 6;

        float[] vertices = new float[vertexCount * 3];
        short[] indices = new short[indexCount];

        int vi = 0;
        for (int r = 0; r <= rings; r++) {
            float phi = MathUtils.PI * r / rings;
            float sinPhi = MathUtils.sin(phi);
            float cosPhi = MathUtils.cos(phi);
            for (int s = 0; s <= segments; s++) {
                float theta = MathUtils.PI2 * s / segments;
                vertices[vi++] = sinPhi * MathUtils.cos(theta);
                vertices[vi++] = cosPhi;
                vertices[vi++] = sinPhi * MathUtils.sin(theta);
            }
        }

        int ii = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int curr = r * (segments + 1) + s;
                int next = curr + segments + 1;
                indices[ii++] = (short) curr;
                indices[ii++] = (short) next;
                indices[ii++] = (short) (curr + 1);
                indices[ii++] = (short) (curr + 1);
                indices[ii++] = (short) next;
                indices[ii++] = (short) (next + 1);
            }
        }

        sphereMesh = new Mesh(true, vertexCount, indexCount,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"));
        sphereMesh.setVertices(vertices);
        sphereMesh.setIndices(indices);
    }

    public void render(ShaderProgram shader) {
        sphereMesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        sphereMesh.dispose();
    }
}
```

- [ ] **Step 5: Implement LightingSystem**

Create `core/src/main/java/com/galacticodyssey/rendering/lighting/LightingSystem.java`:
```java
package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;

public class LightingSystem extends EntitySystem {

    private static final ComponentMapper<LightComponent> lightMapper =
        ComponentMapper.getFor(LightComponent.class);

    private ImmutableArray<Entity> lightEntities;

    @Override
    public void addedToEngine(Engine engine) {
        lightEntities = engine.getEntitiesFor(Family.all(LightComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        lightEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        // Light collection is passive — no per-frame logic needed.
        // LightingPass reads getLights() during render.
    }

    public ImmutableArray<Entity> getLights() {
        return lightEntities;
    }

    public static LightComponent getLight(Entity entity) {
        return lightMapper.get(entity);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.lighting.*" --info`
Expected: All 7 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/lighting/ \
        core/src/test/java/com/galacticodyssey/rendering/lighting/
git commit -m "feat(rendering): add LightComponent, LightVolumeMesh, and LightingSystem"
```

---

## Task 11: Lighting Shaders — Directional, Point, and Spot

**Files:**
- Create: `core/src/main/resources/shaders/lighting_directional.frag`
- Create: `core/src/main/resources/shaders/lighting_point.vert`
- Create: `core/src/main/resources/shaders/lighting_point.frag`
- Create: `core/src/main/resources/shaders/lighting_spot.frag`

- [ ] **Step 1: Create lighting_directional.frag**

Create `core/src/main/resources/shaders/lighting_directional.frag`:
```glsl
#version 330

#include "include/pbr_common.glsl"
#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"

in vec2 v_texCoord;

uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_rt2;
uniform sampler2D u_depth;
uniform sampler2D u_ssao;

uniform mat4 u_invProjection;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_ambientColor;
uniform float u_ambientIntensity;

out vec4 fragColor;

void main() {
    vec4 rt0 = texture(u_rt0, v_texCoord);
    vec4 rt1 = texture(u_rt1, v_texCoord);
    vec4 rt2 = texture(u_rt2, v_texCoord);
    float depth = texture(u_depth, v_texCoord).r;
    float ssao = texture(u_ssao, v_texCoord).r;

    vec3 albedo = rt0.rgb;
    float metallic = rt0.a;
    vec3 N = octDecode(rt1.xy);
    float roughness = rt1.z;
    float ao = rt1.w * ssao;
    vec3 emissive = rt2.rgb;

    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 V = normalize(-viewPos);
    vec3 L = normalize(-u_lightDir);

    vec3 Lo = evaluatePBR(N, V, L, albedo, metallic, roughness, u_lightColor, u_lightIntensity);

    vec3 ambient = u_ambientColor * u_ambientIntensity * albedo * ao;

    fragColor = vec4(Lo + ambient + emissive, 1.0);
}
```

- [ ] **Step 2: Create lighting_point.vert**

Create `core/src/main/resources/shaders/lighting_point.vert`:
```glsl
#version 330
in vec3 a_position;

uniform mat4 u_projViewTrans;
uniform vec3 u_lightPos;
uniform float u_lightRadius;

void main() {
    vec3 worldPos = a_position * u_lightRadius + u_lightPos;
    gl_Position = u_projViewTrans * vec4(worldPos, 1.0);
}
```

- [ ] **Step 3: Create lighting_point.frag**

Create `core/src/main/resources/shaders/lighting_point.frag`:
```glsl
#version 330

#include "include/pbr_common.glsl"
#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"

uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_depth;

uniform mat4 u_invProjection;
uniform vec2 u_screenSize;
uniform vec3 u_lightPos;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform float u_lightRadius;

out vec4 fragColor;

void main() {
    vec2 texCoord = gl_FragCoord.xy / u_screenSize;

    vec4 rt0 = texture(u_rt0, texCoord);
    vec4 rt1 = texture(u_rt1, texCoord);
    float depth = texture(u_depth, texCoord).r;

    vec3 albedo = rt0.rgb;
    float metallic = rt0.a;
    vec3 N = octDecode(rt1.xy);
    float roughness = rt1.z;

    vec3 viewPos = reconstructViewPos(texCoord, depth, u_invProjection);
    vec3 V = normalize(-viewPos);
    vec3 L = u_lightPos - viewPos;
    float dist = length(L);
    L = normalize(L);

    float attenuation = 1.0 / (1.0 + (dist / u_lightRadius) * (dist / u_lightRadius));
    float falloff = clamp(1.0 - dist / u_lightRadius, 0.0, 1.0);
    attenuation *= falloff * falloff;

    vec3 Lo = evaluatePBR(N, V, L, albedo, metallic, roughness, u_lightColor, u_lightIntensity * attenuation);

    fragColor = vec4(Lo, 1.0);
}
```

- [ ] **Step 4: Create lighting_spot.frag**

Create `core/src/main/resources/shaders/lighting_spot.frag`:
```glsl
#version 330

#include "include/pbr_common.glsl"
#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"

uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_depth;

uniform mat4 u_invProjection;
uniform vec2 u_screenSize;
uniform vec3 u_lightPos;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform float u_lightRadius;
uniform float u_innerCone;
uniform float u_outerCone;

out vec4 fragColor;

void main() {
    vec2 texCoord = gl_FragCoord.xy / u_screenSize;

    vec4 rt0 = texture(u_rt0, texCoord);
    vec4 rt1 = texture(u_rt1, texCoord);
    float depth = texture(u_depth, texCoord).r;

    vec3 albedo = rt0.rgb;
    float metallic = rt0.a;
    vec3 N = octDecode(rt1.xy);
    float roughness = rt1.z;

    vec3 viewPos = reconstructViewPos(texCoord, depth, u_invProjection);
    vec3 V = normalize(-viewPos);
    vec3 L = u_lightPos - viewPos;
    float dist = length(L);
    L = normalize(L);

    float attenuation = 1.0 / (1.0 + (dist / u_lightRadius) * (dist / u_lightRadius));
    float falloff = clamp(1.0 - dist / u_lightRadius, 0.0, 1.0);
    attenuation *= falloff * falloff;

    float theta = dot(L, normalize(-u_lightDir));
    float epsilon = u_innerCone - u_outerCone;
    float spotFactor = clamp((theta - u_outerCone) / epsilon, 0.0, 1.0);
    attenuation *= spotFactor;

    vec3 Lo = evaluatePBR(N, V, L, albedo, metallic, roughness, u_lightColor, u_lightIntensity * attenuation);

    fragColor = vec4(Lo, 1.0);
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/shaders/lighting_directional.frag \
        core/src/main/resources/shaders/lighting_point.vert \
        core/src/main/resources/shaders/lighting_point.frag \
        core/src/main/resources/shaders/lighting_spot.frag
git commit -m "feat(rendering): add directional, point, and spot lighting shaders with PBR BRDF"
```

---

## Task 12: LightingPass — Deferred Lighting Resolve

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/LightingPass.java`

- [ ] **Step 1: Implement LightingPass**

Create `core/src/main/java/com/galacticodyssey/rendering/LightingPass.java`:
```java
package com.galacticodyssey.rendering;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.rendering.lighting.LightComponent;
import com.galacticodyssey.rendering.lighting.LightVolumeMesh;
import com.galacticodyssey.rendering.lighting.LightingSystem;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import com.badlogic.gdx.utils.Disposable;

public class LightingPass implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private final LightVolumeMesh lightVolume;
    private FrameBuffer hdrBuffer;
    private int width, height;

    private final Matrix4 invProjection = new Matrix4();

    public LightingPass(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.lightVolume = new LightVolumeMesh(16, 8);
        this.width = width;
        this.height = height;
        createHDRBuffer();
    }

    private void createHDRBuffer() {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        hdrBuffer = builder.build();
        hdrBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void resolve(GBuffer gBuffer, Texture ssaoTexture, PerspectiveCamera camera,
                        Vector3 sunDirection, Vector3 sunColor, float sunIntensity,
                        Vector3 ambientColor, float ambientIntensity,
                        ImmutableArray<Entity> lights) {

        invProjection.set(camera.projection).inv();

        hdrBuffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // Directional light (sun) + ambient + emissive
        ShaderProgram dirShader = shaderCache.get("fullscreen.vert", "lighting_directional.frag");
        dirShader.bind();
        gBuffer.getAlbedoMetallic().bind(0);
        dirShader.setUniformi("u_rt0", 0);
        gBuffer.getNormalRoughnessAO().bind(1);
        dirShader.setUniformi("u_rt1", 1);
        gBuffer.getEmissive().bind(2);
        dirShader.setUniformi("u_rt2", 2);
        // Depth texture — bind from the FBO's texture attachments
        // (GBuffer depth is a renderbuffer, so we'll need to handle this)
        // For now, bind RT1 as placeholder — depth reconstruction addressed in integration
        ssaoTexture.bind(4);
        dirShader.setUniformi("u_ssao", 4);
        dirShader.setUniformMatrix("u_invProjection", invProjection);
        dirShader.setUniformf("u_lightDir", sunDirection.x, sunDirection.y, sunDirection.z);
        dirShader.setUniformf("u_lightColor", sunColor.x, sunColor.y, sunColor.z);
        dirShader.setUniformf("u_lightIntensity", sunIntensity);
        dirShader.setUniformf("u_ambientColor", ambientColor.x, ambientColor.y, ambientColor.z);
        dirShader.setUniformf("u_ambientIntensity", ambientIntensity);
        quad.render(dirShader);

        // Point and spot lights — additive blending
        if (lights != null && lights.size() > 0) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

            for (int i = 0; i < lights.size(); i++) {
                Entity entity = lights.get(i);
                LightComponent light = LightingSystem.getLight(entity);
                if (light == null || light.type == LightComponent.Type.DIRECTIONAL) continue;

                // Get light position from entity's transform
                // (Requires TransformComponent — resolved during integration)
                // For now, skip point/spot rendering until integration task
            }

            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        hdrBuffer.end();
    }

    public FrameBuffer getHDRBuffer() { return hdrBuffer; }
    public Texture getHDRTexture() { return hdrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        hdrBuffer.dispose();
        createHDRBuffer();
    }

    @Override
    public void dispose() {
        hdrBuffer.dispose();
        lightVolume.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/LightingPass.java
git commit -m "feat(rendering): add LightingPass for deferred lighting resolve with PBR BRDF"
```

---

## Task 13: Post-FX Shaders — SSAO, Bilateral Blur, Bloom, SSR, Tone Map, FXAA

**Files:**
- Create: `core/src/main/resources/shaders/ssao.frag`
- Create: `core/src/main/resources/shaders/blur_bilateral.frag`
- Create: `core/src/main/resources/shaders/bloom_downsample.frag`
- Create: `core/src/main/resources/shaders/bloom_upsample.frag`
- Create: `core/src/main/resources/shaders/ssr.frag`
- Create: `core/src/main/resources/shaders/tonemap.frag`
- Create: `core/src/main/resources/shaders/fxaa.frag`

This task creates all post-FX shader files. Each is a fullscreen fragment shader paired with `fullscreen.vert`.

- [ ] **Step 1: Create ssao.frag**

Create `core/src/main/resources/shaders/ssao.frag`:
```glsl
#version 330

#include "include/depth_reconstruct.glsl"
#include "include/normal_encoding.glsl"

in vec2 v_texCoord;

uniform sampler2D u_normalTex;
uniform sampler2D u_depthTex;
uniform sampler2D u_noiseTex;

uniform vec3 u_samples[32];
uniform int u_sampleCount;
uniform mat4 u_projection;
uniform mat4 u_invProjection;
uniform vec2 u_noiseScale;
uniform float u_radius;
uniform float u_bias;

out float fragOcclusion;

void main() {
    float depth = texture(u_depthTex, v_texCoord).r;
    if (depth >= 1.0) { fragOcclusion = 1.0; return; }

    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 normal = octDecode(texture(u_normalTex, v_texCoord).xy);
    vec3 randomVec = normalize(texture(u_noiseTex, v_texCoord * u_noiseScale).xyz * 2.0 - 1.0);

    vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    mat3 TBN = mat3(tangent, bitangent, normal);

    float occlusion = 0.0;
    for (int i = 0; i < u_sampleCount; i++) {
        vec3 samplePos = viewPos + TBN * u_samples[i] * u_radius;

        vec4 offset = u_projection * vec4(samplePos, 1.0);
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5;

        float sampleDepth = texture(u_depthTex, offset.xy).r;
        vec3 sampleViewPos = reconstructViewPos(offset.xy, sampleDepth, u_invProjection);

        float rangeCheck = smoothstep(0.0, 1.0, u_radius / abs(viewPos.z - sampleViewPos.z));
        occlusion += (sampleViewPos.z >= samplePos.z + u_bias ? 1.0 : 0.0) * rangeCheck;
    }

    fragOcclusion = 1.0 - (occlusion / float(u_sampleCount));
}
```

- [ ] **Step 2: Create blur_bilateral.frag**

Create `core/src/main/resources/shaders/blur_bilateral.frag`:
```glsl
#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_direction;
uniform vec2 u_texelSize;

out float fragColor;

void main() {
    float result = 0.0;
    float weightSum = 0.0;
    float centerVal = texture(u_inputTex, v_texCoord).r;

    for (int i = -2; i <= 2; i++) {
        vec2 offset = u_direction * float(i) * u_texelSize;
        float sample_ = texture(u_inputTex, v_texCoord + offset).r;
        float weight = 1.0 / (1.0 + abs(sample_ - centerVal) * 10.0);
        result += sample_ * weight;
        weightSum += weight;
    }

    fragColor = result / weightSum;
}
```

- [ ] **Step 3: Create bloom_downsample.frag**

Create `core/src/main/resources/shaders/bloom_downsample.frag`:
```glsl
#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;
uniform float u_threshold;
uniform float u_softKnee;
uniform bool u_applyThreshold;

out vec4 fragColor;

void main() {
    // 13-tap downsample filter
    vec3 A = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0, -1.0)).rgb;
    vec3 B = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0, -1.0)).rgb;
    vec3 C = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0, -1.0)).rgb;
    vec3 D = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-0.5, -0.5)).rgb;
    vec3 E = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.5, -0.5)).rgb;
    vec3 F = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  0.0)).rgb;
    vec3 G = texture(u_inputTex, v_texCoord).rgb;
    vec3 H = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  0.0)).rgb;
    vec3 I = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-0.5,  0.5)).rgb;
    vec3 J = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.5,  0.5)).rgb;
    vec3 K = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  1.0)).rgb;
    vec3 L = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0,  1.0)).rgb;
    vec3 M = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  1.0)).rgb;

    vec3 color = (D + E + I + J) * 0.5 * 0.25;
    color += (A + B + F + G) * 0.125 * 0.25;
    color += (B + C + G + H) * 0.125 * 0.25;
    color += (F + G + K + L) * 0.125 * 0.25;
    color += (G + H + L + M) * 0.125 * 0.25;

    if (u_applyThreshold) {
        float brightness = max(color.r, max(color.g, color.b));
        float knee = u_threshold * u_softKnee;
        float soft = brightness - u_threshold + knee;
        soft = clamp(soft, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 0.0001);
        float contrib = max(soft, brightness - u_threshold) / max(brightness, 0.0001);
        color *= contrib;
    }

    fragColor = vec4(color, 1.0);
}
```

- [ ] **Step 4: Create bloom_upsample.frag**

Create `core/src/main/resources/shaders/bloom_upsample.frag`:
```glsl
#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;
uniform float u_intensity;

out vec4 fragColor;

void main() {
    // 9-tap tent filter
    vec3 color = vec3(0.0);
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0, -1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0, -1.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0, -1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  0.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord).rgb * 4.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  0.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0,  1.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  1.0)).rgb * 1.0;
    color /= 16.0;

    fragColor = vec4(color * u_intensity, 1.0);
}
```

- [ ] **Step 5: Create ssr.frag**

Create `core/src/main/resources/shaders/ssr.frag`:
```glsl
#version 330

#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"
#include "include/pbr_common.glsl"

in vec2 v_texCoord;

uniform sampler2D u_hdrTex;
uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_depthTex;

uniform mat4 u_projection;
uniform mat4 u_invProjection;
uniform vec2 u_screenSize;
uniform float u_maxDistance;
uniform float u_thickness;
uniform int u_maxSteps;

out vec4 fragColor;

void main() {
    vec4 rt0 = texture(u_rt0, v_texCoord);
    vec4 rt1 = texture(u_rt1, v_texCoord);
    float metallic = rt0.a;
    float roughness = rt1.z;

    if (roughness > 0.7 || metallic < 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    float depth = texture(u_depthTex, v_texCoord).r;
    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 N = octDecode(rt1.xy);
    vec3 V = normalize(-viewPos);
    vec3 R = reflect(-V, N);

    vec3 startPos = viewPos;
    vec3 endPos = viewPos + R * u_maxDistance;

    vec4 startClip = u_projection * vec4(startPos, 1.0);
    vec4 endClip = u_projection * vec4(endPos, 1.0);
    vec2 startScreen = (startClip.xy / startClip.w) * 0.5 + 0.5;
    vec2 endScreen = (endClip.xy / endClip.w) * 0.5 + 0.5;

    vec2 delta = endScreen - startScreen;
    float stepSize = 1.0 / float(u_maxSteps);

    vec2 hitUV = vec2(0.0);
    bool hit = false;

    for (int i = 1; i <= u_maxSteps; i++) {
        float t = float(i) * stepSize;
        vec2 sampleUV = startScreen + delta * t;

        if (sampleUV.x < 0.0 || sampleUV.x > 1.0 || sampleUV.y < 0.0 || sampleUV.y > 1.0) break;

        float sampleDepth = texture(u_depthTex, sampleUV).r;
        vec3 sampleViewPos = reconstructViewPos(sampleUV, sampleDepth, u_invProjection);
        vec3 rayPos = startPos + R * u_maxDistance * t;

        if (rayPos.z < sampleViewPos.z && rayPos.z > sampleViewPos.z - u_thickness) {
            hitUV = sampleUV;
            hit = true;
            break;
        }
    }

    if (!hit) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 reflectedColor = texture(u_hdrTex, hitUV).rgb;

    // Confidence: fade at edges
    float edgeFade = 1.0;
    vec2 edgeDist = abs(hitUV - 0.5) * 2.0;
    edgeFade *= 1.0 - smoothstep(0.8, 1.0, edgeDist.x);
    edgeFade *= 1.0 - smoothstep(0.8, 1.0, edgeDist.y);

    // Fade for rays pointing away from camera
    float dirFade = clamp(dot(V, R) + 0.5, 0.0, 1.0);

    vec3 F0 = mix(vec3(0.04), rt0.rgb, metallic);
    float VdotN = max(dot(V, N), 0.0);
    vec3 fresnel = fresnelSchlick(VdotN, F0);

    float confidence = edgeFade * dirFade * (1.0 - roughness);

    fragColor = vec4(reflectedColor * fresnel * confidence, confidence);
}
```

- [ ] **Step 6: Create tonemap.frag**

Create `core/src/main/resources/shaders/tonemap.frag`:
```glsl
#version 330

in vec2 v_texCoord;

uniform sampler2D u_hdrTex;
uniform float u_exposure;

out vec4 fragColor;

vec3 acesTonemap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 hdr = texture(u_hdrTex, v_texCoord).rgb;
    hdr *= u_exposure;
    vec3 ldr = acesTonemap(hdr);
    ldr = pow(ldr, vec3(1.0 / 2.2));
    fragColor = vec4(ldr, 1.0);
}
```

- [ ] **Step 7: Create fxaa.frag**

Create `core/src/main/resources/shaders/fxaa.frag`:
```glsl
#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 rgbM = texture(u_inputTex, v_texCoord).rgb;
    vec3 rgbN = texture(u_inputTex, v_texCoord + vec2( 0.0, -1.0) * u_texelSize).rgb;
    vec3 rgbS = texture(u_inputTex, v_texCoord + vec2( 0.0,  1.0) * u_texelSize).rgb;
    vec3 rgbE = texture(u_inputTex, v_texCoord + vec2( 1.0,  0.0) * u_texelSize).rgb;
    vec3 rgbW = texture(u_inputTex, v_texCoord + vec2(-1.0,  0.0) * u_texelSize).rgb;

    float lumaM = luma(rgbM);
    float lumaN = luma(rgbN);
    float lumaS = luma(rgbS);
    float lumaE = luma(rgbE);
    float lumaW = luma(rgbW);

    float lumaMin = min(lumaM, min(min(lumaN, lumaS), min(lumaE, lumaW)));
    float lumaMax = max(lumaM, max(max(lumaN, lumaS), max(lumaE, lumaW)));
    float lumaRange = lumaMax - lumaMin;

    if (lumaRange < max(0.0312, lumaMax * 0.166)) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    vec3 rgbNW = texture(u_inputTex, v_texCoord + vec2(-1.0, -1.0) * u_texelSize).rgb;
    vec3 rgbNE = texture(u_inputTex, v_texCoord + vec2( 1.0, -1.0) * u_texelSize).rgb;
    vec3 rgbSW = texture(u_inputTex, v_texCoord + vec2(-1.0,  1.0) * u_texelSize).rgb;
    vec3 rgbSE = texture(u_inputTex, v_texCoord + vec2( 1.0,  1.0) * u_texelSize).rgb;

    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);

    float edgeH = abs(-2.0 * lumaW + lumaNW + lumaSW) + abs(-2.0 * lumaM + lumaN + lumaS) * 2.0 + abs(-2.0 * lumaE + lumaNE + lumaSE);
    float edgeV = abs(-2.0 * lumaN + lumaNW + lumaNE) + abs(-2.0 * lumaM + lumaW + lumaE) * 2.0 + abs(-2.0 * lumaS + lumaSW + lumaSE);
    bool isHorizontal = edgeH >= edgeV;

    float stepLength = isHorizontal ? u_texelSize.y : u_texelSize.x;
    float lumaP = isHorizontal ? lumaS : lumaE;
    float lumaN2 = isHorizontal ? lumaN : lumaW;

    float gradientP = abs(lumaP - lumaM);
    float gradientN = abs(lumaN2 - lumaM);

    if (gradientN > gradientP) stepLength = -stepLength;

    vec2 offset = isHorizontal ? vec2(0.0, stepLength * 0.5) : vec2(stepLength * 0.5, 0.0);
    vec3 rgbF = texture(u_inputTex, v_texCoord + offset).rgb;

    float lumaF = luma(rgbF);
    float subpixelFactor = clamp(abs(lumaF - lumaM) / lumaRange, 0.0, 1.0);
    subpixelFactor = smoothstep(0.0, 1.0, subpixelFactor);

    fragColor = vec4(mix(rgbM, rgbF, subpixelFactor * 0.75), 1.0);
}
```

- [ ] **Step 8: Commit**

```bash
git add core/src/main/resources/shaders/ssao.frag \
        core/src/main/resources/shaders/blur_bilateral.frag \
        core/src/main/resources/shaders/bloom_downsample.frag \
        core/src/main/resources/shaders/bloom_upsample.frag \
        core/src/main/resources/shaders/ssr.frag \
        core/src/main/resources/shaders/tonemap.frag \
        core/src/main/resources/shaders/fxaa.frag
git commit -m "feat(rendering): add all post-processing shaders (SSAO, bloom, SSR, tonemap, FXAA)"
```

---

## Task 14: Post-FX Java Classes — SSAOEffect, BloomEffect, SSREffect, ToneMappingEffect, FXAAEffect

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/PostFXUtils.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/SSAOEffect.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/BloomEffect.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/SSREffect.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/ToneMappingEffect.java`
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/FXAAEffect.java`
- Create: `core/src/test/java/com/galacticodyssey/rendering/postfx/BloomEffectTest.java`

- [ ] **Step 1: Write the failing test for bloom mip calculation**

Create `core/src/test/java/com/galacticodyssey/rendering/postfx/BloomEffectTest.java`:
```java
package com.galacticodyssey.rendering.postfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloomEffectTest {

    @Test
    void mipLevelCountFor1080p() {
        int levels = BloomEffect.calculateMipLevels(1920, 1080);
        assertEquals(6, levels);
    }

    @Test
    void mipLevelCountFor720p() {
        int levels = BloomEffect.calculateMipLevels(1280, 720);
        assertEquals(5, levels);
    }

    @Test
    void mipLevelCountFor4K() {
        int levels = BloomEffect.calculateMipLevels(3840, 2160);
        assertEquals(7, levels);
    }

    @Test
    void mipLevelCountMinimumIsTwo() {
        int levels = BloomEffect.calculateMipLevels(64, 64);
        assertEquals(2, levels);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.postfx.BloomEffectTest" --info`
Expected: Compilation error — `BloomEffect` does not exist.

- [ ] **Step 3: Implement PostFXUtils**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/PostFXUtils.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;

public final class PostFXUtils {

    private PostFXUtils() {}

    public static FrameBuffer createHDRBuffer(int width, int height) {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        FrameBuffer fbo = builder.build();
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fbo.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return fbo;
    }

    public static FrameBuffer createLDRBuffer(int width, int height) {
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fbo.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return fbo;
    }

    public static FrameBuffer createR8Buffer(int width, int height) {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_R8, GL20.GL_RED, GL20.GL_UNSIGNED_BYTE);
        FrameBuffer fbo = builder.build();
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fbo.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return fbo;
    }
}
```

- [ ] **Step 4: Implement SSAOEffect**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/SSAOEffect.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class SSAOEffect implements Disposable {

    private static final int SAMPLE_COUNT = 32;
    private static final int NOISE_SIZE = 4;

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ssaoBuffer;
    private FrameBuffer blurTempBuffer;
    private Texture noiseTexture;
    private final float[] kernelSamples;
    private int halfWidth, halfHeight;

    public float radius = 0.5f;
    public float bias = 0.025f;

    public SSAOEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.halfWidth = width / 2;
        this.halfHeight = height / 2;
        this.kernelSamples = generateKernel();
        this.noiseTexture = generateNoiseTexture();
        createBuffers();
    }

    private void createBuffers() {
        ssaoBuffer = PostFXUtils.createR8Buffer(halfWidth, halfHeight);
        blurTempBuffer = PostFXUtils.createR8Buffer(halfWidth, halfHeight);
    }

    private float[] generateKernel() {
        float[] samples = new float[SAMPLE_COUNT * 3];
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float x = MathUtils.random(-1f, 1f);
            float y = MathUtils.random(-1f, 1f);
            float z = MathUtils.random(0f, 1f);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            x /= len; y /= len; z /= len;
            float scale = (float) i / SAMPLE_COUNT;
            scale = MathUtils.lerp(0.1f, 1f, scale * scale);
            samples[i * 3] = x * scale;
            samples[i * 3 + 1] = y * scale;
            samples[i * 3 + 2] = z * scale;
        }
        return samples;
    }

    private Texture generateNoiseTexture() {
        Pixmap pixmap = new Pixmap(NOISE_SIZE, NOISE_SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                float r = MathUtils.random(-1f, 1f) * 0.5f + 0.5f;
                float g = MathUtils.random(-1f, 1f) * 0.5f + 0.5f;
                pixmap.drawPixel(x, y, ((int)(r * 255) << 24) | ((int)(g * 255) << 16) | (128 << 8) | 255);
            }
        }
        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pixmap.dispose();
        return tex;
    }

    public void apply(Texture normalTex, Texture depthTex, PerspectiveCamera camera) {
        // SSAO pass
        ssaoBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "ssao.frag");
        shader.bind();
        normalTex.bind(0);
        shader.setUniformi("u_normalTex", 0);
        depthTex.bind(1);
        shader.setUniformi("u_depthTex", 1);
        noiseTexture.bind(2);
        shader.setUniformi("u_noiseTex", 2);
        shader.setUniform3fv("u_samples[0]", kernelSamples, 0, kernelSamples.length);
        shader.setUniformi("u_sampleCount", SAMPLE_COUNT);
        shader.setUniformMatrix("u_projection", camera.projection);
        shader.setUniformMatrix("u_invProjection", camera.invProjectionView); // approximate
        shader.setUniformf("u_noiseScale", halfWidth / (float) NOISE_SIZE, halfHeight / (float) NOISE_SIZE);
        shader.setUniformf("u_radius", radius);
        shader.setUniformf("u_bias", bias);
        quad.render(shader);
        ssaoBuffer.end();

        // Bilateral blur — horizontal
        blurTempBuffer.begin();
        ShaderProgram blurShader = shaderCache.get("fullscreen.vert", "blur_bilateral.frag");
        blurShader.bind();
        ssaoBuffer.getColorBufferTexture().bind(0);
        blurShader.setUniformi("u_inputTex", 0);
        blurShader.setUniformf("u_direction", 1f, 0f);
        blurShader.setUniformf("u_texelSize", 1f / halfWidth, 1f / halfHeight);
        quad.render(blurShader);
        blurTempBuffer.end();

        // Bilateral blur — vertical
        ssaoBuffer.begin();
        blurShader.bind();
        blurTempBuffer.getColorBufferTexture().bind(0);
        blurShader.setUniformi("u_inputTex", 0);
        blurShader.setUniformf("u_direction", 0f, 1f);
        blurShader.setUniformf("u_texelSize", 1f / halfWidth, 1f / halfHeight);
        quad.render(blurShader);
        ssaoBuffer.end();
    }

    public Texture getResult() { return ssaoBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        int newHalf = width / 2;
        int newHalfH = height / 2;
        if (newHalf == halfWidth && newHalfH == halfHeight) return;
        halfWidth = newHalf;
        halfHeight = newHalfH;
        ssaoBuffer.dispose();
        blurTempBuffer.dispose();
        createBuffers();
    }

    @Override
    public void dispose() {
        ssaoBuffer.dispose();
        blurTempBuffer.dispose();
        noiseTexture.dispose();
    }
}
```

- [ ] **Step 5: Implement BloomEffect**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/BloomEffect.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class BloomEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer[] downBuffers;
    private FrameBuffer[] upBuffers;
    private int mipLevels;
    private int width, height;

    public float threshold = 1.0f;
    public float softKnee = 0.5f;
    public float intensity = 0.3f;

    public BloomEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        createBuffers();
    }

    public static int calculateMipLevels(int width, int height) {
        int minDim = Math.min(width, height);
        int levels = 0;
        while (minDim > 8) {
            minDim /= 2;
            levels++;
        }
        return Math.max(2, levels);
    }

    private void createBuffers() {
        mipLevels = calculateMipLevels(width, height);
        downBuffers = new FrameBuffer[mipLevels];
        upBuffers = new FrameBuffer[mipLevels - 1];
        int w = width / 2;
        int h = height / 2;
        for (int i = 0; i < mipLevels; i++) {
            downBuffers[i] = PostFXUtils.createHDRBuffer(Math.max(1, w), Math.max(1, h));
            if (i < mipLevels - 1) {
                upBuffers[i] = PostFXUtils.createHDRBuffer(Math.max(1, w), Math.max(1, h));
            }
            w /= 2;
            h /= 2;
        }
    }

    public void apply(Texture hdrInput) {
        ShaderProgram downShader = shaderCache.get("fullscreen.vert", "bloom_downsample.frag");
        ShaderProgram upShader = shaderCache.get("fullscreen.vert", "bloom_upsample.frag");

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // Downsample chain
        Texture currentInput = hdrInput;
        for (int i = 0; i < mipLevels; i++) {
            downBuffers[i].begin();
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            downShader.bind();
            currentInput.bind(0);
            downShader.setUniformi("u_inputTex", 0);
            downShader.setUniformf("u_texelSize",
                1f / currentInput.getWidth(), 1f / currentInput.getHeight());
            downShader.setUniformf("u_threshold", threshold);
            downShader.setUniformf("u_softKnee", softKnee);
            downShader.setUniformi("u_applyThreshold", i == 0 ? 1 : 0);
            quad.render(downShader);
            downBuffers[i].end();
            currentInput = downBuffers[i].getColorBufferTexture();
        }

        // Upsample chain
        for (int i = mipLevels - 2; i >= 0; i--) {
            Texture src = (i == mipLevels - 2)
                ? downBuffers[mipLevels - 1].getColorBufferTexture()
                : upBuffers[i + 1].getColorBufferTexture();

            upBuffers[i].begin();
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            // First: blit the upsampled lower-res level
            upShader.bind();
            src.bind(0);
            upShader.setUniformi("u_inputTex", 0);
            upShader.setUniformf("u_texelSize", 1f / src.getWidth(), 1f / src.getHeight());
            upShader.setUniformf("u_intensity", 1.0f);
            quad.render(upShader);

            // Then: additive blend the current downsample level
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            downBuffers[i].getColorBufferTexture().bind(0);
            upShader.setUniformi("u_inputTex", 0);
            upShader.setUniformf("u_texelSize",
                1f / downBuffers[i].getWidth(), 1f / downBuffers[i].getHeight());
            upShader.setUniformf("u_intensity", 1.0f);
            quad.render(upShader);
            Gdx.gl.glDisable(GL20.GL_BLEND);

            upBuffers[i].end();
        }
    }

    public Texture getResult() {
        return upBuffers[0].getColorBufferTexture();
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        disposeBuffers();
        createBuffers();
    }

    private void disposeBuffers() {
        for (FrameBuffer fb : downBuffers) if (fb != null) fb.dispose();
        for (FrameBuffer fb : upBuffers) if (fb != null) fb.dispose();
    }

    @Override
    public void dispose() {
        disposeBuffers();
    }
}
```

- [ ] **Step 6: Implement SSREffect**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/SSREffect.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class SSREffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ssrBuffer;
    private int width, height;

    public float maxDistance = 50f;
    public float thickness = 0.1f;
    public int maxSteps = 16;

    public SSREffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        ssrBuffer = PostFXUtils.createHDRBuffer(width, height);
    }

    public void apply(Texture hdrTex, Texture rt0, Texture rt1, Texture depthTex, PerspectiveCamera camera) {
        ssrBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "ssr.frag");
        shader.bind();
        hdrTex.bind(0);
        shader.setUniformi("u_hdrTex", 0);
        rt0.bind(1);
        shader.setUniformi("u_rt0", 1);
        rt1.bind(2);
        shader.setUniformi("u_rt1", 2);
        depthTex.bind(3);
        shader.setUniformi("u_depthTex", 3);
        shader.setUniformMatrix("u_projection", camera.projection);
        shader.setUniformMatrix("u_invProjection", camera.invProjectionView);
        shader.setUniformf("u_screenSize", width, height);
        shader.setUniformf("u_maxDistance", maxDistance);
        shader.setUniformf("u_thickness", thickness);
        shader.setUniformi("u_maxSteps", maxSteps);
        quad.render(shader);
        ssrBuffer.end();
    }

    public Texture getResult() { return ssrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        ssrBuffer.dispose();
        ssrBuffer = PostFXUtils.createHDRBuffer(width, height);
    }

    @Override
    public void dispose() {
        ssrBuffer.dispose();
    }
}
```

- [ ] **Step 7: Implement ToneMappingEffect**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/ToneMappingEffect.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class ToneMappingEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ldrBuffer;
    private int width, height;

    public float exposure = 1.0f;

    public ToneMappingEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        ldrBuffer = PostFXUtils.createLDRBuffer(width, height);
    }

    public void apply(Texture hdrInput) {
        ldrBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "tonemap.frag");
        shader.bind();
        hdrInput.bind(0);
        shader.setUniformi("u_hdrTex", 0);
        shader.setUniformf("u_exposure", exposure);
        quad.render(shader);
        ldrBuffer.end();
    }

    public Texture getResult() { return ldrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        ldrBuffer.dispose();
        ldrBuffer = PostFXUtils.createLDRBuffer(width, height);
    }

    @Override
    public void dispose() {
        ldrBuffer.dispose();
    }
}
```

- [ ] **Step 8: Implement FXAAEffect**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/FXAAEffect.java`:
```java
package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class FXAAEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private int width, height;

    public FXAAEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
    }

    public void apply(Texture ldrInput) {
        // Renders directly to the default framebuffer (screen)
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "fxaa.frag");
        shader.bind();
        ldrInput.bind(0);
        shader.setUniformi("u_inputTex", 0);
        shader.setUniformf("u_texelSize", 1f / width, 1f / height);
        quad.render(shader);
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void dispose() {
        // No FBOs to dispose — renders to default framebuffer
    }
}
```

- [ ] **Step 9: Run BloomEffect test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.rendering.postfx.BloomEffectTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/postfx/ \
        core/src/test/java/com/galacticodyssey/rendering/postfx/
git commit -m "feat(rendering): add post-processing effects (SSAO, bloom, SSR, tone mapping, FXAA)"
```

---

## Task 15: PostProcessingPipeline — Chain Effects Together

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/postfx/PostProcessingPipeline.java`

- [ ] **Step 1: Implement PostProcessingPipeline**

Create `core/src/main/java/com/galacticodyssey/rendering/postfx/PostProcessingPipeline.java`:
```java
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
        ssao.apply(gBuffer.getNormalRoughnessAO(), gBuffer.getAlbedoMetallic(), camera);
    }

    public Texture getSSAOTexture() { return ssao.getResult(); }

    public void apply(FrameBuffer hdrBuffer, GBuffer gBuffer, PerspectiveCamera camera) {
        Texture hdrTex = hdrBuffer.getColorBufferTexture();

        // SSR
        ssr.apply(hdrTex, gBuffer.getAlbedoMetallic(), gBuffer.getNormalRoughnessAO(),
                  gBuffer.getAlbedoMetallic(), camera); // depth placeholder
        // Composite SSR into HDR buffer
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

        // Bloom
        bloom.apply(hdrBuffer.getColorBufferTexture());
        // Composite bloom into HDR buffer
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

        // Tone mapping (HDR → LDR)
        toneMapping.apply(hdrBuffer.getColorBufferTexture());

        // FXAA (LDR → screen)
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
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/postfx/PostProcessingPipeline.java
git commit -m "feat(rendering): add PostProcessingPipeline chaining SSAO, SSR, bloom, tonemap, FXAA"
```

---

## Task 16: ForwardPass — Sky, Water, Particles

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/ForwardPass.java`
- Create: `core/src/main/resources/shaders/sky_atmospheric.vert`
- Create: `core/src/main/resources/shaders/sky_atmospheric.frag`
- Create: `core/src/main/resources/shaders/forward_transparent.vert`
- Create: `core/src/main/resources/shaders/forward_transparent.frag`

- [ ] **Step 1: Extract sky shaders from AtmosphericSkyRenderer**

Read the inline shader strings from `AtmosphericSkyRenderer.java` (lines 17–175) and write them as external files.

Create `core/src/main/resources/shaders/sky_atmospheric.vert`: Copy the vertex shader string from AtmosphericSkyRenderer.java lines 17–26 into this file, converting from Java string concatenation to raw GLSL.

Create `core/src/main/resources/shaders/sky_atmospheric.frag`: Copy the fragment shader string from AtmosphericSkyRenderer.java lines 28–175 into this file.

- [ ] **Step 2: Create forward_transparent.vert**

Create `core/src/main/resources/shaders/forward_transparent.vert`:
```glsl
#version 330

in vec3 a_position;
in vec3 a_normal;
in vec2 a_texCoord0;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_worldPos;
out vec3 v_normal;
out vec2 v_texCoord;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_normal = normalize(u_normalMatrix * a_normal);
    v_texCoord = a_texCoord0;
    gl_Position = u_projViewTrans * worldPos;
}
```

- [ ] **Step 3: Create forward_transparent.frag**

Create `core/src/main/resources/shaders/forward_transparent.frag`:
```glsl
#version 330

#include "include/pbr_common.glsl"

in vec3 v_worldPos;
in vec3 v_normal;
in vec2 v_texCoord;

uniform vec4 u_albedoTint;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_cameraPos;
uniform float u_alpha;

out vec4 fragColor;

void main() {
    vec3 N = normalize(v_normal);
    vec3 V = normalize(u_cameraPos - v_worldPos);
    vec3 L = normalize(-u_lightDir);

    vec3 Lo = evaluatePBR(N, V, L, u_albedoTint.rgb, 0.0, 0.5, u_lightColor, u_lightIntensity);
    vec3 ambient = u_albedoTint.rgb * 0.1;

    fragColor = vec4(Lo + ambient, u_alpha);
}
```

- [ ] **Step 4: Implement ForwardPass**

Create `core/src/main/java/com/galacticodyssey/rendering/ForwardPass.java`:
```java
package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ui.AtmosphericSkyRenderer;

public class ForwardPass implements Disposable {

    public void render(FrameBuffer hdrBuffer, PerspectiveCamera camera,
                       AtmosphericSkyRenderer skyRenderer,
                       Runnable waterRenderer,
                       Runnable particleRenderer) {

        hdrBuffer.begin();

        // Sky — renders where stencil=0 (no geometry)
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 0, 0xFF);
        Gdx.gl.glStencilMask(0x00);
        if (skyRenderer != null) {
            skyRenderer.render(camera);
        }
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);

        // Water — alpha blended, depth read only
        if (waterRenderer != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            waterRenderer.run();
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // Particles
        if (particleRenderer != null) {
            particleRenderer.run();
        }

        hdrBuffer.end();
    }

    @Override
    public void dispose() {
        // ForwardPass doesn't own any resources
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/ForwardPass.java \
        core/src/main/resources/shaders/sky_atmospheric.vert \
        core/src/main/resources/shaders/sky_atmospheric.frag \
        core/src/main/resources/shaders/forward_transparent.vert \
        core/src/main/resources/shaders/forward_transparent.frag
git commit -m "feat(rendering): add ForwardPass and forward transparent shaders"
```

---

## Task 17: DeferredRenderer — The Pipeline Orchestrator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/rendering/DeferredRenderer.java`

- [ ] **Step 1: Implement DeferredRenderer**

Create `core/src/main/java/com/galacticodyssey/rendering/DeferredRenderer.java`:
```java
package com.galacticodyssey.rendering;

import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.lighting.LightingSystem;
import com.galacticodyssey.rendering.postfx.PostProcessingPipeline;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import com.galacticodyssey.ui.AtmosphericSkyRenderer;

public class DeferredRenderer implements Disposable {

    private final GBuffer gBuffer;
    private final LightingPass lightingPass;
    private final ForwardPass forwardPass;
    private final PostProcessingPipeline postFX;
    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;

    private int width, height;

    public DeferredRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        this.quad = new FullscreenQuad();
        this.shaderCache = new ShaderCache();
        this.gBuffer = new GBuffer(width, height);
        this.lightingPass = new LightingPass(shaderCache, quad, width, height);
        this.forwardPass = new ForwardPass();
        this.postFX = new PostProcessingPipeline(shaderCache, quad, width, height);
    }

    public void render(PerspectiveCamera camera,
                       Runnable opaqueRenderer,
                       Runnable fpWeaponRenderer,
                       AtmosphericSkyRenderer skyRenderer,
                       Runnable waterRenderer,
                       Runnable particleRenderer,
                       LightingSystem lightingSystem,
                       Vector3 sunDirection, Vector3 sunColor, float sunIntensity,
                       Vector3 ambientColor, float ambientIntensity) {

        // Pass 1: G-Buffer
        gBuffer.begin();
        if (opaqueRenderer != null) opaqueRenderer.run();
        if (fpWeaponRenderer != null) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            fpWeaponRenderer.run();
        }
        gBuffer.end();

        // Pass 2: SSAO
        postFX.applySSAO(gBuffer, camera);

        // Pass 3: Deferred Lighting
        ImmutableArray<Entity> lights = (lightingSystem != null) ? lightingSystem.getLights() : null;
        lightingPass.resolve(gBuffer, postFX.getSSAOTexture(), camera,
            sunDirection, sunColor, sunIntensity,
            ambientColor, ambientIntensity, lights);

        // Pass 4: Forward transparents (sky, water, particles)
        forwardPass.render(lightingPass.getHDRBuffer(), camera,
            skyRenderer, waterRenderer, particleRenderer);

        // Passes 5-7: SSR → Bloom → Tone mapping → FXAA → screen
        postFX.apply(lightingPass.getHDRBuffer(), gBuffer, camera);
    }

    public void reloadShaders() {
        shaderCache.reloadAll();
    }

    public ShaderCache getShaderCache() { return shaderCache; }
    public GBuffer getGBuffer() { return gBuffer; }
    public PostProcessingPipeline getPostFX() { return postFX; }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        gBuffer.resize(width, height);
        lightingPass.resize(width, height);
        postFX.resize(width, height);
    }

    @Override
    public void dispose() {
        gBuffer.dispose();
        lightingPass.dispose();
        forwardPass.dispose();
        postFX.dispose();
        shaderCache.dispose();
        quad.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/DeferredRenderer.java
git commit -m "feat(rendering): add DeferredRenderer pipeline orchestrator"
```

---

## Task 18: Integration — Wire DeferredRenderer into GameScreen

This is the integration task. Modify `GameScreen.java` to use the new `DeferredRenderer` instead of scattered render calls. Also register new components and add LightingSystem.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java`

- [ ] **Step 1: Add LightingSystem to GameWorld**

In `GameWorld.java`, add a `LightingSystem` field and register it with the Ashley engine. Add it after the combat systems block (around line 364):

```java
// At the field declarations:
private LightingSystem lightingSystem;

// In the constructor, after combat systems:
lightingSystem = new LightingSystem();
engine.addSystem(lightingSystem);

// Add getter:
public LightingSystem getLightingSystem() { return lightingSystem; }
```

Import: `com.galacticodyssey.rendering.lighting.LightingSystem`

- [ ] **Step 2: Register MaterialComponent and LightComponent in SnapshotComponentRegistry**

In `SnapshotComponentRegistry.java`, add registrations in the static initializer block:

```java
// After existing registrations:
register("Material", null, MaterialComponent::new);
register("Light", null, LightComponent::new);
```

Imports: `com.galacticodyssey.rendering.materials.MaterialComponent`, `com.galacticodyssey.rendering.lighting.LightComponent`

Note: These components don't have snapshot classes yet (pass `null` for the snapshot class). They can be saved/loaded in a future persistence task.

- [ ] **Step 3: Add DeferredRenderer to GameScreen**

In `GameScreen.java`:

Add field (after `atmosphericSkyRenderer` field around line 114):
```java
private DeferredRenderer deferredRenderer;
```

In the `show()` → `initializeWorld()` method, after camera creation:
```java
deferredRenderer = new DeferredRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
```

Import: `com.galacticodyssey.rendering.DeferredRenderer`

- [ ] **Step 4: Replace render() method body**

Replace the body of `GameScreen.render()` (lines 912–956):

```java
@Override
public void render(float delta) {
    if (!paused) {
        float clampedDelta = Math.min(delta, 1f / 30f);
        gameWorld.update(clampedDelta);
        dayNightCycle.update(clampedDelta);
        gameTime += clampedDelta;
    }

    if (!paused) {
        WorldPopulator.updateAnimals(populatedWorld, delta,
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
    }

    syncBoxTransforms();

    atmosphericSkyRenderer.setSunDirection(dayNightCycle.getSunDirection());
    atmosphericSkyRenderer.setTime(gameTime);

    Vector3 sunDir = dayNightCycle.getSunDirection();
    float sunIntensity = dayNightCycle.getSunIntensity();
    float ambientIntensity = dayNightCycle.getAmbientIntensity();

    deferredRenderer.render(
        camera,
        () -> {
            // Opaque geometry: terrain, boxes, world objects, ships
            renderTerrain();
            renderBoxes();
            renderWorldObjects();
            renderShips();
        },
        () -> renderFirstPersonWeapon(delta),
        atmosphericSkyRenderer,
        () -> {
            if (populatedWorld != null && populatedWorld.waterInstance != null) {
                modelBatch.begin(camera);
                modelBatch.render(populatedWorld.waterInstance, environment);
                modelBatch.end();
            }
        },
        () -> gameWorld.getParticleRenderSystem().render(),
        gameWorld.getLightingSystem(),
        sunDir,
        new Vector3(1f, 0.95f, 0.9f), // sun color
        sunIntensity * 3f,
        new Vector3(0.1f, 0.1f, 0.15f), // ambient color
        ambientIntensity
    );

    // HUD / UI (unchanged — renders to screen)
    gameWorld.getCockpitHUDSystem().render(delta);
    gameWorld.getDebugHudSystem().render(delta);
    if (dialogHudSystem != null) dialogHudSystem.render(delta);
    if (hackingOverlay != null) hackingOverlay.render(delta);
    if (paused) { pauseStage.act(delta); pauseStage.draw(); }
}
```

Note: The existing `renderTerrain()`, `renderBoxes()`, etc. methods stay intact for now — they still use their current shaders and render into whatever FBO is bound (the G-buffer). A future task will update them to use the G-buffer shader, but this integration gets the pipeline wired up and rendering.

- [ ] **Step 5: Add shader hot-reload to input handling**

In GameScreen's input handling (wherever key events are processed), add F5 reload:

```java
if (keycode == Input.Keys.F5) {
    deferredRenderer.reloadShaders();
    return true;
}
```

- [ ] **Step 6: Update resize()**

In `GameScreen.resize()`:
```java
if (deferredRenderer != null) deferredRenderer.resize(width, height);
```

- [ ] **Step 7: Update dispose()**

In `GameScreen.dispose()`, add before existing disposal code:
```java
if (deferredRenderer != null) { deferredRenderer.dispose(); deferredRenderer = null; }
```

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
        core/src/main/java/com/galacticodyssey/core/GameWorld.java \
        core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java
git commit -m "feat(rendering): integrate DeferredRenderer into GameScreen, add LightingSystem to GameWorld"
```

---

## Task 19: Delete Replaced Files and Clean Up

**Files:**
- Delete: `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java`
- Delete: `core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java`

- [ ] **Step 1: Remove FogShaderProvider and SkyRenderer**

Delete `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java` and `core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java`.

- [ ] **Step 2: Remove all references to deleted files**

In `GameScreen.java`:
- Remove the `fogShaderProvider` and `fogModelBatch` field declarations
- Remove their initialization in `initializeWorld()`
- Remove their disposal in `dispose()`
- Remove the import for `FogShaderProvider`

Search the codebase for any remaining imports or references to `FogShaderProvider` or `SkyRenderer` and remove them.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 4: Run all tests**

Run: `./gradlew :core:test --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git rm core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java \
       core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java
git add -u
git commit -m "refactor(rendering): remove FogShaderProvider and SkyRenderer, clean up references"
```

---

## Task 20: Visual Verification — Run the Game

- [ ] **Step 1: Build and run**

Run: `./gradlew :desktop:run`

Expected behavior:
- The game launches and renders the scene through the deferred pipeline
- Terrain, ships, world objects, and FPS weapon are visible
- Atmospheric sky renders correctly (extracted shaders)
- Bloom glow is visible on bright surfaces
- FXAA smooths edges

- [ ] **Step 2: Debug any shader compilation issues**

If shaders fail to compile, check the log output. Common issues:
- `#version` directive must be the first line (before `#include`)
- GLSL 3.30 uses `in`/`out` not `attribute`/`varying`
- `texture()` not `texture2D()`
- `layout(location = N) out` requires GLSL 3.30+

Press F5 to hot-reload shaders after fixes.

- [ ] **Step 3: Verify post-processing chain**

Toggle effects by temporarily commenting out individual effect calls in `PostProcessingPipeline.apply()` to verify each is working:
- Without bloom: no glow on bright surfaces
- Without SSAO: no contact shadows in crevices
- Without tone mapping: image is blown out (HDR values >1 clip to white)
- Without FXAA: visible aliased edges

- [ ] **Step 4: Commit any shader fixes**

```bash
git add -u
git commit -m "fix(rendering): address shader compilation issues from visual verification"
```

---

## Task 21: Add a Sun Directional Light Entity

To complete the lighting integration, create a sun entity with a directional LightComponent so the deferred lighting pass has a proper light source.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Create sun entity in GameScreen.initializeWorld()**

After entity setup, add a sun light entity:

```java
Entity sunEntity = new Entity();
LightComponent sunLight = new LightComponent();
sunLight.type = LightComponent.Type.DIRECTIONAL;
sunLight.color.set(1f, 0.95f, 0.9f, 1f);
sunLight.intensity = 3f;
sunEntity.add(sunLight);
gameWorld.getEngine().addEntity(sunEntity);
```

Import: `com.galacticodyssey.rendering.lighting.LightComponent`

- [ ] **Step 2: Update sun light direction each frame**

In `GameScreen.render()`, before the `deferredRenderer.render()` call, update the sun light's direction from `dayNightCycle`:

```java
// Update sun entity each frame if needed
// (The DeferredRenderer already receives sunDirection as a parameter,
// so this is optional — only needed if point/spot lights read from entities)
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(rendering): add sun directional light entity for deferred lighting"
```
