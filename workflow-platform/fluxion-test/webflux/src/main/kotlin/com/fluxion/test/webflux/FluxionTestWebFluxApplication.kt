package com.fluxion.test.webflux

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Minimal Spring Boot entry point for the WebFlux integration-test harness.
 *
 * Exercises:
 *  - `fluxion-adapter-http:webflux` reactive HTTP adapter
 *  - Direct `HandlerMapping` lookup (no `RouterFunction` indirection)
 *  - End-to-end execution of the workflow engine on a reactive stack
 *
 * Intentionally skips heavyweight deps (DB, JPA, Security) so it boots in
 * well under a second for fast TDD iterations.
 */
@SpringBootApplication
class FluxionTestWebFluxApplication

fun main(args: Array<String>) {
    runApplication<FluxionTestWebFluxApplication>(*args)
}
