package no.nav.arrangor.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorRepository.ArrangorDbo
import no.nav.arrangor.kafka.model.VirksomhetDto
import no.nav.arrangor.metrics.MetricEvent
import no.nav.arrangor.metrics.MetricEvent.MetricName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class ConsumerServiceVirksomhetTest {
    private val arrangorRepository = mockk<ArrangorRepository>()
    private val producerService = mockk<ProducerService>(relaxUnitFun = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    private val consumerService = ConsumerService(
        ansattRepository = mockk(),
        ansattService = mockk(),
        arrangorRepository = arrangorRepository,
        enhetsregisterClient = mockk(),
        eventPublisher = eventPublisher,
        producerService = producerService,
        deltakerRepository = mockk(),
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    )
    private val arrangor = ArrangorDbo(
        id = UUID.randomUUID(),
        navn = "Arrangør",
        organisasjonsnummer = "999988888",
        overordnetArrangorId = null,
    )
    private val overordnetArrangor = ArrangorDbo(
        id = UUID.randomUUID(),
        navn = "Overordnet arrangør",
        organisasjonsnummer = "888887776",
        overordnetArrangorId = null,
    )

    @Test
    fun `uendret arrangor uten overordnet arrangor lagres og publiseres ikke`() {
        // Arrange
        every { arrangorRepository.get(arrangor.organisasjonsnummer) } returns arrangor

        // Act
        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = arrangor.organisasjonsnummer,
                navn = arrangor.navn,
                overordnetEnhetOrganisasjonsnummer = null,
            ),
        )

        // Assert
        verifyIngenArrangorendring()
    }

    @Test
    fun `uendret arrangor med samme overordnede arrangor lagres og publiseres ikke`() {
        // Arrange
        val eksisterendeArrangor = arrangor.copy(overordnetArrangorId = overordnetArrangor.id)
        every { arrangorRepository.get(arrangor.organisasjonsnummer) } returns eksisterendeArrangor
        every { arrangorRepository.get(overordnetArrangor.organisasjonsnummer) } returns overordnetArrangor

        // Act
        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = arrangor.organisasjonsnummer,
                navn = arrangor.navn,
                overordnetEnhetOrganisasjonsnummer = overordnetArrangor.organisasjonsnummer,
            ),
        )

        // Assert
        verifyIngenArrangorendring()
    }

    @Test
    fun `endret navn lagres og publiserer arrangoren returnert fra databasen`() {
        // Arrange
        val oppdatertArrangor = arrangor.copy(navn = "Nytt navn")
        val arrangorFraDatabase = oppdatertArrangor.copy(id = UUID.randomUUID())
        every { arrangorRepository.get(arrangor.organisasjonsnummer) } returns arrangor
        every { arrangorRepository.insertOrUpdate(oppdatertArrangor) } returns arrangorFraDatabase

        // Act
        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = arrangor.organisasjonsnummer,
                navn = oppdatertArrangor.navn,
                overordnetEnhetOrganisasjonsnummer = null,
            ),
        )

        // Assert
        verifyArrangorendring(oppdatertArrangor, arrangorFraDatabase)
    }

    @Test
    fun `endret overordnet arrangor med uendret navn lagres og publiseres`() {
        // Arrange
        val eksisterendeArrangor = arrangor.copy(overordnetArrangorId = UUID.randomUUID())
        val oppdatertArrangor = arrangor.copy(overordnetArrangorId = overordnetArrangor.id)
        every { arrangorRepository.get(arrangor.organisasjonsnummer) } returns eksisterendeArrangor
        every { arrangorRepository.get(overordnetArrangor.organisasjonsnummer) } returns overordnetArrangor
        every { arrangorRepository.insertOrUpdate(oppdatertArrangor) } returns oppdatertArrangor

        // Act
        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = arrangor.organisasjonsnummer,
                navn = arrangor.navn,
                overordnetEnhetOrganisasjonsnummer = overordnetArrangor.organisasjonsnummer,
            ),
        )

        // Assert
        verifyArrangorendring(oppdatertArrangor, oppdatertArrangor)
    }

    @Test
    fun `fjernet overordnet arrangor med uendret navn lagres og publiseres`() {
        // Arrange
        val eksisterendeArrangor = arrangor.copy(overordnetArrangorId = overordnetArrangor.id)
        every { arrangorRepository.get(arrangor.organisasjonsnummer) } returns eksisterendeArrangor
        every { arrangorRepository.insertOrUpdate(arrangor) } returns arrangor

        // Act
        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = arrangor.organisasjonsnummer,
                navn = arrangor.navn,
                overordnetEnhetOrganisasjonsnummer = null,
            ),
        )

        // Assert
        verifyArrangorendring(arrangor, arrangor)
    }

    private fun verifyIngenArrangorendring() {
        verify(exactly = 0) { arrangorRepository.insertOrUpdate(any()) }
        verify(exactly = 0) { producerService.publishArrangor(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(MetricEvent(MetricName.ARRANGOR_CHANGED)) }
        verify(exactly = 1) { eventPublisher.publishEvent(MetricEvent(MetricName.VIRKSOMHET_EVENT_CONSUMED)) }
    }

    private fun verifyArrangorendring(
        oppdatertArrangor: ArrangorDbo,
        arrangorFraDatabase: ArrangorDbo,
    ) {
        verify(exactly = 1) { arrangorRepository.insertOrUpdate(oppdatertArrangor) }
        verify(exactly = 1) { producerService.publishArrangor(arrangorFraDatabase.toDomain()) }
        verify(exactly = 1) { eventPublisher.publishEvent(MetricEvent(MetricName.ARRANGOR_CHANGED)) }
        verify(exactly = 1) { eventPublisher.publishEvent(MetricEvent(MetricName.VIRKSOMHET_EVENT_CONSUMED)) }
    }
}
