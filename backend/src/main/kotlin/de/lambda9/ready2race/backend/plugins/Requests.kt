package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetInitRequest
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// todo: @refactor: remove as middleware and implement own version
fun Application.configureRequests() {
    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveKIO(LoginRequest.example).unsafeRunSync().getOrThrow().email
            }
        }
        register(RateLimitName("resetPassword")){
            rateLimiter(limit = 5, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveKIO(PasswordResetInitRequest.example).unsafeRunSync().getOrThrow().email
            }
        }
        // Notbremse für die öffentlichen Info-Endpoints (Athleten-Anzeige, Kiosk-Daten),
        // kein Feinsteuerungsinstrument: Auf einer Regatta teilen sich viele Telefone eine
        // IP (Vereins-WLAN, Carrier-NAT), und steht die Anwendung hinter einem Proxy ohne
        // Forwarded-Header, fallen sogar alle Zuschauer auf einen Schlüssel zusammen.
        // Die Grenze liegt deshalb bewusst weit über jedem legitimen Aufkommen
        // (500 Telefone im 15-Sekunden-Takt sind ~33 Anfragen/s) und fängt nur Amok
        // laufende Clients und stumpfes Hämmern ab. Die eigentliche Lastdeckelung leistet
        // der Zwischenspeicher in EventInfoService.
        register(RateLimitName("publicInfo")) {
            rateLimiter(limit = 500, refillPeriod = 5.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}