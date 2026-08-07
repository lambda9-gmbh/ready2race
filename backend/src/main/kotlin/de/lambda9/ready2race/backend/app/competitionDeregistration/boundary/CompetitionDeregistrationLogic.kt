package de.lambda9.ready2race.backend.app.competitionDeregistration.boundary

/**
 * Die Entscheidung, ob eine Abmeldung noch in die Meldeliste gehört oder schon ins Ergebnis —
 * herausgezogen aus [CompetitionDeregistrationService], damit sie ohne Datenbank prüfbar ist.
 */
object CompetitionDeregistrationLogic {

    /**
     * Ob für ein Boot in seinem Lauf bereits gewertet wurde: entweder ist ein Platz vergeben oder
     * es ist als ausgeschieden (DNS/DNF/DQ) eingetragen. Beides trägt der Schiedsrichter im
     * Ergebnis ein, beides ist also eine Wertung.
     *
     * Bewusst nicht dabei: `out` und `deregistered`. Beide markieren Boote, die schon vor dieser
     * Runde weg waren und nur mitgeführt werden — in dieser Runde wurde für sie nichts gewertet.
     * Sonst würde eine einzige frühere Abmeldung jede weitere blockieren.
     */
    fun teamIsScored(place: Int?, failed: Boolean): Boolean = place != null || failed

    /**
     * Ob die Wertung des Laufs begonnen hat.
     *
     * Gefragt wird über **alle** Boote des Laufs, nicht nur über das abzumeldende: Plätze werden
     * innerhalb eines Laufs relativ vergeben (1..n über die tatsächlich gefahrenen Boote, siehe die
     * Platz-Prüfung in `CompetitionExecutionService.updateMatchResult`). Sobald für irgendein Boot
     * gewertet wurde, steht das Feld fest; ein Boot dann noch aus der Meldeliste zu nehmen, würde
     * die bereits vergebenen Plätze hinter dem Rücken des Schiedsrichters verschieben. Ab diesem
     * Punkt gehört die Abmeldung ins Ergebnis — dort als Ausscheidung.
     *
     * Vor der ersten Wertung ist der Lauf dagegen unangetastet: eine frisch gesetzte Runde ohne ein
     * einziges Ergebnis erlaubt die Abmeldung.
     */
    fun scoringHasStarted(teamsScored: List<Boolean>): Boolean = teamsScored.any { it }
}
