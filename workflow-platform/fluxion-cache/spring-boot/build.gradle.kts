plugins {
    kotlin("jvm")
    id("org.springframework.boot") apply false
}

dependencies {
    implementation(project(":fluxion-cache"))
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-config:spring-boot"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    compileOnly(project(":fluxion-config:nacos"))
    compileOnly(project(":fluxion-config:apollo"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
