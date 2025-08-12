package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
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
    val colTeamRatingCategory: String?,
    val colTeamClub: String?,
    val colTeamDeregistered: String?,
    val valueTeamDeregistered: String?,
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
            this::colParticipantFirstname validate notBlank,
            this::colParticipantLastname validate notBlank,
            this::colParticipantGender validate notBlank,
            this::colParticipantRole validate notBlank,
            this::colParticipantYear validate notBlank,
            this::colParticipantClub validate notBlank,
            this::colClubName validate notBlank,
            this::colTeamName validate notBlank,
            this::colTeamStartNumber validate notBlank,
            this::colTeamRatingCategory validate notBlank,
            this::colTeamClub validate notBlank,
            this::colTeamDeregistered validate notBlank,
            this::valueTeamDeregistered validate notBlank,
            this::colMatchName validate notBlank,
            this::colMatchStartTime validate notBlank,
            this::colRoundName validate notBlank,
            this::colCompetitionIdentifier validate notBlank,
            this::colCompetitionName validate notBlank,
            this::colCompetitionShortName validate notBlank,
            this::colCompetitionCategory validate notBlank,
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
                this::colTeamRatingCategory validate notNull,
                this::colTeamClub validate notNull,
                this::colTeamDeregistered validate notNull,
                this::valueTeamDeregistered validate notNull,
                this::colMatchName validate notNull,
                this::colMatchStartTime validate notNull,
                this::colRoundName validate notNull,
                this::colCompetitionIdentifier validate notNull,
                this::colCompetitionName validate notNull,
                this::colCompetitionShortName validate notNull,
                this::colCompetitionCategory validate notNull,
            ),
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
            colTeamRatingCategory = null,
            colTeamClub = null,
            colTeamDeregistered = null,
            valueTeamDeregistered = null,
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
