package no.nav.arrangor

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@AutoConfigureMockMvc
abstract class ControllerTestBase : IntegrationTest() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    protected fun sendRequest(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): TestResponse {
        val request = request(
            org.springframework.http.HttpMethod
                .valueOf(method),
            URLDecoder.decode(path, StandardCharsets.UTF_8),
        )
        body?.let {
            request.content(it)
            request.contentType(MediaType.APPLICATION_JSON)
        }
        headers.forEach { (name, value) -> request.header(name, value) }

        return mockMvc
            .perform(request)
            .andReturn()
            .response
            .let(::TestResponse)
    }
}
