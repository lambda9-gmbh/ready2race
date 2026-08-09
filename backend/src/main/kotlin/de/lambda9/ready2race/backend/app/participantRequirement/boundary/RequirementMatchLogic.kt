package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import java.util.UUID

/**
 * Reine Logik des Abgleichs "hochgeladene Liste <-> Gemeldete", bewusst ohne Datenbank- und
 * Ktor-Bezug, damit sie ohne laufende Umgebung geprüft werden kann.
 */
object RequirementMatchLogic {

    /**
     * Vergleicht ohne Rücksicht auf Groß-/Kleinschreibung und auf Leerzeichen am Rand.
     *
     * Das Trimmen ist nicht kosmetisch: Meldungen tragen die Namen so, wie die Vereine sie
     * eingegeben haben, und dabei rutscht regelmäßig ein Leerzeichen mit hinein. Bei der
     * Coastal-Regatta 2026 betraf das 33 von 189 Gemeldeten und kostete 16 Treffer gegen die
     * DRV-Aktivenpassliste. Leerzeichen *innerhalb* des Namens bleiben unangetastet -
     * "Amelie Katharina" und "AmelieKatharina" sind weiterhin verschiedene Personen.
     */
    private fun looseEquals(a: String?, b: String?): Boolean =
        a?.trim().equals(b?.trim(), ignoreCase = true)

    /**
     * Gilt die Zeile laut der Bedingungsspalte als erfüllt?
     *
     * [acceptedValues] sind die im Import ausgewählten Werte. Mehrere sind ausdrücklich
     * erlaubt: Die DRV-Aktivenpassliste führt in der Spalte "Startberechtigt" sowohl "ja" als
     * auch "erweitert", beides bedeutet startberechtigt.
     *
     * Ist die Liste leer oder nicht gesetzt, zählt jede Zeile - das entspricht dem Fall, dass
     * die Spalte gar nicht zugeordnet wurde. Ein Import läuft dadurch nie stillschweigend auf
     * null Treffer, nur weil die Auswahl vergessen wurde. Eine leere Zelle gilt dagegen nie
     * als erfüllt, sobald überhaupt Werte ausgewählt sind.
     */
    fun isAccepted(cellValue: String?, acceptedValues: List<String>?): Boolean =
        if (acceptedValues.isNullOrEmpty()) {
            true
        } else {
            !cellValue.isNullOrBlank() && acceptedValues.any { looseEquals(cellValue, it) }
        }

    /**
     * Trifft eine Zeile der hochgeladenen Liste die gemeldete Person?
     *
     * Verein und Jahrgang werden nur geprüft, wenn die Liste sie überhaupt führt - sind die
     * Spalten beim Import nicht zugeordnet, entscheidet allein der Name. [namedParticipantId]
     * ist gesetzt, wenn die Bedingung nur für eine Rolle gilt (etwa "Waage 55 kg" für
     * Steuerleute); dann muss die gemeldete Person diese Rolle auch tatsächlich haben.
     */
    fun matches(
        listFirstname: String?,
        listLastname: String?,
        listYear: Int?,
        listClub: String?,
        registeredFirstname: String?,
        registeredLastname: String?,
        registeredYear: Int?,
        registeredClub: String?,
        namedParticipantId: UUID?,
        registeredRoles: List<UUID>?,
    ): Boolean =
        looseEquals(registeredFirstname, listFirstname)
            && looseEquals(registeredLastname, listLastname)
            && (listClub?.let { looseEquals(registeredClub, it) } ?: true)
            && (listYear?.let { registeredYear == it } ?: true)
            && (namedParticipantId == null || registeredRoles?.contains(namedParticipantId) == true)
}
