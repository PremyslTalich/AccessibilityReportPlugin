plugins {
    id("java")
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "cz.talich.arp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        instrumentationTools()
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id.set("cz.talich.arp")
        name.set("Accessibility Report Plugin")
        vendor {
            name.set("Talich")
        }
        description.set("A plugin to dump UI Automator.")

        ideaVersion {
            sinceBuild.set("243")
            untilBuild.set("253.*")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}