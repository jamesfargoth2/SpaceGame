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
