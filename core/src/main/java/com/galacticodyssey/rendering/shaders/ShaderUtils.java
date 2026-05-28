package com.galacticodyssey.rendering.shaders;

import com.badlogic.gdx.Gdx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods for loading and preprocessing GLSL shader source.
 *
 * <p>{@link #resolveIncludes} and {@link #prependDefines} are pure string-processing
 * methods that load included files from the JVM classpath, making them fully testable
 * without a libGDX/OpenGL context.
 *
 * <p>{@link #loadShader} uses {@code Gdx.files.internal} and therefore requires a
 * running libGDX application.
 */
public final class ShaderUtils {

    private ShaderUtils() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Load a shader file using the libGDX internal file-handle system.
     * Requires a running libGDX application (Gdx.files must be initialised).
     *
     * @param path path relative to the assets root, e.g. {@code "shaders/gbuffer.vert"}
     * @return full GLSL source string
     */
    public static String loadShader(String path) {
        return Gdx.files.internal(path).readString();
    }

    /**
     * Recursively replace {@code #include "..."} directives with the contents of
     * the referenced file.  Files are resolved relative to {@code basePath} on the
     * JVM classpath (so test resources and packaged assets both work).
     *
     * @param source   GLSL source that may contain {@code #include} directives
     * @param basePath classpath prefix for resolving includes, e.g. {@code "shaders"}
     * @return source with all {@code #include} directives expanded
     * @throws IllegalArgumentException if a referenced file cannot be found or a
     *                                  circular include is detected
     */
    public static String resolveIncludes(String source, String basePath) {
        return resolveIncludes(source, basePath, new HashSet<>());
    }

    /**
     * Prepend {@code #define} lines immediately after the {@code #version} directive
     * (or at the top of the file if no {@code #version} line is present).
     *
     * @param source  GLSL source string
     * @param defines zero or more define names (without the {@code #define} keyword)
     * @return modified source; the original is returned unchanged when {@code defines}
     *         is empty
     */
    public static String prependDefines(String source, String... defines) {
        if (defines.length == 0) {
            return source;
        }

        // Split only on the first newline to preserve the rest of the source exactly.
        String[] parts = source.split("\n", 2);
        StringBuilder sb = new StringBuilder();

        if (parts[0].trim().startsWith("#version")) {
            sb.append(parts[0]).append('\n');
            for (String define : defines) {
                sb.append("#define ").append(define).append('\n');
            }
            if (parts.length > 1) {
                sb.append(parts[1]);
            }
        } else {
            for (String define : defines) {
                sb.append("#define ").append(define).append('\n');
            }
            sb.append(source);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String resolveIncludes(String source, String basePath, Set<String> visited) {
        StringBuilder result = new StringBuilder();
        String[] lines = source.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("#include \"") && trimmed.endsWith("\"")) {
                String includePath = trimmed.substring(10, trimmed.length() - 1);
                String fullPath = basePath + "/" + includePath;

                if (!visited.add(fullPath)) {
                    throw new IllegalArgumentException("Circular #include detected: " + fullPath);
                }

                String includeSource = readFromClasspath(fullPath);
                result.append(resolveIncludes(includeSource, basePath, visited));

                // Remove from visited so the same file can be included in sibling branches.
                visited.remove(fullPath);
            } else {
                result.append(line);
                if (i < lines.length - 1) {
                    result.append('\n');
                }
            }
        }

        return result.toString();
    }

    /**
     * Read a classpath resource to a UTF-8 string.
     *
     * @param path classpath-relative path, e.g. {@code "shaders/include/common.glsl"}
     * @return file contents
     * @throws IllegalArgumentException if the resource is not found
     */
    private static String readFromClasspath(String path) {
        ClassLoader cl = ShaderUtils.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Shader include not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read shader include: " + path, e);
        }
    }
}
