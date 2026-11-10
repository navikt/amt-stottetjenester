package no.nav.test.amt.altinn.acl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(
    classes = [ProblemDetailsProbeApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
class ProblemDetailsErrorFallbackTest
    @Autowired
    constructor(
        @LocalServerPort private val port: Int,
    ) {
        private val httpClient = HttpClient.newHttpClient()

        @Test
        fun `standard MVC-feil blir returnert som ProblemDetail`() {
            val response = get("/probe")

            assertEquals(405, response.statusCode())
            assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElse(null))
        }

        @Test
        fun `NoSuchElementException går til standard 500-respons`() {
            val response = get("/probe/no-such-element")

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.statusCode())
        }

        @Test
        fun `IllegalArgumentException går til standard 500-respons`() {
            val response = get("/probe/illegal-argument")

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.statusCode())
        }

        private fun get(path: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

@SpringBootConfiguration
@EnableAutoConfiguration(
    excludeName = [
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
    ],
)
@Import(ProblemDetailsProbeController::class, ProblemDetailsProbeSecurity::class)
class ProblemDetailsProbeApplication

@RestController
class ProblemDetailsProbeController {
    @PostMapping("/probe")
    fun postOnly() = Unit

    @GetMapping("/probe/no-such-element")
    fun noSuchElement(): Nothing = throw NoSuchElementException("missing")

    @GetMapping("/probe/illegal-argument")
    fun illegalArgument(): Nothing = throw IllegalArgumentException("invalid")
}

@Configuration(proxyBeanMethods = false)
class ProblemDetailsProbeSecurity {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }
}
