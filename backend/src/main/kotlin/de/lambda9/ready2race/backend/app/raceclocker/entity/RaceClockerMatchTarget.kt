package de.lambda9.ready2race.backend.app.raceclocker.entity

/**
 * Wo ein Lauf in RaceClocker zu finden ist.
 *
 * Eine Veranstaltung führt benannte Rennen; Veranstaltung und Wettkampf wählen daraus je eines für
 * die Qualifikationsrunden und eines für alle übrigen Runden ([qualificationRace] / [roundsRace]).
 * [isQualification] entscheidet, welches für DIESEN Lauf gilt.
 *
 * Diese Anwahl ist eine Angabe, keine Garantie: Nichts hindert daran, eine als Zeitfahren gefahrene
 * Runde nicht als Qualifikation zu markieren. Deshalb bleibt das jeweils andere Rennen der Rückfall
 * — geholt wird es allerdings erst, wenn der Lauf im angewählten nicht auftaucht.
 */
data class RaceClockerMatchTarget(
    /**
     * Der Laufname plus die geplante Startzeit (siehe
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName]), exportiert als
     * RaceClocker-Wellenname. Nur für Startlisten ohne Lauf-Kennung nötig.
     */
    val waveName: String?,
    val isQualification: Boolean,
    val qualificationRace: RaceClockerRaceRef?,
    val roundsRace: RaceClockerRaceRef?,
) {
    val race: RaceClockerRaceRef? get() = if (isQualification) qualificationRace else roundsRace

    val alternateRace: RaceClockerRaceRef? get() = if (isQualification) roundsRace else qualificationRace

    val resultsUrl: String? get() = race?.resultsUrl

    /**
     * Null, wenn es kein anderes Rennen gibt — oder wenn beide Anwahlen auf dieselbe Adresse zeigen.
     * Entdoppelt wird über die Adresse und nicht über die Kennung, weil geholt wird, was die Adresse
     * hergibt: Ein zweiter Abruf derselben Adresse brächte dieselbe Antwort.
     */
    val alternateResultsUrl: String? get() = alternateRace?.resultsUrl?.takeIf { it != resultsUrl }

    /** Angewähltes Rennen zuerst, damit eine richtige Anwahl genau einen Abruf kostet. */
    val candidateUrls: List<String> get() = listOfNotNull(resultsUrl, alternateResultsUrl)

    /**
     * Dieselbe Reihenfolge wie [candidateUrls], aber lesbar. Die Fehlermeldung braucht das: „Lauf im
     * Rennen Kurzstrecke nicht gefunden" ist am Renntag brauchbar, eine nackte URL nicht.
     */
    val candidateRaceNames: List<String>
        get() = listOfNotNull(race, alternateRace?.takeIf { it.resultsUrl != resultsUrl }).map { it.name }
}
