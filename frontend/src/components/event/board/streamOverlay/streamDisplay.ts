import {AthleteBoardParticipant, AthleteBoardResultTeam, AthleteBoardTeam} from '@api/types.gen.ts'
import {failedLabel} from '@utils/matchResultStatus.ts'
import {formatPlaceOrdinal} from '@utils/placeOrdinal.ts'

/**
 * Reine Anzeige-Helfer der Stream-Overlay-Panels — ohne React, damit sie einzeln
 * prüfbar bleiben (wie `streamOverlay.ts` und `streamClock.ts` aus Task 10).
 */

/**
 * Nur deckende Hex-Farben dürfen auf die Key-Fläche: während das Theme lädt, liefert
 * MUI rgba-Vorgaben (z. B. text.primary = rgba(0,0,0,0.87)) — die würden sich mit der
 * Key-Farbe mischen und beim Keying Farbsäume ziehen. Also im Zweifel ein festes Hex.
 */
export const solidOr = (color: string, fallback: string): string =>
    color.startsWith('#') ? color : fallback

/** Sortierschlüssel mit Nullen am Ende — weder Platz noch Startnummer dürfen NULL nach vorne ziehen. */
export const byNullsLast =
    <T>(key: (team: T) => number | null | undefined) =>
    (a: T, b: T): number => {
        const av = key(a)
        const bv = key(b)
        if (av == null && bv == null) return 0
        if (av == null) return 1
        if (bv == null) return -1
        return av - bv
    }

/** Die Boot-Felder, die das Overlay zeigt — laufende, anstehende und gewertete Teams teilen sie sich. */
export type StreamTeam = Pick<
    AthleteBoardTeam | AthleteBoardResultTeam,
    | 'startNumber'
    | 'clubsShort'
    | 'clubsFull'
    | 'teamName'
    | 'place'
    | 'timeString'
    | 'penaltySeconds'
    | 'penaltyNote'
    | 'failed'
    | 'failedReason'
    | 'deregistered'
    | 'deregisteredReason'
    | 'laps'
    | 'participants'
>

export type StreamCrewMode = 'CLUBS_FIRST' | 'PARTICIPANTS_FIRST' | 'CLUBS_ONLY'

/** Kurz- oder Langform, getrennt für Wettkampfname und Vereinsnamen. */
export interface StreamNameForms {
    /** Wettkampf-Kürzel („CF1x") statt des vollen Namens. */
    competitions: boolean
    /** Vereins-Kurzform statt der vollen Vereinskette. */
    clubs: boolean
}

/**
 * Welche Namensform die Kachel zeigt. Beide Schalter sind getrennt einstellbar — „CF1x"
 * mit ausgeschriebenen Vereinen ist auf einer breiten TV-Grafik eine sinnvolle Kombination.
 *
 * Fehlt `useShortClubNames` (jedes Board, das vor der Trennung angelegt wurde), folgen die
 * Vereine weiterhin `useShortNames`: bis dahin schaltete dieser eine Schalter beides
 * gemeinsam um, und ein Board soll durch das Update nicht anders aussehen.
 */
export const streamNameForms = (element: {
    useShortNames?: boolean | null
    useShortClubNames?: boolean | null
}): StreamNameForms => {
    const competitions = element.useShortNames !== false
    return {competitions, clubs: element.useShortClubNames ?? competitions}
}

export interface StreamCrewSource {
    clubsShort?: string | null
    clubsFull?: string | null
    teamName?: string | null
    participants?: Array<Pick<AthleteBoardParticipant, 'name'>>
}

/** Vereinskette (Kurz-/Langform nach `useShortNames`) + Teamname, wie überall auf dem Board. */
export const clubLabel = (team: StreamCrewSource, useShortNames: boolean): string => {
    const club = useShortNames
        ? (team.clubsShort ?? team.clubsFull)
        : (team.clubsFull ?? team.clubsShort)
    return [club, team.teamName].filter(Boolean).join(' | ')
}

