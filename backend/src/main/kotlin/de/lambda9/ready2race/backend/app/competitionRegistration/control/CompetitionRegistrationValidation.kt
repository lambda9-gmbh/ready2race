package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.ratingcategory.control.EventRatingCategoryViewRepo
import de.lambda9.ready2race.backend.app.ratingcategory.entity.AgeRestriction
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

/**
 * Shared validation logic for competition registrations.
 * Used across AppUserService, EventRegistrationService, and related registration flows.
 */
object CompetitionRegistrationValidation {

    /**
     * Validates that all provided competitions are single-participant competitions.
     * A single competition has exactly one participant slot (sum of all gender counts = 1).
     *
     * @param competitions List of CompetitionViewRecord to validate
     * @return Success if all are single competitions, failure otherwise
     */
    fun validateSingleCompetitions(
        competitions: List<CompetitionViewRecord>
    ): App<EventRegistrationError, Unit> = KIO.comprehension {
        val namedParticipants = competitions.flatMap { it.namedParticipants!!.filterNotNull() }
        !KIO.failOn(
            namedParticipants.size != competitions.size
                || namedParticipants.map {
                    it.countMales!! + it.countFemales!! + it.countNonBinary!! + it.countMixed!!
                }.any { it > 1 }
        ) {
            EventRegistrationError.SelfRegistrationOnlyForSingleCompetitions
        }
        KIO.unit
    }

    /**
     * Retrieves rating category age restrictions for an event.
     *
     * @param eventId The event ID
     * @return Map of rating category ID to age restriction, and whether any restrictions exist
     */
    fun getRatingCategoryRestrictions(
        eventId: UUID
    ): App<Nothing, Pair<Map<UUID, AgeRestriction>, Boolean>> = KIO.comprehension {
        val ratingCategoryAgeRestrictions = !EventRatingCategoryViewRepo.get(eventId).orDie()
            .map { list ->
                list.associate {
                    it.ratingCategory!! to AgeRestriction(
                        from = it.yearRestrictionFrom,
                        to = it.yearRestrictionTo
                    )
                }
            }
        val ratingCategoryExistsForEvent = ratingCategoryAgeRestrictions.isNotEmpty()
        KIO.ok(ratingCategoryAgeRestrictions to ratingCategoryExistsForEvent)
    }

    /**
     * Validates that a rating category is provided when required.
     *
     * @param ratingCategoryExistsForEvent Whether the event has rating categories
     * @param competitionRatingCategoryRequired Whether the competition requires a rating category
     * @param providedRatingCategory The rating category provided (null if not provided)
     * @param teamName Name of team/participant for error message
     * @param competitionName Name of competition for error message
     * @return Success if valid, failure if missing when required
     */
    fun validateRatingCategoryRequired(
        ratingCategoryExistsForEvent: Boolean,
        competitionRatingCategoryRequired: Boolean,
        providedRatingCategory: UUID?,
        teamName: String,
        competitionName: String
    ): App<EventRegistrationError, Unit> = KIO.comprehension {
        !KIO.failOn(
            ratingCategoryExistsForEvent
                && competitionRatingCategoryRequired
                && providedRatingCategory == null
        ) {
            EventRegistrationError.RatingCategoryMissing(
                teamName = teamName,
                competitionName = competitionName
            )
        }
        KIO.unit
    }

    /**
     * Validates that a participant's birth year meets the age restrictions of a rating category.
     *
     * @param birthYear Participant's birth year
     * @param ratingCategoryId Rating category ID to validate against
     * @param ratingCategoryRestrictions Map of rating category restrictions
     * @param participantName Participant name for error message (null if team)
     * @param teamName Team name for error message (null if individual)
     * @param competitionName Competition name for error message
     * @return Success if age requirement met, failure otherwise
     */
    fun validateAgeRestriction(
        birthYear: Int,
        ratingCategoryId: UUID,
        ratingCategoryRestrictions: Map<UUID, AgeRestriction>,
        participantName: String?,
        teamName: String?,
        competitionName: String
    ): App<EventRegistrationError, Unit> = KIO.comprehension {
        val ageRestriction = !KIO.failOnNull(ratingCategoryRestrictions[ratingCategoryId]) {
            EventRegistrationError.RatingCategoryNotFound(
                id = ratingCategoryId,
                competitionName = competitionName
            )
        }

        val isValid =
            ((ageRestriction.from != null && ageRestriction.from <= birthYear) || ageRestriction.from == null) &&
                ((ageRestriction.to != null && ageRestriction.to >= birthYear) || ageRestriction.to == null)

        !KIO.failOn(!isValid) {
            EventRegistrationError.AgeRequirementNotMet(
                participantName = participantName,
                competitionName = competitionName,
                teamName = teamName
            )
        }
        KIO.unit
    }

    /**
     * Validates multiple participants' birth years against a rating category.
     * Useful for team registrations.
     *
     * @param birthYears List of birth years to validate
     * @param ratingCategoryId Rating category ID to validate against
     * @param ratingCategoryRestrictions Map of rating category restrictions
     * @param teamName Team name for error message
     * @param competitionName Competition name for error message
     * @return Success if all participants meet age requirements, failure otherwise
     */
    fun validateAgeRestrictionForTeam(
        birthYears: List<Int>,
        ratingCategoryId: UUID,
        ratingCategoryRestrictions: Map<UUID, AgeRestriction>,
        teamName: String?,
        competitionName: String
    ): App<EventRegistrationError, Unit> = KIO.comprehension {
        val ageRestriction = !KIO.failOnNull(ratingCategoryRestrictions[ratingCategoryId]) {
            EventRegistrationError.RatingCategoryNotFound(
                id = ratingCategoryId,
                competitionName = competitionName
            )
        }

        birthYears.forEach { year ->
            val isValid =
                ((ageRestriction.from != null && ageRestriction.from <= year) || ageRestriction.from == null) &&
                    ((ageRestriction.to != null && ageRestriction.to >= year) || ageRestriction.to == null)

            !KIO.failOn(!isValid) {
                EventRegistrationError.AgeRequirementNotMet(
                    participantName = null,
                    teamName = teamName,
                    competitionName = competitionName
                )
            }
        }
        KIO.unit
    }

    /**
     * Validates that an optional fee exists for a competition.
     *
     * @param feeId Fee ID to validate
     * @param competitionFees List of competition fee IDs that are optional
     * @param competitionName Competition name for error message
     * @return Success if fee is valid, failure if not found
     */
    fun validateOptionalFee(
        feeId: UUID,
        competitionFees: List<UUID>,
        competitionName: String
    ): App<EventRegistrationError, Unit> = KIO.comprehension {
        !KIO.failOn(!competitionFees.contains(feeId)) {
            EventRegistrationError.FeeNotFound(
                id = feeId,
                competitionName = competitionName
            )
        }
        KIO.unit
    }

    /**
     * Validates that all competitions belong to the same event and match the requested count.
     *
     * @param expectedCount Expected number of competitions
     * @param actualCompetitions Actual competitions found (CompetitionViewRecord)
     * @param eventId Expected event ID (optional, if null only count is validated)
     * @return Success if valid, failure if count mismatch or different events
     */
    fun validateCompetitionConsistency(
        expectedCount: Int,
        actualCompetitions: List<CompetitionViewRecord>,
        eventId: UUID? = null
    ): App<CompetitionError, Unit> = KIO.comprehension {
        !KIO.failOn(
            expectedCount != actualCompetitions.size
                || (eventId != null && actualCompetitions.any { it.event != eventId })
        ) {
            CompetitionError.CompetitionNotFound
        }
        KIO.unit
    }
}