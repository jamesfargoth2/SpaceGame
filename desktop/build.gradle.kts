val gdxVersion: String by project
val gdxControllersVersion: String by project

plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-desktop:$gdxControllersVersion")
}

application {
    mainClass.set("com.galacticodyssey.desktop.DesktopLauncher")
}

tasks.named<JavaExec>("run") {
    workingDir = project(":core").file("src/main/resources")
    isIgnoreExitValue = true

    jvmArgs("--enable-native-access=ALL-UNNAMED")

    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
