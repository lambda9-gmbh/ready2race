package de.lambda9.ready2race.backend.calls.comprehension

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.plugins.kioEnv
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import io.ktor.server.application.*

private class CallComprehensionScopeException(
    val reason: Cause<ToApiError>,
) : Exception()

class CallComprehensionScope(val call: ApplicationCall) : KIO.ComprehensionScope<JEnv, ToApiError>,
    ApplicationCall by call {
    override fun <A> KIO<JEnv, ToApiError, A>.not(): A =
        unsafeRunSync(call.kioEnv).fold(
            onSuccess = { it },
            onError = {
                throw CallComprehensionScopeException(it)
            }
        )
}

suspend fun ApplicationCall.comprehension(
    block: suspend CallComprehensionScope.() -> KIO<JEnv, ToApiError, ApiResponse>
) = try {
    block(CallComprehensionScope(this))
} catch (ex: CallComprehensionScopeException) {
    KIO.halt(ex.reason)
}
