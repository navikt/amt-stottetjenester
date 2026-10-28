package no.nav.enhetsregister.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import no.nav.enhetsregister.service.OppdaterAlleEnheterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor

@WebMvcTest(controllers = [OppdaterAlleEnheterController::class])
class OppdaterAlleEnheterControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        @MockkBean private val oppdaterAlleEnheterService: OppdaterAlleEnheterService,
    ) : ControllerTestBase() {

        @Test
        fun `avviser kall fra ekstern adresse`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/moderenheter")
                            .with(remoteAddress("10.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(401, response.status)
            verify(exactly = 0) { oppdaterAlleEnheterService.oppdaterAlleModerenheter() }
        }

        @Test
        fun `godtar kall til moderenheter fra localhost`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/moderenheter")
                            .with(remoteAddress("127.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(200, response.status)
            verify(timeout = 1_000, exactly = 1) { oppdaterAlleEnheterService.oppdaterAlleModerenheter() }
        }

        @Test
        fun `avviser kall til moderenheter fra ekstern adresse med gyldig token`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/moderenheter")
                            .header("Authorization", "Bearer ${bearerToken()}")
                            .with(remoteAddress("10.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(403, response.status)
            verify(exactly = 0) { oppdaterAlleEnheterService.oppdaterAlleModerenheter() }
        }

        @Test
        fun `avviser kall til underenheter fra ekstern adresse`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/underenheter")
                            .with(remoteAddress("10.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(401, response.status)
            verify(exactly = 0) { oppdaterAlleEnheterService.oppdaterAlleUnderenheter() }
        }

        @Test
        fun `godtar kall til underenheter fra localhost`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/underenheter")
                            .with(remoteAddress("127.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(200, response.status)
            verify(timeout = 1_000, exactly = 1) { oppdaterAlleEnheterService.oppdaterAlleUnderenheter() }
        }

        @Test
        fun `avviser kall til underenheter fra ekstern adresse med gyldig token`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/local/api/oppdater-alle/underenheter")
                            .header("Authorization", "Bearer ${bearerToken()}")
                            .with(remoteAddress("10.0.0.1")),
                    ).andReturn()
                    .response

            assertEquals(403, response.status)
            verify(exactly = 0) { oppdaterAlleEnheterService.oppdaterAlleUnderenheter() }
        }

        private fun remoteAddress(address: String) = RequestPostProcessor { request ->
            request.remoteAddr = address
            request
        }
    }
