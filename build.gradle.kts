plugins {
    id("java")
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "cz.talich.arp"
version = "1.0.0"

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
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id.set("cz.talich.arp")
        name.set("Android Accessibility Report")
        vendor {
            name.set("Přemysl Talich")
        }
        description.set("Analyze the accesibility IDs of your Android app")

        ideaVersion {
            sinceBuild.set("243")
            untilBuild.set(provider { null })
        }
    }
}

tasks.test {
    useJUnitPlatform()
}