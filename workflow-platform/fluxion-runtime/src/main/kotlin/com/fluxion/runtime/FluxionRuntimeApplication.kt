package com.fluxion.runtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Fluxion Runtime (Data Plane / Worker) Spring Boot entry point.
 *
 * A deployed Worker instance is responsible for:
 * - Self-registering with the Admin control plane (via [InstanceRegistry]).
 * - Pulling and hot-reloading workflow definitions and function configs
 *   from the chosen config center (Apollo / Nacos / Admin-HTTP).
 * - Exposing published workflow execution capability through one or more
 *   transport adapters (HTTP SpringMVC / WebFlux, Dubbo, gRPC, Kafka).
 * - Acting as a lightweight sidecar for Java 8 monolith workloads that
 *   cannot embed the platform directly.
 *
 * Activation: `workflow.instance.role=worker` must be set in the environment
 * for the auto-configurations in this module to actually wire beans.
 */
@SpringBootApplication
class FluxionRuntimeApplication

fun main(args: Array<String>) {
    runApplication<FluxionRuntimeApplication>(*args)
}
