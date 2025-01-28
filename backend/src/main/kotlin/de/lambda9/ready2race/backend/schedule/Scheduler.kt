package de.lambda9.ready2race.backend.schedule

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import kotlinx.coroutines.*

class Scheduler(val env: JEnv) {

    fun <A> CoroutineScope.schedule(
        policy: Policy<A>,
        block: suspend CoroutineScope.() -> App<Nothing, A>,
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
}


