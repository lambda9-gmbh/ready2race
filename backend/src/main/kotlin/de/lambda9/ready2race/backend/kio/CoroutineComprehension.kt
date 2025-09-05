package de.lambda9.ready2race.backend.kio

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.plugins.kioEnv
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope

private class CoroutineComprehensionScopeException(
    val reason: Cause<Any?>,
) : Exception()

class CoroutineComprehensionScope<E>(val scope: CoroutineScope, val env: JEnv) : KIO.ComprehensionScope<JEnv, E>,
    CoroutineScope by scope {
    override fun <A> KIO<JEnv, E, A>.not(): A =
        unsafeRunSync(env).fold(
            onSuccess = { it },
            onError = {
                throw CoroutineComprehensionScopeException(it)
            }
        )
}

suspend fun <E, A> CoroutineScope.comprehension(
    env: JEnv,
    block: suspend CoroutineComprehensionScope<E>.() -> KIO<JEnv, E, A>
) = try {
    block(CoroutineComprehensionScope(this, env))
} catch (ex: CoroutineComprehensionScopeException) {
    @Suppress("UNCHECKED_CAST")
    KIO.halt(ex.reason as Cause<E>)
}
