package de.lambda9.ready2race.backend.app.participantTracking.boundary

import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Reihenfolge am Steg als reine Funktion, ohne Datenbank.
 *
 * Bis zum manuellen Nachtrag genügte ein Blick auf den jüngsten Scan: neue Einträge kamen immer
 * hinten an, "aktueller Status" und "letzte Zeile" waren dasselbe. Ein Eintrag mitten in der
 * Historie oder eine verschobene Uhrzeit heben das auf.
 *
 * [validateSequence] prüft deshalb die ganze Kette: chronologisch sortiert muss sie mit
 * [ParticipantScanType.ENTRY] beginnen und danach abwechseln. Das ist dieselbe Regel wie vorher,
 * nur an jeder Stelle statt nur am Ende - fachlich begründet dadurch, dass ENTRY "in der Arena"
 * bedeutet: ein EXIT ohne vorheriges ENTRY wäre eine Rückkehr aus einer Arena, in der die Person
 * nie war.
 *
 * Solange die Abwechslung lückenlos gilt, bricht eine Einfügung ohnehin schon beim direkten
 * Nachfolger - insofern leistet die Ganzketten-Prüfung hier nicht mehr als eine sorgfältige
 * Nachbarschaftsprüfung. Ihr Gewinn liegt woanders: sie setzt die lückenlose Abwechslung nicht
 * voraus, sondern stellt sie fest. Historien aus der Zeit vor dieser Regel, per SQL eingespielte
 * Testdaten und alles, was künftig an der Anwendung vorbei geschrieben wird, fallen beim ersten
 * Anfassen auf, statt still weitergeschleppt zu werden.
 */
object ParticipantTrackingLogic {

    /** Ein Eintrag, reduziert auf das, was die Reihenfolge bestimmt. */
    data class Entry(
        val id: UUID,
        val scanType: ParticipantScanType,
        val scannedAt: LocalDateTime,
    )

    sealed interface SequenceViolation {

        /**
         * Zwei Einträge derselben Person auf denselben Zeitpunkt. Ihre Reihenfolge wäre nicht
         * bestimmt - und damit auch nicht die Frage, ob die Person am Ende auf dem Wasser ist.
         */
        data class Collision(val at: LocalDateTime) : SequenceViolation

        /** Ein Eintrag, der an seiner Stelle in der Kette nicht stehen kann. */
        data class OutOfOrder(
            val id: UUID,
            val at: LocalDateTime,
            val scanType: ParticipantScanType,
            val expected: ParticipantScanType,
        ) : SequenceViolation
    }

    /**
     * Prüft die vollständige Historie einer Person in einer Veranstaltung. Der Aufrufer stellt sie
     * so zusammen, wie sie *nach* der geplanten Änderung aussähe, und schreibt nur, wenn hier
     * `null` herauskommt.
     *
     * Gibt den ersten Verstoß zurück, nicht alle: die Oberfläche zeigt eine Meldung, und wer den
     * ersten Widerspruch behebt, sieht den nächsten ohnehin.
     */
    fun validateSequence(entries: List<Entry>): SequenceViolation? {
        val sorted = entries.sortedBy { it.scannedAt }

        sorted.zipWithNext { earlier, later ->
            if (earlier.scannedAt == later.scannedAt) {
                return SequenceViolation.Collision(later.scannedAt)
            }
        }

        sorted.forEachIndexed { index, entry ->
            // Gerade Positionen sind Hinfahrten, ungerade Rückkehrten - eine Kette, die mit einem
            // EXIT beginnt, scheitert damit schon an Position 0.
            val expected =
                if (index % 2 == 0) ParticipantScanType.ENTRY else ParticipantScanType.EXIT
            if (entry.scanType != expected) {
                return SequenceViolation.OutOfOrder(
                    id = entry.id,
                    at = entry.scannedAt,
                    scanType = entry.scanType,
                    expected = expected,
                )
            }
        }

        return null
    }
}
