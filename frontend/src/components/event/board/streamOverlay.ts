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
    /**
     * Gefahrene Zeit an dieser Marke in Millisekunden - nur zum Sortieren, nicht zum Anzeigen
     * (dafür ist [timeString] da). Trifft mehreres im selben Takt ein, ist die höhere Zeit die
     * jüngere Nachricht: Dieses Boot war später an der Marke.
     */
    lapMillis: number | null
}

/** Chroma-Voreinstellung des Stream-Overlays — reines Grün. */
export const STREAM_DEFAULT_BACKGROUND = '#00FF00'

const slotAt = (slots: BoardViewDto['slots'], offset: number) =>
    slots.find(slot => slot.offset === offset)

/**
 * Das jüngste Ergebnis der Veranstaltung — aus der Ergebnisliste, die der Server für jede
 * Stream-Kachel mit „Letztes Ergebnis"/AUTO mitliefert (BoardLogic.dataNeeds).
 *
 * Der Slot −1 taugt dafür nicht: Er zählt die Timeline rückwärts und trifft zuerst die
 * früher gestarteten, noch LAUFENDEN Läufe. Fahren zwei Läufe gleichzeitig — an einem
 * vollen Regattatag der Normalfall —, liefert er einen laufenden Lauf ohne Ergebnis, und
 * die Kachel zeigte nichts an. Er bleibt nur als Rückfall stehen, für den Fall, dass die
 * Antwort (noch) keine Ergebnisliste trägt.
 */
export const latestResultOf = (view: Pick<BoardViewDto, 'slots' | 'lists'>) =>
    view.lists.find(list => list.mode === 'RESULTS')?.results?.[0] ?? slotAt(view.slots, -1)?.result

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
            lapMillis: lap.lapMillis ?? null,
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
            // Gleicher Zeitstempel heißt: im selben Takt eingetroffen. Dann entscheidet die
            // gefahrene Zeit - wer später an der Marke war, ist die jüngere Nachricht. Der
            // Rundenname taugt dafür nicht: nach ihm stand ewig 1, 2, 3 im Band, weil der
            // Abruf früher alle Runden mit demselben Zeitstempel neu schrieb.
            if (a.lapMillis != null && b.lapMillis != null && a.lapMillis !== b.lapMillis) {
                return b.lapMillis - a.lapMillis
            }
            const nameCompare = b.lapName.localeCompare(a.lapName, undefined, {numeric: true})
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
    const latestResult = latestResultOf(view)
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
            return autoContent(running, latestResult)
    }
}

/**
 * Der Auto-Modus: was die Kachel zeigt, wenn niemand von Hand umschaltet.
 *
 * Ein Lauf, der nur an den Start gerufen ist (PREPARING), verdrängt das jüngste Ergebnis
 * nicht mehr. Vorher tat er es: Kaum war das nächste Rennen aktiviert, verschwand das
 * Ergebnis des eben gefahrenen aus dem Stream — für die Zuschauer sprang die Grafik auf ein
 * Rennen, das noch minutenlang am Steg lag, und die Zeiten des gerade beendeten Laufs waren
 * nie zu lesen. Erst der belegte Start übernimmt.
 *
 * Nur solange es noch gar kein Ergebnis gibt (der erste Lauf des Tages), ist der vorbereitete
 * Lauf das Beste, was die Kachel zeigen kann — besser als eine leere Key-Fläche.
 */
export const autoContent = (
    running: AthleteBoardMatch | null | undefined,
    latestResult: AthleteBoardResult | null | undefined,
): StreamOverlayContent => {
    if (running && running.state === 'RUNNING') return {kind: 'running', match: running}
    if (latestResult) return {kind: 'result', result: latestResult}
    return running ? {kind: 'running', match: running} : null
}
