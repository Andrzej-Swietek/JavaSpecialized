subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    group = "dev.specialize"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        // -source/-target instead of --release: --release forbids --add-exports for the processor module
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        withSourcesJar()
    }

    // `./gradlew publishToMavenLocal` puts dev.specialize:specialize-api, :specialize-processor, :specialize-jackson
    // and :specialize-examples into ~/.m2, where any Gradle or Maven project (see samples/) can pick them up.
    run {
        apply(plugin = "maven-publish")
        val registry = providers.environmentVariable("MAVEN_REPO_URL")
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name = project.name
                        description = provider { project.description }
                    }
                }
            }
            // `MAVEN_REPO_URL=https://… ./gradlew publishAllPublicationsToRemoteRepository` publishes to any
            // Maven repository; without the variable the build has no remote repository at all.
            if (registry.isPresent) {
                repositories {
                    maven {
                        name = "Remote"
                        url = uri(registry.get())
                        credentials {
                            username = providers.environmentVariable("MAVEN_REPO_USER").orNull
                            password = providers.environmentVariable("MAVEN_REPO_TOKEN").orNull
                        }
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:-options")
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required = true
            html.required = true
        }
    }

    // 100% line and branch coverage is required; generated specializations carry @GeneratedSpecialization and are
    // filtered out by JaCoCo automatically (annotation name contains "Generated", retention CLASS).
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
