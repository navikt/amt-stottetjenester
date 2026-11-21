package no.nav.amt.tiltak.test.integration

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.amt.tiltak.kafka.config.KafkaProperties
import no.nav.amt.tiltak.test.database.data.TestDataRepository
import no.nav.amt.tiltak.test.integration.utils.SingletonKafkaProvider
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.KafkaProducerClientImpl
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.CompletableFuture

@Profile("integration")
@TestConfiguration
class IntegrationTestConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true")
    fun kafkaProperties(): KafkaProperties = SingletonKafkaProvider.getKafkaProperties(ByteArraySerializer(), ByteArraySerializer())

    @Bean(name = ["stringKafkaProducer"])
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true")
    fun stringKafkaProducer(): KafkaProducerClient<String, String> {
        val properties = SingletonKafkaProvider.getKafkaProperties(StringSerializer(), StringSerializer()).producer()
        return KafkaProducerClientImpl(properties)
    }

    @Bean
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "false")
    fun kafkaProducer(): KafkaProducerClient<ByteArray, ByteArray> = mockedProducer()

    @Bean(name = ["stringKafkaProducer"])
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "false")
    fun mockedStringKafkaProducer(): KafkaProducerClient<String, String> = mockedProducer()

    private fun <K, V> mockedProducer(): KafkaProducerClient<K, V> {
        val producer = mockk<KafkaProducerClient<K, V>>()

        fun metadata(record: ProducerRecord<K, V>) =
            RecordMetadata(TopicPartition(record.topic(), record.partition() ?: 0), 0L, 0, 0L, 0, 0)

        every { producer.sendSync(any()) } answers { metadata(firstArg()) }
        every { producer.send(any()) } answers { CompletableFuture.completedFuture(metadata(firstArg())) }
        every { producer.send(any(), any()) } answers {
            val result = metadata(firstArg())
            secondArg<Callback?>()?.onCompletion(result, null)
            CompletableFuture.completedFuture(result)
        }
        every { producer.close() } just Runs
        return producer
    }

    @Bean
    fun testDataRepository(template: NamedParameterJdbcTemplate): TestDataRepository = TestDataRepository(template)

    @Bean
    fun unleashClient(): Unleash {
        val fakeUnleash = FakeUnleash()
        fakeUnleash.enableAll()
        return fakeUnleash
    }
}
