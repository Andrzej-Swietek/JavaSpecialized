// JMH benchmarks against the published artifacts (run `./gradlew publishToMavenLocal` in the root project first).
plugins {
    java
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    jmhImplementation("dev.specialize:specialize-api:0.1.0")
    jmhImplementation("dev.specialize:specialize-examples:0.1.0")
    jmhAnnotationProcessor("dev.specialize:specialize-processor:0.1.0")   // next to JMH's own generator
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

jmh {
    jmhVersion = "1.37"
    fork = 1
    warmupIterations = 5
    iterations = 5
    timeOnIteration = "1s"
    warmup = "1s"
    resultFormat = "TEXT"
}
