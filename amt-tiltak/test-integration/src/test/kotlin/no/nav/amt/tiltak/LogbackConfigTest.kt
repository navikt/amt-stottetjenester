package no.nav.amt.tiltak

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogbackConfigTest {
	@Test
	fun `Kafka-logger arver ERROR fra pakken`() {
		val logger = LoggerFactory.getLogger("org.apache.kafka.clients.consumer.ConsumerConfig")

		logger.isInfoEnabled shouldBe false
		logger.isWarnEnabled shouldBe false
		logger.isErrorEnabled shouldBe true
	}

	@Test
	fun `applikasjonslogger beholder INFO`() {
		val logger = LoggerFactory.getLogger("no.nav.amt.tiltak.loggtest")

		logger.isInfoEnabled shouldBe true
		logger.isErrorEnabled shouldBe true
	}
}
