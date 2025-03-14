package de.lambda9.ready2race.backend.schedule

sealed interface FixedIntervalJobState {
    data object Running : FixedIntervalJobState
    data class Fatal(val reason: String) : FixedIntervalJobState
}