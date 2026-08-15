package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Lauf, der für den automatischen Abruf überhaupt in Frage kommt. Ob er auch beobachtet wird,
 * entscheidet erst `RaceClockerPollLogic.isWatched` anhand von [startTime] und [activatedAt] - das
 * Zeitfenster steht in der Logik und nicht in der Abfrage, damit es prüfbar bleibt.
 *
 * [matchId] ist wie überall `competition_match.competition_setup_match`.
 */
data class RaceClockerPollCandidate(
    val matchId: UUID,
    val competitionId: UUID,
    val startTime: LocalDateTime?,
    /** Wann der Lauf an den Start gerufen wurde — null, solange ihn niemand aktiviert hat. */
    val activatedAt: LocalDateTime?,
    /**
     * Der Ist-Start. Null bei einem Lauf, der aktiviert ist, aber noch am Steg liegt — genau der
     * Fall, für den der Abruf im Feed nach einer gemessenen Startzeit sieht.
     */
    val startedAt: LocalDateTime?,
    /**
     * Wann der automatische Abruf für diesen Lauf pausiert wurde (Handeingabe, Datei-Upload,
     * Deaktivieren, Reset) — null, solange die Automatik ihn beschreiben darf.
     *
     * Ein pausierter Lauf kommt bewusst trotzdem als Kandidat zurück: Er darf nicht mehr
     * BESCHRIEBEN werden, aber sein Zustand zählt weiter für den Takt der Veranstaltung. Vorher
     * fiel er schon in der Abfrage heraus, und eine Handeingabe in den einzigen aktivierten Lauf
     * kippte damit die GANZE Veranstaltung vom schnellen in den langsamen Takt — für die übrigen
     * Läufe der Runde sah das aus, als stünde der Abruf (beobachtet am Regattatag 14.08.2026).
     */
    val autoPausedAt: LocalDateTime?,
    val target: RaceClockerMatchTarget,
)
