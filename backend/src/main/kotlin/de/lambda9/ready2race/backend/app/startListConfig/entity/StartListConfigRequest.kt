package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import kotlin.String

data class StartListConfigRequest(
    val name: String,
    val colParticipantFirstname: String?,
    val colParticipantLastname: String?,
    val colParticipantGender: String?,
    val colParticipantRole: String?,
    val colParticipantYear: String?,
    val colParticipantClub: String?,
    val colClubName: String?,
    val colTeamName: String?,
    val colTeamStartNumber: String?,
    val colMatchName: String?,
    val colMatchStartTime: String?,
    val colRoundName: String?,
    val colCompetitionIdentifier: String?,
    val colCompetitionName: String?,
    val colCompetitionShortName: String?,
    val colCompetitionCategory: String?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            ValidationResult.anyOf(
                this::colParticipantFirstname validate notNull,
                this::colParticipantLastname validate notNull,
                this::colParticipantGender validate notNull,
                this::colParticipantRole validate notNull,
                this::colParticipantYear validate notNull,
                this::colParticipantClub validate notNull,
                this::colClubName validate notNull,
                this::colTeamName validate notNull,
                this::colTeamStartNumber validate notNull,
                this::colMatchName validate notNull,
                this::colMatchStartTime validate notNull,
                this::colRoundName validate notNull,
                this::colCompetitionIdentifier validate notNull,
                this::colCompetitionName validate notNull,
                this::colCompetitionShortName validate notNull,
                this::colCompetitionCategory validate notNull,
            )
        )

    companion object {

        val example get() = StartListConfigRequest(
            name = "Einzelrennen",
            colTeamStartNumber = "Start Number",
            colParticipantFirstname = null,
            colParticipantLastname = null,
            colParticipantGender = null,
            colParticipantRole = null,
            colParticipantYear = null,
            colParticipantClub = null,
            colClubName = null,
            colTeamName = null,
            colMatchName = null,
            colMatchStartTime = null,
            colRoundName = null,
            colCompetitionIdentifier = null,
            colCompetitionName = null,
            colCompetitionShortName = null,
            colCompetitionCategory = null,
        )
    }
}
