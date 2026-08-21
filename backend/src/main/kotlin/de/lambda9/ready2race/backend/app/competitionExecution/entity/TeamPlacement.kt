package de.lambda9.ready2race.backend.app.competitionExecution.entity

/**
 * Ein Boot mit dem Platz, den die Rundenlogik ihm gegeben hat.
 *
 * [matchName] und [matchWeighting] stehen nur, wenn die Runde je Partie gewertet wird
 * (`CompetitionSetupPlacesOption.PER_MATCH`): Dann ist [place] der Platz INNERHALB dieser Partie -
 * Finale A, B und C haben jeweils einen Ersten - und die Partie gehört zur Aussage dazu. Bei allen
 * anderen Wertungen gilt der Platz für die ganze Runde und beide Felder bleiben leer.
 */
data class TeamPlacement(
    val team: CompetitionMatchTeamWithRegistration,
    val place: Int,
    val matchName: String? = null,
    val matchWeighting: Int? = null,
) {
    /**
     * Der Schlüssel, nach dem Platzierungen untereinander geordnet werden: erst die Partie in
     * Setup-Reihenfolge, dann der Platz. Ohne Wertung je Partie ist der Partie-Anteil für alle
     * gleich, es entscheidet also allein der Platz.
     */
    fun rankKey(): Pair<Int, Int> = (matchWeighting ?: 0) to place
}
