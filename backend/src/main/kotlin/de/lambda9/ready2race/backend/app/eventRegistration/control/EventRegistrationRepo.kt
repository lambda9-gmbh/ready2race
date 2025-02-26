package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationDayDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationNamedParticipantDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationRaceDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationTemplateDto
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object EventRegistrationRepo {

    fun getEvenRegistrationTemplate(eventId: UUID): JIO<EventRegistrationTemplateDto?> = Jooq.query {

        val eventDays = DSL.select(
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

        val raceDays = DSL.select(
            EVENT_DAY_HAS_RACE.EVENT_DAY,
        )
            .from(EVENT_DAY_HAS_RACE)
            .where(EVENT_DAY_HAS_RACE.RACE.eq(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID))
            .asMultiset("raceDays")
            .convertFrom { it.map { it[EVENT_DAY_HAS_RACE.EVENT_DAY]!! } }

        val namedParticipants = DSL.select(
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.ID,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.NAME,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.DESCRIPTION,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.REQUIRED,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MALES,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_FEMALES,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_NON_BINARY,
            NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MIXED
        )
            .from(NAMED_PARTICIPANT_FOR_RACE_PROPERTIES)
            .join(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT).on(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(
                NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.ID))
            .join(RACE_PROPERTIES).on(RACE_PROPERTIES.ID.eq(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT.RACE_PROPERTIES))
            .where(RACE_PROPERTIES.RACE.eq(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID))
            .asMultiset("namedParticipants")
            .convertFrom {
                it!!.map {
                    EventRegistrationNamedParticipantDto(
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.ID]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.NAME]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.DESCRIPTION],
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.REQUIRED]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MALES]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_FEMALES]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_NON_BINARY]!!,
                        it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MIXED]!!,
                    )
                }
            }


        val racesSingle = DSL.select(
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.PARTICIPATION_FEE,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.RENTAL_FEE,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME,
//            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAMED_PARTICIPANTS,
            namedParticipants,
            raceDays
        )
            .from(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS)
            .where(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.EVENT.eq(EVENT.ID))
            .and(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.TOTAL_COUNT.eq(1))
            .asMultiset("racesSingle")
            .convertFrom {
                it.map {
                    EventRegistrationRaceDto(
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.PARTICIPATION_FEE]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.RENTAL_FEE],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME],
//                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAMED_PARTICIPANTS]!!.mapNotNull {
//                            EventRegistrationNamedParticipantDto(
//                                it!![NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.ID]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.NAME]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.DESCRIPTION],
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.REQUIRED]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MALES]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_FEMALES]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_NON_BINARY]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MIXED]!!,
//                            )
//                        },
                        it[namedParticipants],
                        it[raceDays]
                    )
                }
            }

        val racesTeam = DSL.select(
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.PARTICIPATION_FEE,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.RENTAL_FEE,
            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME,
//            RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAMED_PARTICIPANTS,
            namedParticipants,
            raceDays
        )
            .from(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS)
            .where(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.EVENT.eq(EVENT.ID))
            .and(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.TOTAL_COUNT.greaterThan(1))
            .asMultiset("racesTeam")
            .convertFrom {
                it.map {
                    EventRegistrationRaceDto(
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.DESCRIPTION],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MALES]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_FEMALES]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_NON_BINARY]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.COUNT_MIXED]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.PARTICIPATION_FEE]!!,
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.RENTAL_FEE],
                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME],
//                        it[RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAMED_PARTICIPANTS]!!.mapNotNull {
//                            EventRegistrationNamedParticipantDto(
//                                it!![NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.ID]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.NAME]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.DESCRIPTION],
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.REQUIRED]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MALES]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_FEMALES]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_NON_BINARY]!!,
//                                it[NAMED_PARTICIPANT_FOR_RACE_PROPERTIES.COUNT_MIXED]!!,
//                            )
//                        },
                        it[namedParticipants],
                        it[raceDays]
                    )
                }
            }

        select(
            EVENT.NAME,
            EVENT.DESCRIPTION,
            EVENT.LOCATION,
            eventDays,
            racesSingle,
            racesTeam
        )
            .from(EVENT)
            .where(EVENT.ID.eq(eventId))
            .fetch {
                EventRegistrationTemplateDto(
                    it[EVENT.NAME]!!,
                    it[EVENT.DESCRIPTION],
                    it[EVENT.LOCATION],
                    it[eventDays],
                    it[racesSingle],
                    it[racesTeam],
                )
            }.firstOrNull()

    }


}