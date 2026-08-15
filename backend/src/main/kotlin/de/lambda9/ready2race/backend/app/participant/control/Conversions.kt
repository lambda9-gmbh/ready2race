package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantClubDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSearchResultDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.CheckedParticipantRequirement
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun ParticipantUpsertDto.toRecord(userId: UUID, clubId: UUID): App<Nothing, ParticipantRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRecord(
                id = UUID.randomUUID(),
                club = clubId,
                firstname = this.firstname,
                lastname = this.lastname,
                year = this.year,
                gender = this.gender,
                phone = this.phone,
                external = this.external,
                externalClubName = this.externalClubName?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
                email = email,
            )
        }
    )

/**
 * [hideContactData] blendet Telefonnummer und E-Mail-Adresse aus.
 *
 * Gesetzt wird es, wenn ein Zweitverein die Person nur als Gast in seiner Liste sieht (siehe
 * Migration V202608142000): melden darf er sie, ihre Kontaktdaten gehen ihn nichts an. Der
 * Stammverein und wer global lesen darf, sehen den vollen Datensatz.
 */
fun ParticipantViewRecord.participantDto(hideContactData: Boolean = false): App<Nothing, ParticipantDto> = KIO.ok(
    ParticipantDto(
        id = id!!,
        firstname = firstname!!,
        lastname = lastname!!,
        year = year,
        gender = gender!!,
        phone = phone.takeIf { !hideContactData },
        external = external,
        externalClubName = externalClubName,
        usedInRegistration = usedInRegistration!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        email = email.takeIf { !hideContactData },
        clubId = club!!,
        clubName = clubName!!,
        additionalClubs = additionalClubs(),
    )
)

/**
 * Die beiden Array-Spalten aus `participant_view` paarweise zusammenlegen. Beide sind in der
 * Sicht nach Vereinsname sortiert, deshalb tragen die Positionen zusammen; `zip` kappt zudem
 * einen etwaigen Längenunterschied, statt an einem `!!` zu sterben.
 */
private fun ParticipantViewRecord.additionalClubs(): List<ParticipantClubDto> {
    val ids = additionalClubIds?.filterNotNull() ?: emptyList()
    val names = additionalClubNames?.filterNotNull() ?: emptyList()
    return ids.zip(names) { id, name -> ParticipantClubDto(id = id, name = name) }
}

/**
 * Der schmale Treffer der vereinsübergreifenden Suche. Er entsteht bewusst aus einer eigenen
 * Abbildung und nicht aus [participantDto] mit ausgeblendeten Feldern: so kann kein später
 * hinzugefügtes Feld von [ParticipantDto] versehentlich in die Suche durchschlagen.
 */
fun ParticipantViewRecord.participantSearchResultDto(): App<Nothing, ParticipantSearchResultDto> = KIO.ok(
    ParticipantSearchResultDto(
        id = id!!,
        firstname = firstname!!,
        lastname = lastname!!,
        year = year,
        gender = gender!!,
        clubName = externalClubName?.takeIf { external == true } ?: clubName!!,
    )
)

fun SubstitutionViewRecord.participantInToParticipantForEventDto(
    namedParticipantIds: List<UUID>,
    participantRequirementsChecked: List<CheckedParticipantRequirement>,
    qrCode: String?,
): App<Nothing, ParticipantForEventDto> = KIO.ok(
    ParticipantForEventDto(
        id = participantIn!!.id,
        clubId = clubId!!,
        clubName = clubName!!,
        firstname = participantIn!!.firstname,
        lastname = participantIn!!.lastname,
        year = participantIn!!.year,
        gender = participantIn!!.gender,
        external = participantIn!!.external,
        externalClubName = participantIn!!.externalClubName,
        participantRequirementsChecked = participantRequirementsChecked,
        namedParticipantIds = namedParticipantIds,
        qrCodeId = qrCode,
        email = participantIn!!.email,
        hasChallengeResults = null,
    )
)

fun ParticipantForEventRecord.toDto(
    overwriteNamedParticipantIds: List<UUID>?,
): App<Nothing, ParticipantForEventDto> = KIO.ok(
    ParticipantForEventDto(
        id = id!!,
        clubId = clubId!!,
        clubName = clubName!!,
        firstname = firstname!!,
        lastname = lastname!!,
        year = year!!,
        gender = gender!!,
        external = external,
        externalClubName = externalClubName,
        participantRequirementsChecked = participantRequirementsChecked!!.map{ reqChecked ->
            CheckedParticipantRequirement(
                id = reqChecked!!.id!!,
                note = reqChecked.note,
                // Die beiden Dimensionen müssen mit (V202608141900): Die Scan-App fragt nicht
                // "gibt es einen Haken?", sondern "deckt er DIESEN Wettkampf an DIESEM Tag ab?".
                // Ohne sie kam die Antwort für eine Bedingung mit Schaltern immer "nein" - die
                // Bestätigung an der Waage war geschrieben, das Häkchen erschien trotzdem nie.
                eventDayId = reqChecked.eventDay,
                competitionId = reqChecked.competition,
            )
        },
        namedParticipantIds = overwriteNamedParticipantIds ?: namedParticipantIds!!.filterNotNull(),
        qrCodeId = qrCodeId,
        email = email,
        hasChallengeResults = hasChallengeResults,
    )
)