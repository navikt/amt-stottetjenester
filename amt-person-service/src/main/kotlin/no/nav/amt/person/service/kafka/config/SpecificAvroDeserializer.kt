package no.nav.amt.person.service.kafka.config

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.common.kafka.consumer.util.deserializer.AvroDeserializer

class SpecificAvroDeserializer<T>(
    schemaRegistryUrl: String,
    schemaRegistryUser: String,
    schemaRegistryPassword: String,
) : AvroDeserializer<T>(schemaRegistryUrl, schemaRegistryUser, schemaRegistryPassword) {
    init {
        this.configure(
            mapOf(
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryUrl,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
            ),
            false,
        )
    }
}
