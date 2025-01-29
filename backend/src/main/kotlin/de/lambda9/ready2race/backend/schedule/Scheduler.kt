package de.lambda9.ready2race.backend.schedule

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.fold
import de.lambda9.ready2race.backend.onDefect
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Scheduler(val env: JEnv) {

    private val logger = KotlinLogging.logger {}

    fun CoroutineScope.scheduleFixed(
        interval: Duration,
        block: suspend CoroutineScope.() -> App<Nothing, Any?>
    ): Job = launch {
        while (true) {
            delay(interval)
            block().unsafeRunSync(env).onDefect { logger.error(it) { "Error executing scheduled Job" } }
        }
    }

    fun CoroutineScope.scheduleDynamic(
        emptyDelay: Duration,
        processedDelay: Duration = Duration.ZERO,
        defectDelay: Duration = 1.minutes,
        initialDelay: Duration = Duration.ZERO,
        block: suspend CoroutineScope.() -> App<Nothing, JobQueueState>,
    ): Job = launch {
        var state: JobQueueState = JobQueueState.INITIAL
        while (true) {
            val duration = when (state) {
                JobQueueState.INITIAL -> initialDelay
                JobQueueState.EMPTY -> emptyDelay
                JobQueueState.PROCESSED -> processedDelay
                JobQueueState.DEFECT -> defectDelay
            }
            delay(duration)
            val exit = block().unsafeRunSync(env)
            state = exit.fold(
                onDefect = {
                    logger.error(it) { "Error executing scheduled Job" }
                    JobQueueState.DEFECT
                },
                onSuccess = { it }
            )
        }
    }
}
