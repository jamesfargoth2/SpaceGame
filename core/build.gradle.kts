plugins {
    `java-library`
}

// Temporarily exclude test stubs that reference not-yet-implemented systems.
// These are placeholder tests for future features; they do not compile until
// the referenced classes exist.  Remove entries here as each system lands.
sourceSets {
    test {
        java {
            exclude(
                "com/galacticodyssey/combat/CombatIntegrationTest.java",
                "com/galacticodyssey/economy/EconomyIntegrationTest.java",
                "com/galacticodyssey/economy/procgen/SystemEconomyGeneratorTest.java",
                // "com/galacticodyssey/hacking/PuzzleGridTest.java" — PuzzleGrid implemented
            )
        }
    }
}

val gdxVersion: String by project
val ashleyVersion: String by project
val gdxAiVersion: String by project
val junitVersion: String by project

dependencies {
    api(project(":common"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-bullet:$gdxVersion")
    api("com.badlogicgames.ashley:ashley:$ashleyVersion")
    api("com.badlogicgames.gdx:gdx-ai:$gdxAiVersion")
    api("com.esotericsoftware:kryo:5.6.0")
    api("com.github.mgsx-dev.gdx-gltf:gltf:2.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}
