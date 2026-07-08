// fluxion-acl-spi - Access Control List (ACL) Service Provider Interface module.
// Defines permission, role, and authentication interfaces that admin console applications
// implement for pluggable security (LDAP, OAuth2, custom database-backed auth, etc.).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // SLF4J logging facade - ACL SPI implementations typically log auth events.
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
