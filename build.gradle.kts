import java.util.Properties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

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
version = "1.2.0"

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
        zipSigner()
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
            name = "Přemysl Talich"
            url = "https://github.com/PremyslTalich"
        }
        description.set("Analyze the accesibility IDs of your Android app")

        ideaVersion {
            sinceBuild.set("243")
            untilBuild.set(provider { null })
        }
    }
    signing {
        certificateChain.set(file("certificate-chain.pem").readText())
        privateKey.set(file("private.pem").readText())
        password.set(
            providers.provider { localProperty("keystorePassword") }
        )
    }
    publishing {
        token.set(
            providers.provider { localProperty("publishPluginToken") }
        )
    }
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}