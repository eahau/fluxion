plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":fluxion-registry:spring-boot"))
    api(project(":fluxion-discovery:spring-boot"))
    api("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
}