val gdxVersion: String by project
val ashleyVersion: String by project
val junitVersion: String by project
val hikariVersion: String by project
val jedisVersion: String by project
val postgresVersion: String by project

plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("redis.clients:jedis:$jedisVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.galacticodyssey.server.ServerLauncher")
}
