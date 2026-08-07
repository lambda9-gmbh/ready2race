package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.util.UUID

/**
 * Eine Veranstaltung, die ihre RaceClocker-Ergebnisse selbst abholen lässt, samt ihrer Takte.
 * Bewusst ohne die URLs: Welches Rennen ein Lauf braucht, entscheidet die Runde, nicht die
 * Veranstaltung - das steht je Lauf in [RaceClockerPollCandidate].
 */
data class RaceClockerPollEvent(
    val eventId: UUID,
    val intervalActiveSeconds: Int,
    val intervalUpcomingSeconds: Int,
    val watchBeforeMinutes: Int,
    val watchAfterMinutes: Int,
)
