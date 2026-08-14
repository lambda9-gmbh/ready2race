package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

/**
 * Ein Treffer der vereinsübergreifenden Suche — bewusst NICHT [ParticipantDto].
 *
 * Wer über diese Suche eine fremde Person findet, bekommt genau so viel, wie er zum Melden
 * braucht: wen er meint (Name), ob die Person in die Altersklasse passt (Jahrgang), aus welchem
 * Verein sie kommt (Verein) und in welche Bootsplätze sie darf (Geschlecht — die Meldemaske
 * rechnet damit die Besetzung aus, ohne den Wert wäre die Auswahl gar nicht bedienbar).
 *
 * Was NICHT mitreist: Telefonnummer, E-Mail-Adresse, Anlage- und Änderungsdaten, geprüfte
 * Bedingungen. Ein fremder Verein hat davon nichts zu sehen.
 */
data class ParticipantSearchResultDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    /**
     * Der Verein im Klartext. Bei Gastruderern (`participant.external`) steht hier der
     * Freitext aus `external_club_name`, sonst der Name des Stammvereins — dieselbe Regel wie
     * überall sonst, damit die Auswahl nicht auf den meldenden Verein zurückfällt.
     */
    val clubName: String,
)
