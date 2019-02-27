package no.nav.dagpenger.inntekt.v1

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.RegexPattern
import no.nav.dagpenger.inntekt.InntektskomponentHttpClient
import no.nav.dagpenger.inntekt.oidc.OidcClient
import no.nav.dagpenger.inntekt.oidc.OidcToken
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals

class InntektskomponentHttpClientTest {

    @Rule
    @JvmField
    var wireMockRule = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    class DummyOidcClient : OidcClient {
        override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
    }

    @Test
    fun `fetch klassifisert inntekt on 200 ok`() {
        val body = InntektskomponentHttpClientTest::class.java
            .getResource("/test-data/example-inntekt-payload.json").readText()

        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/v1/hentinntektliste"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val inntektskomponentClient = InntektskomponentHttpClient(
            wireMockRule.url("/v1/hentinntektliste"),
            DummyOidcClient()
        )

        val hentInntektListeResponse =
            inntektskomponentClient.getInntekt("", YearMonth.of(2017, 3), YearMonth.of(2019, 1))

        assertEquals("99999999999", hentInntektListeResponse.ident.identifikator)
    }
}