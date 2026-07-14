package com.fluxion.config.core

import org.slf4j.*
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractConfigSubscriber<L> {

    protected val log = LoggerFactory.getLogger(javaClass)

    private val listeners = CopyOnWriteArrayList<L>()

    protected fun addListener(listener: L) {
        listeners.add(listener)
    }

    protected fun listeners(): List<L> = listeners.toList()

    protected inline fun notifyListeners(action: (L) -> Unit) {
        for (listener in listeners()) {
            try {
                action(listener)
            } catch (e: Exception) {
                log.error(e) { "Config listener error" }
            }
        }
    }
}
