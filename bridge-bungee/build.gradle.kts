plugins {
    `java-library`
    grim.`base-conventions`
    grim.`shadow-conventions`
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
