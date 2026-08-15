import {describe, expect, it} from 'vitest'
import {lastLaps, streamOverlayContent} from './streamOverlay.ts'
import {
    AthleteBoardMatch,
    AthleteBoardResult,
    BoardListDto,
    BoardMatchSlotDto,
    MatchTeamLapDto,
} from '@api/types.gen.ts'

const match = (name: string) => ({matchName: name, teams: []}) as unknown as AthleteBoardMatch
const result = (name: string) => ({matchName: name}) as unknown as AthleteBoardResult
const slot = (offset: number, m?: AthleteBoardMatch, r?: AthleteBoardResult): BoardMatchSlotDto =>
    ({offset, match: m ?? null, result: r ?? null}) as BoardMatchSlotDto
const view = (slots: BoardMatchSlotDto[], lists: BoardListDto[] = []) => ({slots, lists})

const lap = (
    startNumber: number,
    name: string,
    timeString: string,
    recordedAt: string | null,
): {startNumber: number; laps: MatchTeamLapDto[]} => ({
    startNumber,
    laps: [{name, timeString, recordedAt}] as MatchTeamLapDto[],
})

const runningMatch = (teams: ReturnType<typeof lap>[]) =>
    ({matchName: 'VF1', teams}) as unknown as AthleteBoardMatch

describe('streamOverlayContent', () => {
    it('AUTO zeigt den laufenden Lauf, wenn einer läuft', () => {
        const content = streamOverlayContent(
            view([slot(0, match('VF1')), slot(-1, undefined, result('ZF'))]),
            'AUTO',
        )
        expect(content).toMatchObject({kind: 'running'})
    })

    it('AUTO fällt ohne laufenden Lauf auf das jüngste Ergebnis zurück', () => {
        const content = streamOverlayContent(
            view([slot(0), slot(-1, undefined, result('ZF'))]),
            undefined,
        )
        expect(content).toMatchObject({kind: 'result'})
    })

    it('AUTO ohne beides bleibt leer', () => {
        expect(streamOverlayContent(view([slot(0), slot(-1)]), 'AUTO')).toBeNull()
    })

    it('ein Lauf in Vorbereitung in Slot 0 bleibt als Lower-Third sichtbar', () => {
        // Der Running-Block des Servers führt auch die an den Start gerufenen Läufe
        // (PREPARING) — der Inhalt bleibt bewusst stehen, nur das Badge unterscheidet
        // (siehe runningBadge in streamDisplay.ts). Ein Verstecken hier würde das
        // Lower-Third am Regattatag ständig flackern lassen.
        const preparing = {matchName: 'VF1', state: 'PREPARING', teams: []} as unknown as AthleteBoardMatch
        expect(streamOverlayContent(view([slot(0, preparing)]), 'RUNNING')).toMatchObject({
            kind: 'running',
            match: {state: 'PREPARING'},
        })
        expect(streamOverlayContent(view([slot(0, preparing)]), 'AUTO')).toMatchObject({
            kind: 'running',
        })
    })

    it('RUNNING zeigt kein Ergebnis als Rückfall', () => {
        expect(
            streamOverlayContent(view([slot(0), slot(-1, undefined, result('ZF'))]), 'RUNNING'),
        ).toBeNull()
    })

    it('RESULTS zeigt nur das Ergebnis', () => {
        const content = streamOverlayContent(view([slot(-1, undefined, result('ZF'))]), 'RESULTS')
        expect(content).toMatchObject({kind: 'result'})
    })

    it('UPCOMING zeigt den nächsten anstehenden Lauf', () => {
        const content = streamOverlayContent(view([slot(1, match('HF2'))]), 'UPCOMING')
        expect(content).toMatchObject({kind: 'upcoming'})
    })

    it('ein Ergebnis im Slot −1 wird im AUTO-Modus nicht als laufend ausgegeben', () => {
        const content = streamOverlayContent(
            view([slot(0), slot(-1, match('noch laufender älterer Lauf'))]),
            'AUTO',
        )
        // Slot −1 kann laut resolveOffset auch einen FRÜHER gestarteten, noch laufenden
        // Lauf tragen — der ist kein Ergebnis und wird im AUTO-Rückfall übersprungen.
        expect(content).toBeNull()
    })

    it('UPCOMING_LIST liefert die ersten 5 der UPCOMING-Liste', () => {
        const matches = ['HF1', 'HF2', 'HF3', 'HF4', 'HF5', 'HF6'].map(match)
        const list: BoardListDto = {mode: 'UPCOMING', matches, results: []} as BoardListDto
        const content = streamOverlayContent(view([], [list]), 'UPCOMING_LIST')
        expect(content).toMatchObject({kind: 'upcomingList'})
        if (content?.kind === 'upcomingList') {
            expect(content.matches).toHaveLength(5)
            expect(content.matches.map(m => m.matchName)).toEqual([
                'HF1',
                'HF2',
                'HF3',
                'HF4',
                'HF5',
            ])
        }
    })

    it('UPCOMING_LIST bleibt ohne UPCOMING-Liste leer', () => {
        expect(streamOverlayContent(view([]), 'UPCOMING_LIST')).toBeNull()
    })

    it('LAPS liefert den laufenden Lauf mit den letzten drei Runden', () => {
        const running = runningMatch([
            lap(1, 'Runde 1', '0:30.0', '2026-08-13T10:00:00Z'),
            lap(1, 'Runde 2', '1:00.0', '2026-08-13T10:01:00Z'),
        ])
        const content = streamOverlayContent(view([slot(0, running)]), 'LAPS')
        expect(content).toMatchObject({kind: 'laps'})
        if (content?.kind === 'laps') {
            expect(content.laps).toHaveLength(2)
            expect(content.laps[0].lapName).toBe('Runde 2')
        }
    })

    it('LAPS ohne Runden bleibt leer', () => {
        const running = runningMatch([])
        expect(streamOverlayContent(view([slot(0, running)]), 'LAPS')).toBeNull()
    })
})