/** Die Besatzung als "Vorname Nachname"-Kette, mit mittlerem Punkt getrennt; null ohne Namen. */
const participantsLabel = (team: StreamCrewSource): string | null => {
    const names = (team.participants ?? []).map(p => p.name)
    return names.length > 0 ? names.join(' · ') : null
}

/**
 * Zwei Textzeilen einer Bootszeile nach `streamCrew`: welche der beiden — Verein oder
 * Besatzung — steht prominent (primary), welche klein darunter (secondary). CLUBS_ONLY
 * lässt die Besatzung ganz weg; ohne erfasste Besatzung fällt PARTICIPANTS_FIRST auf
 * den Verein als Primärzeile zurück — eine leere große Zeile wäre schlechter.
 */
export const crewLines = (
    team: StreamCrewSource,
    mode: StreamCrewMode,
    useShortNames: boolean,
): {primary: string; secondary: string | null} => {
    const club = clubLabel(team, useShortNames)
    if (mode === 'PARTICIPANTS_FIRST') {
        const participants = participantsLabel(team)
        return participants
            ? {primary: participants, secondary: club}
            : {primary: club, secondary: null}
    }
    // CLUBS_FIRST (Default) und CLUBS_ONLY: der Verein bleibt vorn; CLUBS_ONLY lässt die
    // Besatzungszeile weg.
    const secondary = mode === 'CLUBS_ONLY' ? null : participantsLabel(team)
    return {primary: club, secondary}
}

/**
 * Platz/Zeit, DNF/DNS/DSQ oder „Abgemeldet" einer Bootszeile — null ohne jedes Teilergebnis
 * (Aufstellung).
 *
 * Die Abmeldung steht vor allem anderen und ist ausdrücklich kein Ergebnis: Sie sagt, dass dieses
 * Boot nicht fährt, und gehört deshalb auch in ein Panel, in dem noch gar nichts gewertet sein
 * kann. Ohne [deregisteredFallback] (Aufrufer, die die Angabe nicht führen) bleibt es beim alten
 * Verhalten.
 */
export const teamTrailingLabel = (
    team: Pick<
        StreamTeam,
        'place' | 'timeString' | 'failed' | 'failedReason' | 'deregistered' | 'deregisteredReason'
    >,
    failedFallback: string,
    deregisteredFallback?: string,
): string | null => {
    if (team.deregistered && deregisteredFallback) {
        return team.deregisteredReason
            ? `${deregisteredFallback} — ${team.deregisteredReason}`
            : deregisteredFallback
    }
    if (team.failed) return failedLabel(team.failedReason, failedFallback)
    const parts = [
        team.place != null ? formatPlaceOrdinal(team.place) : null,
        team.timeString,
    ].filter(Boolean)
    return parts.length > 0 ? parts.join(' ') : null
}

/**
 * Restzeit bis zum Start als 'm:ss' — sekundengenau reicht für den Countdown im
 * Als-Nächstes-Panel, anders als die zehntelgenaue Laufuhr des laufenden Laufs.
 */
export const formatCountdownClock = (remainingMs: number): string => {
    const totalSeconds = Math.max(0, Math.round(remainingMs / 1000))
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/**
 * Runde + Lauf, dedupliziert: ein Laufname, der nur die Runde wiederholt (z. B. beide
 * "Vorlauf 1"), entfällt — dieselbe Regel wie ResultsMatchCard und die bisherige
 * Lower-Third-Kopfzeile.
 */
export const roundMatchLabel = (
    roundName: string | null | undefined,
    matchName: string | null | undefined,
): string | null => {
    const showMatchName = matchName != null && matchName !== roundName
    return [roundName, showMatchName ? matchName : null].filter(Boolean).join(' · ') || null
}

/** Wettkampfname in Kurz- oder Langform je nach `useShortNames` — ohne Kürzel bleibt der volle Name. */
export const competitionLabel = (
    name: string,
    shortName: string | null | undefined,
    useShortNames: boolean,
): string => (useShortNames ? (shortName ?? name) : name)
