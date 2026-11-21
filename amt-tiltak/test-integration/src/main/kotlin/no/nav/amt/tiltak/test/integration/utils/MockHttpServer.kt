package no.nav.amt.tiltak.test.integration.utils

import okhttp3.Headers
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.slf4j.LoggerFactory

private val requestBodyCache = mutableMapOf<RecordedRequest, String>()

fun RecordedRequest.getBodyAsString(): String = requestBodyCache.getOrPut(this) { this.body.readUtf8() }

open class MockHttpServer(
    private val name: String,
) {
    private val server = MockWebServer()

    private val log = LoggerFactory.getLogger(javaClass)

    private var lastRequestCount = 0

    private val responses = mutableMapOf<(request: RecordedRequest) -> Boolean, MockResponse>()

    fun start() {
        try {
            server.start()

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = responses.entries.find { it.key.invoke(request) }?.value
                        ?: throw IllegalStateException(
                            "$name: Mock has no handler for $request\n" +
                                "	Headers: \n${printHeaders(request.headers)}\n" +
                                "	Body: ${request.getBodyAsString()}",
                        )

                    log.info("Responding [${request.method}: ${request.path}]: $response")
                    return response
                }
            }
        } catch (_: IllegalArgumentException) {
            log.info("${javaClass.simpleName} is already started")
        }
    }

    fun addResponseHandler(
        predicate: (req: RecordedRequest) -> Boolean,
        response: MockResponse,
    ) {
        responses[predicate] = response
    }

    fun addResponseHandler(
        path: String,
        response: MockResponse,
    ) {
        val predicate = { req: RecordedRequest -> req.path == path }
        addResponseHandler(predicate, response)
    }

    fun reset() {
        responses.clear()
        lastRequestCount = server.requestCount
    }

    fun serverUrl(): String = server.url("").toString().removeSuffix("/")

    fun requestCount(): Int = server.requestCount - lastRequestCount

    private fun printHeaders(headers: Headers): String = headers.joinToString("\n") { "		${it.first} : ${it.second}" }
}
