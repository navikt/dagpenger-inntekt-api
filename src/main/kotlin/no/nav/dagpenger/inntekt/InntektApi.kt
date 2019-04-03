package no.nav.dagpenger.inntekt

import com.ryanharter.ktor.moshi.moshi
import com.squareup.moshi.JsonEncodingException
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.pipeline.PipelineContext
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging
import no.nav.dagpenger.inntekt.oidc.StsOidcClient
import no.nav.dagpenger.inntekt.v1.beregningsdato
import no.nav.dagpenger.inntekt.v1.inntekt
import org.slf4j.event.Level
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

fun main() {
    val env = Environment()

    val inntektskomponentHttpClient = InntektskomponentHttpClient(
        env.hentinntektListeUrl,
        StsOidcClient(env.oicdStsUrl, env.username, env.password)
    )

    DefaultExports.initialize()
    val application = embeddedServer(Netty, port = env.httpPort) {
        inntektApi(inntektskomponentHttpClient)
    }
    application.start(wait = false)
    Runtime.getRuntime().addShutdownHook(Thread {
        application.stop(5, 60, TimeUnit.SECONDS)
    })
}

fun Application.inntektApi(inntektskomponentHttpClient: InntektskomponentClient) {

    install(DefaultHeaders)

    install(StatusPages) {
        exception<BadRequestException> { cause ->
            badRequest(cause)
        }
        exception<Throwable> { cause ->
            LOGGER.error("Request failed!", cause)
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch inntekt")
        }
        exception<InntektskomponentenHttpClientException> { cause ->
            LOGGER.error("Request failed against inntektskomponenet", cause)
            call.respond(HttpStatusCode.fromValue(cause.status), cause.message)
        }
        exception<JsonEncodingException> { cause ->
            LOGGER.error("Bad input", cause)
            call.respond(HttpStatusCode.BadRequest, "Request was malformed")
        }
    }
    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            !call.request.path().startsWith("/isAlive") &&
                !call.request.path().startsWith("/isReady") &&
                !call.request.path().startsWith("/metrics")
        }
    }
    install(ContentNegotiation) {
        moshi(moshiInstance)
    }

    routing {
        inntekt(inntektskomponentHttpClient)
        beregningsdato()
        naischecks()
    }
}

private suspend fun <T : Throwable> PipelineContext<Unit, ApplicationCall>.badRequest(
    cause: T
) {
    call.respond(HttpStatusCode.BadRequest)
    throw cause
}

class BadRequestException : RuntimeException()