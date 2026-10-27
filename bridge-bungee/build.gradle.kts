plugins {
    `java-library`
    grim.`base-conventions`
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    mavenCentral()
}

dependencies {
    compileOnly(libs.bungeecord.api)
    implementation(project(":bridge-protocol"))
}

// The proxy provides slf4j, adventure and its own API: bundle only the bridge protocol, without the
// relocations of grim.shadow-conventions (they point the plugin at shaded classes that are not in the jar).
tasks.shadowJar {
    archiveFileName = "${rootProject.name}-${project.name}-${rootProject.version}.jar"
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
