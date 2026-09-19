description = "Annotations (@Specialize, @Specialized, @Inline, @Boxed) and the Prim helper used by specialization templates"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
