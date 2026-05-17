import java.util.Properties

plugins {
    id("java")
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProperty(name: String): String? =
    localProperties.getProperty(name)

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
        pluginVerifier()
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
    signing {
        keyStore.set(file("plugin-signing-keystore.p12"))
        keyStorePassword.set(localProperty("keystorePassword"))
        keyStoreKeyAlias.set("plugin-signing-key")
        keyStoreType.set("PKCS12")
    }
    publishing {
        token.set(
            providers.provider {
                localProperty("publishPluginToken")
            }
        )
    }
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}