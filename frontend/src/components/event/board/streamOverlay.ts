import {AthleteBoardMatch, AthleteBoardResult, BoardElement, BoardViewDto} from '@api/types.gen.ts'

/**
 * Was das Livestream-Overlay einblendet. Genau EIN Lauf oder nichts — ein Lower-Third
 * mit zwei Läufen gibt es nicht (Spec: bei mehreren laufenden gewinnt der zuletzt
 * gestartete, und das erledigt bereits die Slot-Maschinerie des Servers mit Offset 0).
 */
export type StreamOverlayContent =
    | {kind: 'running'; match: AthleteBoardMatch}
    | {kind: 'result'; result: AthleteBoardResult}
    | {kind: 'upcoming'; match: AthleteBoardMatch}
    | {kind: 'upcomingList'; matches: AthleteBoardMatch[]}
    | {kind: 'laps'; match: AthleteBoardMatch; laps: StreamLapEntry[]}
    | {kind: 'clock'; match: AthleteBoardMatch}
    | null

/**
 * Eine eingetroffene Zwischenzeit fürs Rundenband. club-Aufbereitung (Kürzel vs. voller
 * Name) ist bewusst NICHT hier drin — die roh mitgegebenen clubsShort/clubsFull
 * entscheidet erst die Anzeige (useShortNames), wie überall sonst auf dem Board.
 */
export type StreamLapEntry = {
    startNumber: number
    clubsShort: string | null
    clubsFull: string | null
    lapName: string
    timeString: string
    recordedAt: string | null
}

/** Chroma-Voreinstellung des Stream-Overlays — reines Grün. */
export const STREAM_DEFAULT_BACKGROUND = '#00FF00'

const slotAt = (slots: BoardViewDto['slots'], offset: number) =>
    slots.find(slot => slot.offset === offset)

/**
 * Die letzten `limit` (Default 3) eingetroffenen Runden des Laufs, über alle Boote hinweg
 * geflacht, neueste zuerst. Runden ohne recordedAt gelten als älteste und landen hinten;
 * bei Gleichstand entscheidet stabil erst der Rundenname, dann die Startnummer.
 */
export const lastLaps = (match: AthleteBoardMatch, limit = 3): StreamLapEntry[] => {
    const entries: StreamLapEntry[] = match.teams.flatMap(team =>
        (team.laps ?? []).map(lap => ({
            startNumber: team.startNumber,
            clubsShort: team.clubsShort ?? null,
            clubsFull: team.clubsFull ?? null,
            lapName: lap.name,
            timeString: lap.timeString,
            recordedAt: lap.recordedAt ?? null,
        })),
    )

    return entries
        .slice()
        .sort((a, b) => {
            const ta = a.recordedAt ? new Date(a.recordedAt).getTime() : null
            const tb = b.recordedAt ? new Date(b.recordedAt).getTime() : null
            if (ta !== null && tb !== null && ta !== tb) return tb - ta // neueste zuerst
            if (ta !== null && tb === null) return -1 // Runde mit Zeit vor Runde ohne
            if (ta === null && tb !== null) return 1
            const nameCompare = a.lapName.localeCompare(b.lapName)
            if (nameCompare !== 0) return nameCompare
            return a.startNumber - b.startNumber
        })
        .slice(0, limit)
}

export const streamOverlayContent = (
    view: Pick<BoardViewDto, 'slots' | 'lists'>,
    mode: BoardElement['streamMode'],
): StreamOverlayContent => {
    const running = slotAt(view.slots, 0)?.match
    const latestResult = slotAt(view.slots, -1)?.result
    const upcoming = slotAt(view.slots, 1)?.match
    switch (mode ?? 'AUTO') {
        case 'RUNNING':
            return running ? {kind: 'running', match: running} : null
        case 'RESULTS':
            return latestResult ? {kind: 'result', result: latestResult} : null
        case 'UPCOMING':
            return upcoming ? {kind: 'upcoming', match: upcoming} : null
        case 'UPCOMING_LIST': {
            const matches = (view.lists.find(l => l.mode === 'UPCOMING')?.matches ?? []).slice(0, 5)
            return matches.length > 0 ? {kind: 'upcomingList', matches} : null
        }
        case 'CLOCK':
            // Nur-Uhr-Quelle: Streamer croppen sie notfalls separat. Ohne laufenden
            // Lauf bleibt die Seite reine Key-Farbe — die Uhr regelt ihr Erscheinen
            // danach selbst (Fade ab actualStartTime, Freeze, Fade-out).
            return running ? {kind: 'clock', match: running} : null
        case 'LAPS': {
            if (!running) return null
            const laps = lastLaps(running)
            return laps.length > 0 ? {kind: 'laps', match: running, laps} : null
        }
        default:
            return running
                ? {kind: 'running', match: running}
                : latestResult
                  ? {kind: 'result', result: latestResult}
                  : null
    }
}
