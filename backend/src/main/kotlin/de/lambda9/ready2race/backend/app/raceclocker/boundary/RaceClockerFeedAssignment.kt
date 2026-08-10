package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget

/**
 * Welche Adressen ein Takt anfragt — bewusst ohne Datenbank- und HTTP-Bezug, wie
 * [RaceClockerPollLogic] und aus demselben Grund: Am Renntag zählt, dass diese Regeln stimmen, und
 * prüfen lassen sie sich nur so ohne laufende Umgebung.
 *
 * Der Abruf läuft in zwei Runden. Runde 1 holt die angewählten Rennen; Runde 2 holt den Rückfall,
 * aber nur für Läufe, die in ihrem Rennen nicht gefunden wurden. Vorher wurden beide Adressen jedes
 * beobachteten Laufs bedingungslos geholt — bei einer Regatta ohne Zeitfahren war damit jeder
 * zweite Abruf überflüssig, in jedem Takt der ganzen Veranstaltung.
 */
object RaceClockerFeedAssignment {

    /**
     * Die angewählten Rennen, entdoppelt. Acht Läufe desselben Rennens kosten damit einen Abruf,
     * nicht acht. Die Reihenfolge bleibt stabil, damit Protokolle zweier Takte vergleichbar sind.
     */
    fun primaryUrls(targets: List<RaceClockerMatchTarget>): List<String> =
        targets.mapNotNull { it.resultsUrl }.distinct()

    /**
     * Die Rückfall-Rennen der Läufe, die in Runde 1 leer ausgegangen sind — ohne das, was schon
     * geholt ist. Ist die Liste leer, entfällt die zweite Runde ganz, und das ist der Normalfall.
     *
     * [alreadyFetched] ist nicht bloß Sparsamkeit: Ohne diese Prüfung würde ein Rennen, das schon
     * in Runde 1 geantwortet hat, ein zweites Mal geholt, nur weil ein anderer Lauf es als Rückfall
     * führt — und die Antwort wäre dieselbe.
     */
    fun fallbackUrls(
        unresolved: List<RaceClockerMatchTarget>,
        alreadyFetched: Set<String>,
    ): List<String> =
        unresolved.mapNotNull { it.alternateResultsUrl }
            .filterNot { it in alreadyFetched }
            .distinct()
}
