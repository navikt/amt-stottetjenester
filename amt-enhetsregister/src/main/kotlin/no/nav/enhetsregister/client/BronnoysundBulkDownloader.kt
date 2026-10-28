package no.nav.enhetsregister.client

import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.InputStream
import java.util.zip.GZIPInputStream

@Service
class BronnoysundBulkDownloader(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {
    fun hentAlleModerenheter(): List<Moderenhet> = hentAlle(
        uri = "/enhetsregisteret/api/enheter/lastned",
        accept = MODERENHET_V1_GZIP,
        feilmelding = "Klarte ikke å laste ned moderenheter.",
    ) { body ->
        objectMapper.readValue<List<ModerenhetDto>>(body).map { it.toDomain() }
    }

    fun hentAlleUnderenheter(): List<Underenhet> = hentAlle(
        uri = "/enhetsregisteret/api/underenheter/lastned",
        accept = UNDERENHET_V1_GZIP,
        feilmelding = "Klarte ikke å laste ned underenheter.",
    ) { body ->
        objectMapper.readValue<List<UnderenhetDto>>(body).map { it.toDomain() }
    }

    private fun <T> hentAlle(
        uri: String,
        accept: MediaType,
        feilmelding: String,
        parse: (InputStream) -> T,
    ): T = restClient
        .get()
        .uri(uri)
        .accept(accept)
        .exchange { _, response ->
            if (!response.statusCode.is2xxSuccessful) {
                throw RuntimeException("$feilmelding Status: ${response.statusCode.value()}")
            }

            GZIPInputStream(response.body).use(parse)
        }

    companion object {
        private val MODERENHET_V1_GZIP =
            MediaType.parseMediaType("application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8")
        private val UNDERENHET_V1_GZIP =
            MediaType.parseMediaType("application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8")
    }
}
