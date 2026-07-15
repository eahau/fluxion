plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":fluxion-config:nacos"))
    api(project(":fluxion-registry:spring-boot"))
    api(project(":fluxion-discovery:spring-boot"))
    api("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")
    api("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config")
}