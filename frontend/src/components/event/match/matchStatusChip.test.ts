import {describe, expect, it} from 'vitest'
import {EventScheduleSlotDto, MatchStatusDto} from '@api/types.gen.ts'
import {
    OVERDUE_GRACE_MINUTES,
    matchStatusChip,
    roundCounterChips,
    slotMatchStatus,
    waterChip,
} from './matchStatusChip.ts'

const NOW = new Date('2026-08-15T10:00:00')

/**
 * Eine Zeit relativ zu [NOW] als Zeitstempel ohne Zone — so kommen sie vom Server (LocalDateTime),
 * und so liest der Browser sie auch wieder: als Ortszeit. Deshalb bewusst aus den lokalen
 * Bestandteilen zusammengesetzt statt über `toISOString()`, das nach UTC verschieben würde.
 */
const minutesAgo = (minutes: number): string => {
    const d = new Date(NOW.getTime() - minutes * 60_000)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const status = (overrides: Partial<MatchStatusDto>): MatchStatusDto => ({
    state: 'UPCOMING',
    teamsTotal: 6,
    teamsScored: 0,
    ...overrides,
})

describe('matchStatusChip', () => {
    it('zeigt einen anstehenden Lauf innerhalb der Nachfrist als „Anstehend"', () => {
        const chip = matchStatusChip(status({}), minutesAgo(OVERDUE_GRACE_MINUTES - 1), NOW)
        expect(chip).toEqual({labelKey: 'event.match.status.upcoming', color: 'default'})
    })

    it('zeigt einen anstehenden Lauf vor seiner Startzeit als „Anstehend"', () => {
        const chip = matchStatusChip(status({}), minutesAgo(-30), NOW)
        expect(chip.labelKey).toBe('event.match.status.upcoming')
    })

    it('meldet einen anstehenden Lauf nach der Nachfrist als überfällig, mit Verzug in Minuten', () => {
        const chip = matchStatusChip(status({}), minutesAgo(8), NOW)
        expect(chip).toEqual({
            labelKey: 'event.match.status.overdue',
            values: {minutes: 8},
            color: 'error',
        })
    })

    it('zeigt einen aktiven Lauf mit seiner Laufzeit', () => {
        const chip = matchStatusChip(
            status({state: 'RUNNING', startedAt: minutesAgo(4)}),
            minutesAgo(6),
            NOW,
        )
        expect(chip).toEqual({
            labelKey: 'event.match.status.running',
            values: {minutes: 4},
            color: 'primary',
        })
    })

    it('zeigt einen aktiven Lauf ohne Startstempel ohne Zahl', () => {
        const chip = matchStatusChip(status({state: 'RUNNING'}), minutesAgo(6), NOW)
        expect(chip).toEqual({labelKey: 'event.match.status.runningPlain', color: 'primary'})
    })

    it('meldet einen teilweise gewerteten Lauf mit gewertet/gesamt', () => {
        const chip = matchStatusChip(status({teamsScored: 4}), minutesAgo(20), NOW)
        expect(chip).toEqual({
            labelKey: 'event.match.status.partiallyScored',
            values: {scored: 4, total: 6},
            color: 'warning',
        })
    })

    it('zeigt einen vollständig gewerteten, aber nicht beendeten Lauf als „Wartet auf Beenden"', () => {
        const chip = matchStatusChip(
            status({state: 'AWAITING_FINISH', teamsScored: 6}),
            minutesAgo(20),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.awaitingFinish', color: 'warning'})
    })

    it('zeigt einen beendeten Lauf als „Beendet"', () => {
        const chip = matchStatusChip(
            status({state: 'FINISHED', teamsScored: 6, startedAt: minutesAgo(30)}),
            minutesAgo(35),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.finished', color: 'success'})
    })

    it('zeigt einen abgesagten Lauf durchgestrichen', () => {
        const chip = matchStatusChip(status({state: 'SKIPPED'}), minutesAgo(35), NOW)
        expect(chip).toEqual({
            labelKey: 'event.match.status.cancelled',
            color: 'default',
            strikeThrough: true,
        })
    })

    it('zeigt einen ungeplanten Lauf als „Ungeplant"', () => {
        const chip = matchStatusChip(status({state: 'UNSCHEDULED'}), null, NOW)
        expect(chip).toEqual({labelKey: 'event.match.status.unscheduled', color: 'default'})
    })

    // --- Fehlerfälle (Spec Abschnitt 6) ---

    it('behauptet bei einem Lauf ohne Mannschaften keine Teilwertung', () => {
        const chip = matchStatusChip(status({teamsTotal: 0, teamsScored: 0}), minutesAgo(1), NOW)
        expect(chip.labelKey).toBe('event.match.status.upcoming')
    })

    it('zeigt einen abgesagten, aber trotzdem aktiven Lauf als „Läuft"', () => {
        // Der Server liefert hier RUNNING (deriveMatchState: was passiert, schlägt den
        // zurückgenommenen Plan) — der Chip folgt dieser Reihenfolge unverändert.
        const chip = matchStatusChip(
            status({state: 'RUNNING', startedAt: minutesAgo(2)}),
            minutesAgo(9),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.running')
    })

    it('nennt einen abgesagten Lauf mit Teilergebnissen nicht „Teilweise gewertet"', () => {
        const chip = matchStatusChip(
            status({state: 'SKIPPED', teamsScored: 3}),
            minutesAgo(35),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.cancelled')
    })

    it('nennt einen Lauf ohne Startzeit niemals überfällig', () => {
        expect(matchStatusChip(status({state: 'UNSCHEDULED'}), null, NOW).labelKey).toBe(
            'event.match.status.unscheduled',
        )
        expect(matchStatusChip(status({}), undefined, NOW).labelKey).toBe(
            'event.match.status.upcoming',
        )
    })

    it('rechnet bei nachgehender Browseruhr keine negative Laufzeit', () => {
        const chip = matchStatusChip(
            status({state: 'RUNNING', startedAt: minutesAgo(-15)}),
            minutesAgo(-20),
            NOW,
        )
        expect(chip.values).toEqual({minutes: 0})
    })
})

describe('waterChip', () => {
    it('entfällt, solange der Wasserstand nicht erhoben wird', () => {
        expect(waterChip(status({state: 'RUNNING'}))).toBeNull()
        expect(waterChip(status({state: 'RUNNING', teamsOnWater: undefined}))).toBeNull()
    })

    it('zeigt den Wasserstand, solange nicht alle Crews draußen sind', () => {
        expect(waterChip(status({state: 'UPCOMING', teamsOnWater: 4}))).toEqual({
            labelKey: 'event.match.status.water',
            values: {onWater: 4, total: 6},
            color: 'default',
        })
    })

    it('entfällt, sobald alle Crews draußen sind', () => {
        expect(waterChip(status({state: 'RUNNING', teamsOnWater: 6}))).toBeNull()
    })

    it('entfällt bei einem Lauf ohne Mannschaften', () => {
        expect(waterChip(status({state: 'UPCOMING', teamsTotal: 0, teamsOnWater: 0}))).toBeNull()
    })

    it('entfällt bei beendeten und abgesagten Läufen', () => {
        expect(waterChip(status({state: 'FINISHED', teamsOnWater: 0}))).toBeNull()
        expect(waterChip(status({state: 'SKIPPED', teamsOnWater: 0}))).toBeNull()
        expect(waterChip(status({state: 'AWAITING_FINISH', teamsOnWater: 0}))).toBeNull()
    })
})

describe('roundCounterChips', () => {
    it('zählt jeden Lauf in genau einen Topf und lässt leere Töpfe weg', () => {
        const chips = roundCounterChips([
            status({state: 'RUNNING'}),
            status({state: 'UPCOMING'}),
            status({state: 'FINISHED'}),
            status({state: 'FINISHED'}),
            status({state: 'FINISHED'}),
            status({state: 'SKIPPED'}),
        ])
        expect(chips).toEqual([
            {labelKey: 'event.match.status.counter.running', values: {n: 1}, color: 'primary'},
            {labelKey: 'event.match.status.counter.open', values: {n: 1}, color: 'default'},
            {labelKey: 'event.match.status.counter.finished', values: {n: 3}, color: 'success'},
            {labelKey: 'event.match.status.counter.cancelled', values: {n: 1}, color: 'default'},
        ])
    })

    it('rechnet wartende und ungeplante Läufe zu den offenen', () => {
        const chips = roundCounterChips([
            status({state: 'AWAITING_FINISH'}),
            status({state: 'UNSCHEDULED'}),
            status({state: 'UPCOMING'}),
        ])
        expect(chips).toEqual([
            {labelKey: 'event.match.status.counter.open', values: {n: 3}, color: 'default'},
        ])
    })

    it('bleibt bei einer einzigen Lauf-Karte stumm', () => {
        expect(roundCounterChips([status({state: 'RUNNING'})])).toEqual([])
        expect(roundCounterChips([])).toEqual([])
    })
})

const slot = (overrides: Partial<EventScheduleSlotDto>): EventScheduleSlotDto => ({
    id: 'slot-1',
    startTime: minutesAgo(10),
    state: 'LINKED',
    matchCurrentlyRunning: false,
    matchTeamsTotal: 6,
    matchTeamsScored: 0,
    matchId: 'match-1',
    ...overrides,
})

describe('slotMatchStatus', () => {
    it('lässt einen Slot ohne verknüpften Lauf bei seinem eigenen Chip', () => {
        expect(slotMatchStatus(slot({matchId: null, state: 'FREE'}))).toBeNull()
        expect(slotMatchStatus(slot({matchId: undefined, state: 'WAITING'}))).toBeNull()
    })

    it('liest den aktiven Lauf als RUNNING, auch wenn der Slot abgesagt ist', () => {
        const status = slotMatchStatus(
            slot({state: 'SKIPPED', matchCurrentlyRunning: true, matchStartedAt: minutesAgo(3)}),
        )
        expect(status?.state).toBe('RUNNING')
        expect(status?.startedAt).toBe(minutesAgo(3))
    })

    it('liest einen beendeten Lauf als FINISHED', () => {
        expect(slotMatchStatus(slot({matchFinishedAt: minutesAgo(2)}))?.state).toBe('FINISHED')
    })

    it('liest einen abgesagten Slot als SKIPPED', () => {
        expect(slotMatchStatus(slot({state: 'SKIPPED'}))?.state).toBe('SKIPPED')
    })

    it('liest einen vollständig gewerteten Lauf als AWAITING_FINISH', () => {
        expect(slotMatchStatus(slot({matchTeamsScored: 6}))?.state).toBe('AWAITING_FINISH')
    })

    it('lässt einen Lauf ohne Mannschaften nicht auf AWAITING_FINISH laufen', () => {
        expect(slotMatchStatus(slot({matchTeamsTotal: 0, matchTeamsScored: 0}))?.state).toBe(
            'UPCOMING',
        )
    })

    it('reicht die Zählungen für die Teilwertung durch', () => {
        expect(slotMatchStatus(slot({matchTeamsScored: 2}))).toEqual({
            state: 'UPCOMING',
            startedAt: undefined,
            teamsTotal: 6,
            teamsScored: 2,
        })
    })
})
