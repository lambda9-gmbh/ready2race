package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget

/**
 * Welche Adressen ein Takt anfragt — bewusst ohne Datenbank- und HTTP-Bezug, wie
 * [RaceClockerPollLogic] und aus demselben Grund: Am Renntag zählt, dass diese Regeln stimmen, und
 * prüfen lassen sie sich nur so ohne laufende Umgebung.
 *
 * Seit jeder Wettkampf genau EIN Rennen hat (11.08.2026), gibt es keine Rückfall-Runde mehr: Ein
 * Takt holt die angewählten Rennen, entdoppelt — fertig.
 */
object RaceClockerFeedAssignment {

    /**
     * Die angewählten Rennen, entdoppelt. Acht Läufe desselben Rennens kosten damit einen Abruf,
     * nicht acht. Die Reihenfolge bleibt stabil, damit Protokolle zweier Takte vergleichbar sind.
     */
    fun urls(targets: List<RaceClockerMatchTarget>): List<String> =
        targets.mapNotNull { it.resultsUrl }.distinct()
}
