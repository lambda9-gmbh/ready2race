package de.lambda9.ready2race.backend.app.eventInfo.entity

/**
 * Wie die Startzeit einer Lauf-Karte anzuzeigen ist.
 *
 * Bedeutung trägt der Zustand nur im Block `upcoming`. Im Block `running` wird er
 * nicht ausgewertet — dort steht ohnehin die Startzeit statt einer Restzeit.
 */
enum class AthleteBoardStartState {
    /** Keine Startzeit gepflegt — die Runde ist noch nicht gesetzt. */
    UNSCHEDULED,

    /** Startzeit liegt in der Zukunft, Countdown ist eingeschaltet. */
    COUNTDOWN,

    /** Startzeit liegt in der Zukunft, Countdown ist abgeschaltet. */
    SCHEDULED,

    /** Startzeit ist verstrichen, der Lauf ist aber noch nicht gestartet. */
    OVERDUE,
}
