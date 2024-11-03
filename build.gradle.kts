plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
}

application {
    mainClass.set("DependencyAnalyzer")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "DependencyAnalyzer"
    }
}

