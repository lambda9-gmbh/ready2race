package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.time.LocalDateTime
import java.util.UUID

/** Was mit einer Bestätigung geschah: gesetzt oder zurückgenommen. */
enum class ParticipantRequirementLogAction { APPROVED, REVOKED }

/**
 * Woher die Änderung kam. Genau diese Unterscheidung fehlte am Regattatag: Ein an der Waage
 * gesetzter Haken und ein im Büro gesetzter sehen im Bestand gleich aus, und als Bestätigungen
 * verschwanden, ließ sich nicht sagen, welcher Weg sie genommen hat.
 */
enum class ParticipantRequirementLogSource {
    /** Die Scan-App - ein Bändchen an der Waage oder in der Meldestelle. */
    SCAN,

    /** Der Abgleich im Verwaltungs-UI, der den vollständigen Zustand einer Bedingung ersetzt. */
    BULK,

    /** Der Datei-Import einer Bedingung. */
    IMPORT,
}

/**
 * Ein Eintrag der Revisionsspur, wie ihn die Verwaltung liest.
 *
 * Die Namen stehen ausgeschrieben drin und werden nicht nachgeschlagen: Der Eintrag soll auch dann
 * lesbar bleiben, wenn die Erfüllungszeile längst gelöscht ist - das ist der Fall, um den es geht.
 */
data class ParticipantRequirementLogEntryDto(
    val id: UUID,
    val participantId: UUID,
    val participantName: String,
    val clubName: String?,
    val requirementId: UUID,
    val requirementName: String,
    val action: ParticipantRequirementLogAction,
    val source: ParticipantRequirementLogSource,
    val competitionId: UUID?,
    val competitionName: String?,
    val eventDayId: UUID?,
    val eventDayDate: java.time.LocalDate?,
    val note: String?,
    val createdAt: LocalDateTime,
    val createdBy: String?,
)
