// A consumer project exactly as a product team would write it: nothing here knows about the specialize sources,
// the processor comes from a Maven repository (here ~/.m2 after `./gradlew publishToMavenLocal` in the root project).
plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "demo"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.specialize:specialize-api:0.1.0")
    implementation("dev.specialize:specialize-jackson:0.1.0")     // JSON null / missing → Opt.empty()
    annotationProcessor("dev.specialize:specialize-processor:0.1.0")
    testAnnotationProcessor("dev.specialize:specialize-processor:0.1.0")

    implementation("org.springframework.boot:spring-boot-starter-web")

    // Lombok next to it: both are javac-internal processors and coexist
    compileOnly("org.projectlombok:lombok:1.18.40")
    annotationProcessor("org.projectlombok:lombok:1.18.40")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    // shows the rewritten sources under build/specialize-dump, like delombok
    options.compilerArgs.add("-Aspecialize.dump=${layout.buildDirectory.get().asFile}/specialize-dump")
}

tasks.test {
    useJUnitPlatform()
}
