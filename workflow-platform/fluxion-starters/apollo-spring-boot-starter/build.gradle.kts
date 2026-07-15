plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":fluxion-config:apollo"))
    api("com.ctrip.framework.apollo:apollo-client")
    api("com.ctrip.framework.apollo:apollo-openapi")
}