import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Plugin.KOTLIN
    id("com.github.johnrengelman.shadow") version Plugin.SHADOW_JAR
}

group = "com.github.felixgail"
version = "0.3.0"

tasks {
    "compileKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "compileTestKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "test"(Test::class) {
        useJUnitPlatform()
    }
}

dependencies {
    compileOnly(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )
    implementation(
        group = "com.github.felixgail",
        name = "gplaymusic",
        version = Lib.GPLAYMUSIC
    )

    testImplementation(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )
    testRuntime(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Lib.SLF4J
    )
    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Lib.JUNIT
    )
    testRuntime(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Lib.JUNIT
    )
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.MINUTES)
}

repositories {
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots") {
        mavenContent {
            snapshotsOnly()
        }
    }
}