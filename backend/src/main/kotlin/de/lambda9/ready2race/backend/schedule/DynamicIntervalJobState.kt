package de.lambda9.ready2race.backend.schedule

sealed interface DynamicIntervalJobState {
    data object Initial : DynamicIntervalJobState
    data object Empty : DynamicIntervalJobState
    data object Processed : DynamicIntervalJobState
    data object Defect : DynamicIntervalJobState
    data class Fatal(val reason: String) : DynamicIntervalJobState
}