package no.nav.amt_arena_ords_proxy.config

import no.nav.amt_arena_ords_proxy.client.ords.ArenaOrdsClient
import no.nav.amt_arena_ords_proxy.client.ords.ArenaOrdsClientImpl
import no.nav.amt_arena_ords_proxy.client.ords.ArenaOrdsTokenProvider
import no.nav.amt_arena_ords_proxy.client.ords.ArenaOrdsTokenProviderImpl
import no.nav.common.rest.filter.LogRequestFilter
import no.nav.common.utils.EnvironmentUtils
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.ObjectMapper

@Configuration
@Profile("!local")
@EnableJwtTokenValidation
@EnableConfigurationProperties(EnvironmentProperties::class)
class ApplicationConfig {
	@Bean
	fun arenaOrdsTokenProvider(
		environmentProperties: EnvironmentProperties,
		objectMapper: ObjectMapper,
	): ArenaOrdsTokenProvider =
		ArenaOrdsTokenProviderImpl(
			clientId = environmentProperties.arenaOrdsClientId,
			clientSecret = environmentProperties.arenaOrdsClientSecret,
			arenaOrdsUrl = environmentProperties.arenaOrdsUrl,
			objectMapper = objectMapper,
		)

	@Bean
	fun arenaOrdsClient(
		environmentProperties: EnvironmentProperties,
		arenaOrdsTokenProvider: ArenaOrdsTokenProvider,
		objectMapper: ObjectMapper,
	): ArenaOrdsClient =
		ArenaOrdsClientImpl(
			tokenProvider = arenaOrdsTokenProvider,
			arenaOrdsUrl = environmentProperties.arenaOrdsUrl,
			objectMapper = objectMapper,
		)

	@Bean
	fun logFilterRegistrationBean(): FilterRegistrationBean<LogRequestFilter> {
		val registration = FilterRegistrationBean<LogRequestFilter>()
		registration.setFilter(
			LogRequestFilter(
				EnvironmentUtils.requireApplicationName(),
				EnvironmentUtils.isDevelopment().orElse(false),
			),
		)
		registration.order = 1
		registration.addUrlPatterns("/*")
		return registration
	}
}
