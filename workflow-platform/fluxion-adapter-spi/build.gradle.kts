dependencies {
    api(project(":fluxion-core"))
    implementation("org.slf4j:slf4j-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
