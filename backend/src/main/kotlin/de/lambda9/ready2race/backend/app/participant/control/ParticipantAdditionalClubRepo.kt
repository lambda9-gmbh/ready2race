package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantAdditionalClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_ADDITIONAL_CLUB
import de.lambda9.ready2race.backend.database.insert
import java.util.*

/**
 * Die weiteren Vereine einer Person (Migration V202608142000).
 *
 * Bewusst schmal: anlegen, entfernen, nachsehen. Gelesen wird die Zugehörigkeit sonst nirgends
 * über dieses Repo, sondern als Bedingung mitten in den bestehenden Abfragen
 * (`ParticipantRepo.belongsToClub`) — sonst müsste jede Personenliste erst alle Zugehörigkeiten
 * holen und dann in Kotlin filtern.
 */
object ParticipantAdditionalClubRepo {

    fun create(record: ParticipantAdditionalClubRecord) = PARTICIPANT_ADDITIONAL_CLUB.insert(record)

    fun exists(participantId: UUID, clubId: UUID) = PARTICIPANT_ADDITIONAL_CLUB.exists {
        PARTICIPANT.eq(participantId).and(CLUB.eq(clubId))
    }

    fun delete(participantId: UUID, clubId: UUID) = PARTICIPANT_ADDITIONAL_CLUB.delete {
        PARTICIPANT.eq(participantId).and(CLUB.eq(clubId))
    }
}
