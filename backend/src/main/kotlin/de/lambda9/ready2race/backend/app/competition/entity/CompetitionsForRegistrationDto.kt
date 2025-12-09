package de.lambda9.ready2race.backend.app.competition.entity

data class CompetitionsForRegistrationDto(
    val competitions: List<CompetitionDto>,
    val teamsEventOmitted: Boolean,
)