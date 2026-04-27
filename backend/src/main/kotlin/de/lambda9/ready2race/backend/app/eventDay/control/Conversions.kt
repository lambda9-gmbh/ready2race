package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.MinimalTimeslotDurationData
import de.lambda9.ready2race.backend.app.eventDay.entity.TimeslotDto
import de.lambda9.ready2race.backend.app.eventDay.entity.TimeslotRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.TimeslotWithCompetitionDurationData
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotWithCompetitionDurationDataRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

private fun String?.toMeaningfulDescription(): String? = this?.takeIf { it.isNotBlank() }

private fun stripAutoPartFromManualDescription(
    manualDescription: String?,
    autoDescription: String?,
): String? {
    val meaningfulManualDescription = manualDescription.toMeaningfulDescription() ?: return null
    val meaningfulAutoDescription = autoDescription.toMeaningfulDescription() ?: return meaningfulManualDescription

    if (meaningfulManualDescription == meaningfulAutoDescription) {
        return null
    }

    val autoSuffix = "\n\n$meaningfulAutoDescription"
    return if (meaningfulManualDescription.endsWith(autoSuffix)) {
        meaningfulManualDescription.removeSuffix(autoSuffix).toMeaningfulDescription()
    } else {
        meaningfulManualDescription
    }
}

private fun composeTimeslotDescription(
    manualDescription: String?,
    autoDescription: String?,
): String? {
    val meaningfulManualDescription = manualDescription.toMeaningfulDescription()
    val meaningfulAutoDescription = autoDescription.toMeaningfulDescription()

    return when {
        meaningfulManualDescription != null && meaningfulAutoDescription != null ->
            "$meaningfulManualDescription\n\n$meaningfulAutoDescription"

        meaningfulManualDescription != null -> meaningfulManualDescription
        meaningfulAutoDescription != null -> meaningfulAutoDescription
        else -> null
    }
}

fun EventDayRequest.toRecord(userId: UUID, eventId: UUID): App<Nothing, EventDayRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventDayRecord(
                id = UUID.randomUUID(),
                event = eventId,
                date = date,
                name = name,
                description = description,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun EventDayRecord.eventDayDto(): App<Nothing, EventDayDto> = KIO.ok(
    EventDayDto(
        id = id,
        event = event,
        date = date,
        name = name,
        description = description,
    )
)

fun TimeslotRecord.toDto(): TimeslotDto =
    (competitionReference != null || roundReference != null || matchReference != null).let { hasReference ->
        val rawManualDescription = if (hasReference) {
            descriptionManual
        } else {
            descriptionManual ?: description
        }
        val cleanedManualDescription = stripAutoPartFromManualDescription(rawManualDescription, descriptionAuto)
        val fullDescription = composeTimeslotDescription(cleanedManualDescription, descriptionAuto) ?: description

        TimeslotDto(
            id = id,
            eventDay = eventDay,
            name = name,
            description = fullDescription,
            descriptionManual = cleanedManualDescription,
            competitionReference = competitionReference,
            roundReference = roundReference,
            matchReference = matchReference,
            startTime = startTime,
            endTime = endTime
        )
    }

fun TimeslotWithCompetitionDurationDataRecord.toDto(): TimeslotWithCompetitionDurationData =
    TimeslotWithCompetitionDurationData(
        id = id!!,
        date = date!!,
        competitionReference = competitionReference!!,
        roundReference = roundReference,
        matchReference = matchReference,
        startTime = startTime!!,
        endTime = endTime!!,
        matchDuration = matchDuration!!,
        matchGapsDuration = matchGapsDuration!!
    )

fun TimeslotWithCompetitionDurationData.toMinimalTimeslotDurationData(): MinimalTimeslotDurationData =
    MinimalTimeslotDurationData(
        date = date,
        startTime = startTime,
        matchDuration = matchDuration,
        matchGapsDuration = matchGapsDuration
    )
