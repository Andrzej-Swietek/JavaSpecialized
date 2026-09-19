description = "javac annotation processor that rewrites ASTs (Lombok-style) to specialize generic templates and inline methods"

val javacExports = listOf(
    "com.sun.tools.javac.tree",
    "com.sun.tools.javac.util",
    "com.sun.tools.javac.code",
    "com.sun.tools.javac.processing",
    "com.sun.tools.javac.parser",
    "com.sun.tools.javac.api",
    "com.sun.tools.javac.comp",
)

dependencies {
    api(project(":specialize-api"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    // the processor is not applied to itself
    options.compilerArgs.add("-proc:none")
    javacExports.forEach { pkg ->
        options.compilerArgs.addAll(listOf("--add-exports", "jdk.compiler/$pkg=ALL-UNNAMED"))
    }
}

tasks.test {
    // tests compile snippets in-process with javax.tools; the processor opens jdk.compiler itself,
    // this flag only silences the JDK 24+ Unsafe deprecation warning printed by that trick
    jvmArgs("--sun-misc-unsafe-memory-access=allow")
}
