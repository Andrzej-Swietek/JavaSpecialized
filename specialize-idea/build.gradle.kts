import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

description = "IntelliJ IDEA support for the specialize processor: Opt<int> without red squiggles, bridge methods visible in completion"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

// `-PpluginVersion=1.2.3` (CI passes the tag); the zip keeps its plain name so install paths stay stable.
val pluginVersion: String = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

intellijPlatform {
    pluginConfiguration {
        id = "dev.specialize.idea"
        name = "Specialize"
        version = pluginVersion
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

// A private plugin repository needs only this XML next to the zip on any HTTP server (Nexus, Artifactory, S3):
// Settings → Plugins → ⚙ → Manage Plugin Repositories → the URL of updatePlugins.xml. No Marketplace involved.
val pluginRepositoryUrl = providers.gradleProperty("pluginRepositoryUrl").orElse("https://repo.example.com/idea-plugins")

tasks.register("updatePluginsXml") {
    description = "Writes build/distributions/updatePlugins.xml for a private plugin repository"
    dependsOn(tasks.named("buildPlugin"))
    val output = layout.buildDirectory.file("distributions/updatePlugins.xml")
    val url = pluginRepositoryUrl
    val version = provider { pluginVersion }
    inputs.property("url", url)
    inputs.property("version", version)
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plugins>
              <plugin id="dev.specialize.idea" url="${url.get()}/specialize-idea.zip" version="${version.get()}">
                <idea-version since-build="251"/>
                <name>Specialize</name>
                <description>IDE support for the dev.specialize annotation processor</description>
              </plugin>
            </plugins>
            """.trimIndent()
        )
    }
}
