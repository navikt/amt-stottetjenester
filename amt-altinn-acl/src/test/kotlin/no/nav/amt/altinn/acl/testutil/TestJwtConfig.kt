package no.nav.amt.altinn.acl.testutil

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * Erstatter den nettverksbaserte JwtDecoder-en (som ellers ville hentet JWKS fra issuer-uri
 * ved oppstart) med en lokal nøkkel. Testene kan dermed signere egne tokens uten å kjøre
 * en OAuth2-server ved siden av.
 *
 * Valideringen er den samme som i produksjon: signatur, utløpstid, issuer og audience.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestJwtConfig {
    private val rsaKey = RSAKeyGenerator(2048).keyID("test-key").generate()

    @Bean
    fun jwtEncoder() = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))

    @Bean
    fun jwtDecoder(
        @Value($$"${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String,
        @Value($$"${spring.security.oauth2.resourceserver.jwt.audiences}") audiences: String,
    ): JwtDecoder = NimbusJwtDecoder
        .withPublicKey(rsaKey.toRSAPublicKey())
        .build()
        .apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(),
                    JwtIssuerValidator(issuerUri),
                    JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud ->
                        aud.any { it in audiences.split(',').map(String::trim) }
                    },
                ),
            )
        }
}