describe('lastLaps', () => {
    it('sortiert nach recordedAt absteigend, neueste zuerst', () => {
        const m = runningMatch([
            lap(1, 'Runde 1', '0:30.0', '2026-08-13T10:00:00Z'),
            lap(2, 'Runde 1', '0:31.0', '2026-08-13T10:02:00Z'),
            lap(3, 'Runde 1', '0:32.0', '2026-08-13T10:01:00Z'),
        ])
        const laps = lastLaps(m)
        expect(laps.map(l => l.startNumber)).toEqual([2, 3, 1])
    })

    it('Runden ohne recordedAt landen hinten, stabil nach Rundenname und Startnummer', () => {
        const m = runningMatch([
            lap(1, 'Runde 2', '0:30.0', null),
            lap(2, 'Runde 1', '0:31.0', '2026-08-13T10:00:00Z'),
            lap(3, 'Runde 1', '0:32.0', null),
        ])
        const laps = lastLaps(m, 10)
        expect(laps.map(l => l.startNumber)).toEqual([2, 3, 1])
    })

    it('begrenzt standardmäßig auf drei Einträge', () => {
        const m = runningMatch([
            lap(1, 'Runde 1', '0:30.0', '2026-08-13T10:00:00Z'),
            lap(2, 'Runde 1', '0:31.0', '2026-08-13T10:01:00Z'),
            lap(3, 'Runde 1', '0:32.0', '2026-08-13T10:02:00Z'),
            lap(4, 'Runde 1', '0:33.0', '2026-08-13T10:03:00Z'),
        ])
        expect(lastLaps(m)).toHaveLength(3)
    })

    it('leere Runden ergeben eine leere Liste', () => {
        expect(lastLaps(runningMatch([]))).toEqual([])
    })
})

describe('streamOverlayContent CLOCK', () => {
    it('liefert die Uhr-Quelle nur mit laufendem Lauf', () => {
        const running = match('VF1')
        expect(streamOverlayContent({slots: [slot(0, running)], lists: []}, 'CLOCK')).toMatchObject(
            {kind: 'clock'},
        )
        expect(streamOverlayContent({slots: [slot(0)], lists: []}, 'CLOCK')).toBeNull()
    })
})

/**
 * Das jüngste Ergebnis kommt aus der Ergebnisliste, nicht aus dem Slot −1 — der trifft
 * bei zwei gleichzeitig fahrenden Läufen zuerst den früher gestarteten (siehe
 * `latestResultOf`). Genau daran zeigte „Nur letztes Ergebnis" auf einem vollen
 * Regattatag nichts an.
 */
describe('jüngstes Ergebnis aus der Ergebnisliste', () => {
    const resultList = (...results: AthleteBoardResult[]) =>
        [{mode: 'RESULTS', matches: [], results}] as unknown as BoardListDto[]

    it('RESULTS zeigt das Ergebnis, obwohl Slot −1 einen laufenden Lauf trägt', () => {
        const content = streamOverlayContent(
            view([slot(-1, match('parallel laufend'))], resultList(result('ZF'))),
            'RESULTS',
        )
        expect(content).toMatchObject({kind: 'result', result: {matchName: 'ZF'}})
    })

    it('nimmt das erste Ergebnis der Liste (Server sortiert neuestes zuerst)', () => {
        const content = streamOverlayContent(
            view([], resultList(result('neu'), result('alt'))),
            'RESULTS',
        )
        expect(content).toMatchObject({kind: 'result', result: {matchName: 'neu'}})
    })

    it('fällt ohne Ergebnisliste weiterhin auf den Slot −1 zurück', () => {
        const content = streamOverlayContent(view([slot(-1, undefined, result('ZF'))]), 'RESULTS')
        expect(content).toMatchObject({kind: 'result', result: {matchName: 'ZF'}})
    })

    it('zeigt nichts, wenn die Ergebnisliste leer ist und der Slot einen laufenden Lauf trägt', () => {
        expect(
            streamOverlayContent(
                view([slot(-1, match('parallel laufend'))], resultList()),
                'RESULTS',
            ),
        ).toBeNull()
    })

    it('AUTO fällt ohne laufenden Lauf ebenfalls auf die Ergebnisliste zurück', () => {
        const content = streamOverlayContent(view([], resultList(result('ZF'))), 'AUTO')
        expect(content).toMatchObject({kind: 'result', result: {matchName: 'ZF'}})
    })
})
