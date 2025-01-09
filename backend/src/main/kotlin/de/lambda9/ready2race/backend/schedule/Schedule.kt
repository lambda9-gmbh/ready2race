package de.lambda9.ready2race.backend.schedule

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 *
 */
@OptIn(DelicateCoroutinesApi::class)
fun schedulingJobs(env: JEnv) = GlobalScope.launch(Dispatchers.IO) {


}

sealed interface Policy<A> {

    data class Fixed(val interval: Duration): Policy<Unit>

    data class Dynamic<A>(val block: (A?) -> Duration): Policy<A>

}

fun <A> CoroutineScope.schedule(
    env: JEnv,
    policy: Policy<A>,
    block: suspend CoroutineScope.() -> KIO<JEnv, Nothing, A>,
): Job = launch {
    var result: A? = null
    while (true) {
        val duration = when (policy) {
            is Policy.Dynamic -> policy.block(result)
            is Policy.Fixed -> policy.interval
        }

        delay(duration)
        val exit = block().unsafeRunSync(env)
        result = exit.fold(
            onDefect = {
                null
            },
            onError = { null },
            onSuccess = { it }
        )
    }
}
