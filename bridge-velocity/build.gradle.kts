plugins {
    `java-library`
    grim.`base-conventions`
    grim.`shadow-conventions`
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(project(":bridge-protocol"))
}
