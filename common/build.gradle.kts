plugins {
    `java-library`
}

dependencies {
    api("com.esotericsoftware:kryo:5.6.0")
    api("com.esotericsoftware:kryonet:2.22.0-RC1")

    testImplementation("org.junit.jupiter:junit-jupiter:${project.property("junitVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
