package de.lambda9.ready2race.backend.app.raceclocker.entity

/**
 * Wo ein Lauf in RaceClocker zu finden ist.
 *
 * Eine Veranstaltung führt benannte Rennen, und jeder Wettkampf wählt daraus genau EINES — für die
 * Qualifikation und alle übrigen Runden gemeinsam. Die frühere Zweiteilung (Zeitfahren-Rennen für
 * die Qualifikation, Läufe-Rennen für den Rest, mit dem jeweils anderen als Rückfall) ist mit dem
 * RaceClocker-Update vom 11.08.2026 entfallen: Dort gibt es keine Startarten mehr, ein Rennen trägt
 * alle Runden.
 */
data class RaceClockerMatchTarget(
    /**
     * Die geplante Startzeit, der Wettkampf (Nummer und Kürzel) und der Laufname (siehe
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName]), exportiert als
     * RaceClocker-Wellenname. Nur für Startlisten ohne Lauf-Kennung nötig.
     */
    val waveName: String?,
    /** Das angewählte Rennen dieses Wettkampfs — null, solange keines zugewiesen ist. */
    val race: RaceClockerRaceRef?,
) {
    val resultsUrl: String? get() = race?.resultsUrl

    /**
     * Höchstens ein Eintrag — als Liste geführt, weil Abruf und Fehlermeldung
     * ([de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError.MatchNotInFeed])
     * listenförmig bleiben: Die Schnittstelle nach außen ändert sich damit nicht, nur ihre Länge.
     */
    val candidateUrls: List<String> get() = listOfNotNull(resultsUrl)

    /**
     * Dieselbe Form wie [candidateUrls], aber lesbar. Die Fehlermeldung braucht das: „Lauf im
     * Rennen Kurzstrecke nicht gefunden" ist am Renntag brauchbar, eine nackte URL nicht.
     */
    val candidateRaceNames: List<String> get() = listOfNotNull(race?.name)
}
