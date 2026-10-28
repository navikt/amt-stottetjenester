package no.nav.amt.person.service.config

import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

object TeamLogs {
    private val log = LoggerFactory.getLogger(javaClass)
    private val marker = MarkerFactory.getMarker("TEAM_LOGS")

    fun info(msg: String) = log.info(marker, msg)

    fun warn(
        msg: String,
        error: Throwable? = null,
    ) = log.warn(marker, msg, error)

    fun error(
        msg: String,
        error: Throwable? = null,
    ) = log.error(marker, msg, error)
}
