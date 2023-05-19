plugins {
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "ris58h.lacopom"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
}

intellij {
    version.set("2019.2") // Build against 'since' version
//    version.set("LATEST-EAP-SNAPSHOT") // Check against 'latest' version
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("maven"))

    updateSinceUntilBuild.set(false)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    buildSearchableOptions {
        enabled = false
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
