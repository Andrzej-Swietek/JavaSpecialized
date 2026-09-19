description = "Jackson module: specializations (OptInt, …) and their templates deserialize JSON null / missing fields into their @Absent value"

dependencies {
    api(project(":specialize-api"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testAnnotationProcessor(project(":specialize-processor"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("--sun-misc-unsafe-memory-access=allow")
}
