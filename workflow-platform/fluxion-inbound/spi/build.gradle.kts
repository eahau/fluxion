// fluxion-inbound:spi - Inbound adapter SPI contracts module.
// Hosts protocol-agnostic request/response/router types (UnifiedRequest / UnifiedResponse /
// InboundRouter) and event-driven trigger interfaces. Formerly lived inside
// fluxion-adapter-spi; extracted to its own SPI surface so all inbound adapters
// (http/rpc/mq) depend on this clean module without pulling in unrelated capabilities.
plugins {
    kotlin("jvm")
    `java-library`
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

dependencies {
    api(project(":fluxion-core"))
}
