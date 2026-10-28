package no.nav.amt.person.service.person

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.person.dbo.PersonDbo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClientException

@SpringJUnitConfig(classes = [PersonServiceRetryTestConfiguration::class])
class PersonServiceRetryTest
    @Autowired
    constructor(
        private val personService: PersonService,
        private val pdlClient: PdlClient,
        private val personRepository: PersonRepository,
        private val personidentRepository: PersonidentRepository,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @BeforeEach
        fun setUp() {
            clearMocks(
                pdlClient,
                personRepository,
                personidentRepository,
                transactionTemplate,
                answers = true,
                recordedCalls = true,
            )
            AopUtils.isAopProxy(personService) shouldBe true
        }

        @Test
        fun `retries retryable upstream errors three times`() {
            val personident = TestData.randomIdent()
            val exception = retryableUpstreamException()
            every { personRepository.get(personident) } returns null
            every { pdlClient.hentPerson(personident) } throws exception

            shouldThrow<RetryableUpstreamServiceException> {
                personService.hentEllerOpprettPerson(personident)
            } shouldBe exception

            verify(exactly = 3) { pdlClient.hentPerson(personident) }
        }

        @Test
        fun `retries database errors three times`() {
            val personident = TestData.randomIdent()
            val exception = DataAccessResourceFailureException("database unavailable")
            every { personRepository.get(personident) } throws exception

            shouldThrow<DataAccessResourceFailureException> {
                personService.hentEllerOpprettPerson(personident)
            } shouldBe exception

            verify(exactly = 3) { personRepository.get(personident) }
            verify(exactly = 0) { pdlClient.hentPerson(any()) }
        }

        @Test
        fun `retries transaction errors three times`() {
            val personident = TestData.randomIdent()
            val exception = TransactionSystemException("transaction failed")
            every { personRepository.get(personident) } returns null
            every { pdlClient.hentPerson(personident) } returns TestData.lagPdlPerson(
                TestData.lagPerson(personident = personident),
            )
            every { transactionTemplate.execute<PersonDbo>(any()) } throws exception

            shouldThrow<TransactionSystemException> {
                personService.hentEllerOpprettPerson(personident)
            } shouldBe exception

            verify(exactly = 3) { transactionTemplate.execute<PersonDbo>(any()) }
        }

        @Test
        fun `does not retry non-retryable upstream errors`() {
            val personident = TestData.randomIdent()
            val exception = UpstreamServiceException(
                serviceName = "PDL",
                operation = "hent person",
                statusCode = 400,
                cause = RestClientException("bad request"),
            )
            every { personRepository.get(personident) } returns null
            every { pdlClient.hentPerson(personident) } throws exception

            shouldThrow<UpstreamServiceException> {
                personService.hentEllerOpprettPerson(personident)
            } shouldBe exception

            verify(exactly = 1) { pdlClient.hentPerson(personident) }
        }

        @Test
        fun `does not retry GraphQL errors`() {
            val personident = TestData.randomIdent()
            val exception = RuntimeException("Feilmeldinger i respons fra pdl")
            every { personRepository.get(personident) } returns null
            every { pdlClient.hentPerson(personident) } throws exception

            shouldThrow<RuntimeException> {
                personService.hentEllerOpprettPerson(personident)
            } shouldBe exception

            verify(exactly = 1) { pdlClient.hentPerson(personident) }
        }
    }

@TestConfiguration(proxyBeanMethods = false)
@EnableResilientMethods
@Import(PersonService::class)
class PersonServiceRetryTestConfiguration {
    @Bean
    fun pdlClient(): PdlClient = mockk()

    @Bean
    fun personRepository(): PersonRepository = mockk()

    @Bean
    fun personidentRepository(): PersonidentRepository = mockk()

    @Bean
    fun transactionTemplate(): TransactionTemplate = mockk(relaxed = true)
}

private fun retryableUpstreamException() = RetryableUpstreamServiceException(
    serviceName = "PDL",
    operation = "hent person",
    statusCode = 503,
    cause = RestClientException("temporary failure"),
)
