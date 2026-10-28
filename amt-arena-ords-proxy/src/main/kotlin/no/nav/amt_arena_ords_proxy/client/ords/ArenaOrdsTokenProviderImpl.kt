package no.nav.amt_arena_ords_proxy.client.ords

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

class ArenaOrdsTokenProviderImpl(
	private val clientId: String,
	private val clientSecret: String,
	private val arenaOrdsUrl: String,
	private val objectMapper: ObjectMapper,
	private val httpClient: OkHttpClient = baseClient(),
) : ArenaOrdsTokenProvider {
	// Consider the token expired before actual expiration to allow for clock skew, network lag etc...
	private val expireEarlySeconds = 30L

	private var cachedToken: CachedOrdsToken? = null

	override fun getArenaOrdsToken(): String {
		if (isMissingOrExpired(cachedToken)) {
			cachedToken = CachedOrdsToken(fetchNewOrdsToken())
		}

		return cachedToken!!.token.accessToken
	}

	private fun fetchNewOrdsToken(): OrdsToken {
		val request =
			Request
				.Builder()
				.url("$arenaOrdsUrl/arena/api/oauth/token")
				.addHeader("Authorization", Credentials.basic(clientId, clientSecret))
				.post("grant_type=client_credentials".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
				.build()

		httpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("Failed to refresh ORDS token, status: ${response.code}")
			}

			val body = response.body.string()

			return objectMapper.readValue(body, OrdsToken::class.java)
		}
	}

	private fun isMissingOrExpired(cachedOrdsToken: CachedOrdsToken?): Boolean {
		if (cachedOrdsToken == null) {
			return true
		}

		val expiresAt =
			cachedOrdsToken.cachedAt
				.plusSeconds(cachedOrdsToken.token.expiresIn)
				.minusSeconds(expireEarlySeconds)

		return LocalDateTime.now().isAfter(expiresAt)
	}

	private data class CachedOrdsToken(
		val token: OrdsToken,
		val cachedAt: LocalDateTime = LocalDateTime.now(),
	)

	private data class OrdsToken(
		@JsonAlias("access_token")
		val accessToken: String,
		@JsonAlias("expires_in")
		val expiresIn: Long,
	)
}
