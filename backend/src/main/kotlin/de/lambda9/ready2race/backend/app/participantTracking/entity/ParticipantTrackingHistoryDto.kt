package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Eintrag im Verlauf einer Person, so wie ihn Admin und Schiedsrichter sehen.
 *
 * [source] und [editCount] zusammen ergeben die drei Fälle, die die Oberfläche unterscheiden
 * muss: per QR erfasst (QR, 0), von Hand angelegt (MANUAL), per QR erfasst und danach berichtigt
 * (QR, >0).
 */
data class ParticipantTrackingEntryDto(
    val id: UUID,
    val scanType: ParticipantScanType,
    val scannedAt: LocalDateTime,
    val source: ParticipantTrackingSource,
    /** Wer gescannt oder den Eintrag angelegt hat. */
    val recordedBy: AppUserNameDto?,
    val editCount: Int,
    val lastEditedAt: LocalDateTime?,
    val lastEditedBy: AppUserNameDto?,
)

/** Eine Zeile der Änderungsspur: was galt vorher, was gilt jetzt, warum, von wem und wann. */
data class ParticipantTrackingChangeDto(
    val id: UUID,
    /** Null, wenn der zugehörige Eintrag nicht mehr existiert - die Spur überlebt ihn. */
    val trackingId: UUID?,
    val changeType: ParticipantTrackingChangeType,
    val previousScanType: ParticipantScanType?,
    val previousScannedAt: LocalDateTime?,
    val newScanType: ParticipantScanType,
    val newScannedAt: LocalDateTime,
    val reason: String,
    val createdAt: LocalDateTime,
    val createdBy: AppUserNameDto?,
)

/**
 * Verlauf und Änderungsspur einer Person in einer Veranstaltung.
 *
 * Der Name der Person steht hier nicht: der Dialog wird immer aus einer Zeile heraus geöffnet, die
 * ihn bereits zeigt. Nur die Angaben, die es sonst nirgends gibt.
 */
data class ParticipantTrackingHistoryDto(
    val entries: List<ParticipantTrackingEntryDto>,
    val changes: List<ParticipantTrackingChangeDto>,
)
