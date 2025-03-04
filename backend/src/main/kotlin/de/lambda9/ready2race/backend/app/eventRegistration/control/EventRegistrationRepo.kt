package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*

object EventRegistrationRepo {

    fun getEvenRegistrationTemplate(eventId: UUID): JIO<EventRegistrationTemplateDto?> = Jooq.query {

        val eventDays = selectEventDays()

        val competitionDays = selectCompetitionDays()

        val fees = selectFees()

        val namedParticipants = selectNamedParticipants()

        val competitionsSingle = selectCompetitions(
            namedParticipants,
            competitionDays,
            fees,
            COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.TOTAL_COUNT.eq(1),
            "competitionsSingle"
        )

        val competitionsTeam = selectCompetitions(
            namedParticipants,
            competitionDays,
            fees,
            COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.TOTAL_COUNT.greaterThan(1),
            "competitionTeam"
        )

        select(
            EVENT.NAME,
            EVENT.DESCRIPTION,
            EVENT.LOCATION,
            eventDays,
            competitionsSingle,
            competitionsTeam
        )
            .from(EVENT)
            .where(EVENT.ID.eq(eventId))
            .fetch {
                EventRegistrationTemplateDto(
                    it[EVENT.NAME]!!,
                    it[EVENT.DESCRIPTION],
                    it[EVENT.LOCATION],
                    it[eventDays],
                    it[competitionsSingle],
                    it[competitionsTeam],
                )
            }.firstOrNull()

    }

    private fun selectCompetitions(
        namedParticipants: Field<MutableList<EventRegistrationNamedParticipantDto>>,
        competitionDays: Field<MutableList<UUID>>,
        fees: Field<MutableList<EventRegistrationFeeDto>>,
        condition: Condition,
        alias: String
    ) = DSL.select(
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED,
        COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME,
        namedParticipants,
        fees,
        competitionDays,
    )
        .from(COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS)
        .where(COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.EVENT.eq(EVENT.ID))
        .and(condition)
        .asMultiset(alias)
        .convertFrom {
            it.map {
                EventRegistrationCompetitionDto(
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME],
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION],
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED]!!,
                    it[COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME],
                    it[namedParticipants],
                    it[fees],
                    it[competitionDays]
                )
            }
        }

    private fun selectNamedParticipants() = DSL.select(
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.ID,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.NAME,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.DESCRIPTION,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.REQUIRED,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MALES,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_FEMALES,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_NON_BINARY,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MIXED
    )
        .from(NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES)
        .join(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT).on(
            COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(
                NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.ID
            )
        )
        .join(COMPETITION_PROPERTIES)
        .on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.COMPETITION_PROPERTIES))
        .where(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID))
        .asMultiset("namedParticipants")
        .convertFrom {
            it!!.map {
                EventRegistrationNamedParticipantDto(
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.ID]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.NAME]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.DESCRIPTION],
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.REQUIRED]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MALES]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_FEMALES]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_NON_BINARY]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MIXED]!!,
                )
            }
        }

    private fun selectFees() = DSL.select(
        FEE_FOR_COMPETITION.ID,
        FEE_FOR_COMPETITION.LABEL,
        FEE_FOR_COMPETITION.DESCRIPTION,
        FEE_FOR_COMPETITION.REQUIRED,
        FEE_FOR_COMPETITION.AMOUNT,
    )
        .from(FEE_FOR_COMPETITION)
        .where(COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID.eq(FEE_FOR_COMPETITION.COMPETITION_ID))
        .asMultiset("fees")
        .convertFrom {
            it!!.map {
                EventRegistrationFeeDto(
                    it[FEE_FOR_COMPETITION.ID]!!,
                    it[FEE_FOR_COMPETITION.LABEL]!!,
                    it[FEE_FOR_COMPETITION.DESCRIPTION],
                    it[FEE_FOR_COMPETITION.REQUIRED]!!,
                    it[FEE_FOR_COMPETITION.AMOUNT]!!
                )
            }
        }

    private fun selectCompetitionDays() = DSL.select(
        EVENT_DAY_HAS_COMPETITION.EVENT_DAY,
    )
        .from(EVENT_DAY_HAS_COMPETITION)
        .where(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID))
        .asMultiset("competitionDays")
        .convertFrom { it.map { it[EVENT_DAY_HAS_COMPETITION.EVENT_DAY]!! } }

    private fun selectEventDays() = DSL.select(
        EVENT_DAY.ID,
        EVENT_DAY.NAME,
        EVENT_DAY.DATE,
        EVENT_DAY.DESCRIPTION
    )
        .from(EVENT_DAY)
        .where(EVENT_DAY.EVENT.eq(EVENT.ID))
        .asMultiset("eventDays")
        .convertFrom {
            it.map {
                EventRegistrationDayDto(
                    it[EVENT_DAY.ID]!!,
                    it[EVENT_DAY.DATE]!!,
                    it[EVENT_DAY.NAME],
                    it[EVENT_DAY.DESCRIPTION],
                )
            }
        }


}