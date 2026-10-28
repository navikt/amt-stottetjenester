package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.SkjermetPersonConsumer
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import org.junit.jupiter.api.Test

class SkjermetPersonConsumerTest(
    private val navBrukerRepository: NavBrukerRepository,
    private val skjermetPersonConsumer: SkjermetPersonConsumer,
) : IntegrationTestBase() {
    @Test
    fun `ingest - bruker finnes - skal oppdatere med skjermingsdata`() {
        val navBruker = TestData.lagNavBruker(erSkjermet = false)
        testDataRepository.insertNavBruker(navBruker)

        skjermetPersonConsumer.ingest(navBruker.person.personident, "true")

        val faktiskBruker = navBrukerRepository.get(navBruker.id)
        faktiskBruker.erSkjermet shouldBe true
    }

    @Test
    fun `ingest tombstone - bruker finnes - skal kaste exception`() {
        val navBruker = TestData.lagNavBruker(erSkjermet = false)
        testDataRepository.insertNavBruker(navBruker)

        shouldThrow<IllegalArgumentException> {
            skjermetPersonConsumer.ingestTombstone(navBruker.person.personident)
        }
    }
}
