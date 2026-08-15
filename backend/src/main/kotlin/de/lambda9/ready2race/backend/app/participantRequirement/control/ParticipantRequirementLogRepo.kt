package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementLogAction
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementLogEntryDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementLogSource
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementLogRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT_LOG
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

object ParticipantRequirementLogRepo {

    /**
     * Schreibt Einträge der Revisionsspur.
     *
     * Bewusst als Liste: Der Abgleich im Verwaltungs-UI ändert viele Personen auf einmal, und die
     * Spur soll dabei nicht je Person eine eigene Anweisung kosten.
     */
    fun create(records: List<ParticipantRequirementLogRecord>) = PARTICIPANT_REQUIREMENT_LOG.insert(records)

    /**
     * Baut einen Eintrag. Die Kennung entsteht hier und nicht in der Datenbank, wie überall sonst
     * im Projekt.
     */
    fun entry(
        eventId: UUID,
        participantId: UUID,
        requirementId: UUID,
        action: ParticipantRequirementLogAction,
        source: ParticipantRequirementLogSource,
        eventDayId: UUID?,
        competitionId: UUID?,
        note: String?,
        userId: UUID,
        at: LocalDateTime = LocalDateTime.now(),
    ) = ParticipantRequirementLogRecord(
        id = UUID.randomUUID(),
        event = eventId,
        participant = participantId,
        participantRequirement = requirementId,
        eventDay = eventDayId,
        competition = competitionId,
        action = action.name,
        source = source.name,
        note = note,
        createdAt = at,
        createdBy = userId,
    )

    /**
     * Die Spur einer Veranstaltung, neueste zuerst.
     *
     * [requirementId] und [participantId] grenzen ein; ohne beides kommt alles. Die Namen werden
     * mitgelesen und nicht nachgeschlagen - der Sinn der Spur ist gerade der Fall, in dem die
     * Erfüllungszeile nicht mehr existiert.
     */
    fun getForEvent(
        eventId: UUID,
        requirementId: UUID?,
        participantId: UUID?,
        limit: Int,
    ): JIO<List<ParticipantRequirementLogEntryDto>> = Jooq.query {
        select(
            PARTICIPANT_REQUIREMENT_LOG.ID,
            PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT,
            PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT_REQUIREMENT,
            PARTICIPANT_REQUIREMENT_LOG.ACTION,
            PARTICIPANT_REQUIREMENT_LOG.SOURCE,
            PARTICIPANT_REQUIREMENT_LOG.COMPETITION,
            PARTICIPANT_REQUIREMENT_LOG.EVENT_DAY,
            PARTICIPANT_REQUIREMENT_LOG.NOTE,
            PARTICIPANT_REQUIREMENT_LOG.CREATED_AT,
            PARTICIPANT.FIRSTNAME,
            PARTICIPANT.LASTNAME,
            PARTICIPANT.EXTERNAL_CLUB_NAME,
            CLUB.NAME,
            PARTICIPANT_REQUIREMENT.NAME,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.SHORT_NAME,
            COMPETITION_PROPERTIES.NAME,
            EVENT_DAY.DATE,
            APP_USER.FIRSTNAME.`as`("user_firstname"),
            APP_USER.LASTNAME.`as`("user_lastname"),
        )
            .from(PARTICIPANT_REQUIREMENT_LOG)
            .join(PARTICIPANT).on(PARTICIPANT.ID.eq(PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT))
            .leftJoin(CLUB).on(CLUB.ID.eq(PARTICIPANT.CLUB))
            .join(PARTICIPANT_REQUIREMENT)
            .on(PARTICIPANT_REQUIREMENT.ID.eq(PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT_REQUIREMENT))
            .leftJoin(COMPETITION).on(COMPETITION.ID.eq(PARTICIPANT_REQUIREMENT_LOG.COMPETITION))
            .leftJoin(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(PARTICIPANT_REQUIREMENT_LOG.EVENT_DAY))
            .leftJoin(APP_USER).on(APP_USER.ID.eq(PARTICIPANT_REQUIREMENT_LOG.CREATED_BY))
            .where(
                DSL.and(
                    PARTICIPANT_REQUIREMENT_LOG.EVENT.eq(eventId),
                    if (requirementId != null) {
                        PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT_REQUIREMENT.eq(requirementId)
                    } else DSL.trueCondition(),
                    if (participantId != null) {
                        PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT.eq(participantId)
                    } else DSL.trueCondition(),
                )
            )
            .orderBy(PARTICIPANT_REQUIREMENT_LOG.CREATED_AT.desc())
            .limit(limit)
            .fetch { r ->
                val userName = listOfNotNull(
                    r.get("user_firstname", String::class.java),
                    r.get("user_lastname", String::class.java),
                ).joinToString(" ").ifEmpty { null }

                ParticipantRequirementLogEntryDto(
                    id = r[PARTICIPANT_REQUIREMENT_LOG.ID]!!,
                    participantId = r[PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT]!!,
                    participantName = "${r[PARTICIPANT.FIRSTNAME]} ${r[PARTICIPANT.LASTNAME]}",
                    clubName = r[PARTICIPANT.EXTERNAL_CLUB_NAME] ?: r[CLUB.NAME],
                    requirementId = r[PARTICIPANT_REQUIREMENT_LOG.PARTICIPANT_REQUIREMENT]!!,
                    requirementName = r[PARTICIPANT_REQUIREMENT.NAME]!!,
                    action = ParticipantRequirementLogAction.valueOf(r[PARTICIPANT_REQUIREMENT_LOG.ACTION]!!),
                    source = ParticipantRequirementLogSource.valueOf(r[PARTICIPANT_REQUIREMENT_LOG.SOURCE]!!),
                    competitionId = r[PARTICIPANT_REQUIREMENT_LOG.COMPETITION],
                    competitionName = r[COMPETITION_PROPERTIES.COMPETITION]?.let {
                        listOfNotNull(
                            r[COMPETITION_PROPERTIES.IDENTIFIER],
                            r[COMPETITION_PROPERTIES.SHORT_NAME] ?: r[COMPETITION_PROPERTIES.NAME],
                        ).joinToString(" ").ifEmpty { null }
                    },
                    eventDayId = r[PARTICIPANT_REQUIREMENT_LOG.EVENT_DAY],
                    eventDayDate = r[EVENT_DAY.DATE],
                    note = r[PARTICIPANT_REQUIREMENT_LOG.NOTE],
                    createdAt = r[PARTICIPANT_REQUIREMENT_LOG.CREATED_AT]!!,
                    createdBy = userName,
                )
            }
    }
}
