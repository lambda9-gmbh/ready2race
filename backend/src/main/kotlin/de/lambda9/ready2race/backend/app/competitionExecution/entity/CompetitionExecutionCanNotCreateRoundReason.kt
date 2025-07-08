package de.lambda9.ready2race.backend.app.competitionExecution.entity

enum class CompetitionExecutionCanNotCreateRoundReason {
    ALL_ROUNDS_CREATED,
    NO_ROUNDS_IN_SETUP,
    NO_SETUP_MATCHES,
    NO_REGISTRATIONS,
    REGISTRATIONS_NOT_FINALIZED,
    NOT_ENOUGH_TEAM_SPACE,
    NOT_ALL_PLACES_SET,
}