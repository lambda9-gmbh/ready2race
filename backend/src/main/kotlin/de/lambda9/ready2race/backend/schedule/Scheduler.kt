package de.lambda9.ready2race.backend.schedule

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.kio.fold
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Scheduler(val env: JEnv) {

    fun CoroutineScope.scheduleFixed(
        name: String,
        interval: Duration,
        retryAfterDefect: Duration = interval,
        block: suspend CoroutineScope.() -> App<Nothing, Any?>
    ): Job = scheduleDynamic(
        name = name,
        emptyDelay = interval,
        processedDelay = interval,
        defectDelay = retryAfterDefect,
        initialDelay = interval,
    ) {
        block().map {
            when (it) {
                is FixedIntervalJobState -> {
                    when (it) {
                        is FixedIntervalJobState.Fatal -> DynamicIntervalJobState.Fatal(it.reason)
                        FixedIntervalJobState.Running -> DynamicIntervalJobState.Processed
                    }
                }

                is DynamicIntervalJobState -> it

                else -> DynamicIntervalJobState.Processed
            }
        }
    }

    fun CoroutineScope.scheduleDynamic(
        name: String,
        emptyDelay: Duration,
        processedDelay: Duration = Duration.ZERO,
        defectDelay: Duration = 1.minutes,
        initialDelay: Duration = Duration.ZERO,
        block: suspend CoroutineScope.() -> App<Nothing, DynamicIntervalJobState>,
    ): Job = launch {
        var state: DynamicIntervalJobState = DynamicIntervalJobState.Initial
        while (true) {
            val duration = when (val curState = state) {
                DynamicIntervalJobState.Initial -> initialDelay
                DynamicIntervalJobState.Empty -> emptyDelay
                DynamicIntervalJobState.Processed -> processedDelay
                DynamicIntervalJobState.Defect -> defectDelay
                is DynamicIntervalJobState.Fatal -> {
                    logger.error { "Scheduled job '$name' stopped due to fatal error:\n ${curState.reason}." }
                    return@launch
                }
            }
            delay(duration)
            val exit = block().unsafeRunSync(env)
            state = exit.fold(
                onDefect = {
                    logger.error(it) { "Scheduled job '$name' threw an unexpected exception." }
                    DynamicIntervalJobState.Defect
                },
                onSuccess = { it }
            )
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
