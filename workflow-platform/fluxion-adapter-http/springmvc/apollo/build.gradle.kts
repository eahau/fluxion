plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-adapter-http:core"))
    implementation("com.ctrip.framework.apollo:apollo-client")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")
}

