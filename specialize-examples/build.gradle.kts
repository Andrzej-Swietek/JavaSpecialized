description = "Opt<T> (modelled after AVSystem commons Opt[A]) specialized to primitives, explicit specializations and @Inline math"

dependencies {
    // This is all a consumer project needs.
    implementation(project(":specialize-api"))
    annotationProcessor(project(":specialize-processor"))
    testAnnotationProcessor(project(":specialize-processor"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    // Like delombok: the sources exactly as the processor rewrote them land in build/specialize-dump
    options.compilerArgs.add("-Aspecialize.dump=${layout.buildDirectory.get().asFile}/specialize-dump")
}

tasks.withType<JavaCompile>().configureEach {
    // Optional: makes the compiler output free of the JDK 24+ sun.misc.Unsafe deprecation warning that
    // the processor's Lombok-style module opener triggers. Without it everything still works.
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("--sun-misc-unsafe-memory-access=allow")
}
