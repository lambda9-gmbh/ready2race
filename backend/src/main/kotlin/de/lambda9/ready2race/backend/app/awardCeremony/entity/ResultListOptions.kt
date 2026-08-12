package de.lambda9.ready2race.backend.app.awardCeremony.entity

/**
 * Der Schriftgrad der Ergebnisliste. Er entscheidet über die komplette Maßtabelle des Blatts,
 * nicht nur über eine einzelne Schriftgröße - siehe die Maßtabellen in `AwardCeremonyPdf`.
 */
enum class ResultListSize {
    /** Aushang: groß genug, um im Vorbeigehen am schwarzen Brett gelesen zu werden. */
    POSTING,

    /** Der heutige Siegerehrungsbogen: gesetzt für das Vorlesen am Pult. */
    CEREMONY,
}

/**
 * Die Optionen des einen Ergebnislisten-Generators. Der Siegerehrungsbogen ist seit der
 * Verallgemeinerung nur noch das Preset [ceremony] dieses Objekts - sein Endpoint und sein Knopf
 * sind äußerlich unverändert, ziehen aber durch denselben Codepfad.
 *
 * Rundenzeiten gibt es hier bewusst nicht als Schalter: die Datenbasis des Bogens kennt je Boot
 * genau eine Endzeit und den Lauf, in dem sie entstand - Zwischen- oder Rundenzeiten kennt sie
 * nicht, und ein Schalter ohne Daten dahinter wäre eine leere Zusage.
 */
data class ResultListOptions(
    /** Die Überschrift des Blatts - „ERGEBNISLISTE" oder „SIEGEREHRUNG". */
    val heading: String,
    /** Mit oder ohne Crew-Aufstellung - die Namenszeilen je Boot. */
    val includeCrew: Boolean,
    /** Mit oder ohne Zeiten und Zeitstrafen. */
    val includeTimes: Boolean,
    /** Nur die Ränge bis [de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic.MAX_RANK] - das Podium - statt aller platzierten Boote. */
    val podiumOnly: Boolean,
    /** Je Wertungskategorie ein eigener Abschnitt (ein Blatt) statt des Gesamtfelds. */
    val byRatingCategory: Boolean,
    val size: ResultListSize,
    /**
     * Die Fußzeile jedes Blatts, in der Regel Veranstaltung plus „Stand: …". Beim Aushang ist sie
     * Pflicht - nur an ihr erkennt man einen veralteten Ausdruck am Brett. `null` heißt keine
     * Fußzeile, und genau das braucht der klassische Bogen: seine Ausgabe bleibt unverändert.
     */
    val footerLine: String?,
) {
    companion object {
        /** Das Preset hinter dem klassischen Siegerehrungsbogen-Endpoint - Ausgabe wie eh und je. */
        val ceremony = ResultListOptions(
            heading = "SIEGEREHRUNG",
            includeCrew = true,
            includeTimes = true,
            podiumOnly = true,
            byRatingCategory = true,
            size = ResultListSize.CEREMONY,
            footerLine = null,
        )
    }
}
