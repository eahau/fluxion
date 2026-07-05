plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-adapter-spi"))

    // Groovy 脚本引擎
    implementation("org.apache.groovy:groovy")

    // Caffeine 编译缓存
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.networknt:json-schema-validator")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
