import {describe, expect, it} from 'vitest'
import {EventScheduleSlotDto, MatchByeDto, MatchStatusDto} from '@api/types.gen.ts'
import {
    OVERDUE_GRACE_MINUTES,
    matchStatusChip,
    roundCounterChips,
    slotMatchStatus,
    arenaChip,
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

    it('zeigt einen Lauf am Start als „In Vorbereitung“', () => {
        const chip = matchStatusChip(status({state: 'PREPARING'}), minutesAgo(20), NOW)
        expect(chip).toEqual({labelKey: 'event.match.status.preparing', color: 'info'})
    })

    /**
     * „Überfällig" misst den Verzug gegen den Plan — bei einem Lauf, der am Start steht, ist der
     * Plan bereits eingeholt: der Schiedsrichter hat ihn aufgerufen. Der Zweig muss deshalb vor der
     * Verzugsrechnung greifen, sonst blinkte ausgerechnet der Lauf rot, um den sich gerade jemand
     * kümmert.
     */
    it('macht aus einem Lauf am Start keinen überfälligen', () => {
        const chip = matchStatusChip(status({state: 'PREPARING'}), minutesAgo(60), NOW)
        expect(chip.labelKey).not.toBe('event.match.status.overdue')
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

describe('arenaChip', () => {
    /**
     * Zwei Wege führen hierher: eine Ansicht, die die Check-in-Daten gar nicht holt (Zeitplan,
     * öffentliche Anzeigen), und eine Veranstaltung ohne Check-in — dort schickt der Server für
     * jeden Lauf der Runde null statt 0, damit nicht dauerhaft „Arena 0/6" dasteht. Auf der
     * Leitung ist beides dasselbe: das Feld fehlt (Jackson schreibt nulls nicht mit).
     */
    it('entfällt, solange der Stand nicht erhoben wird', () => {
        expect(arenaChip(status({state: 'RUNNING'}))).toBeNull()
        expect(arenaChip(status({state: 'RUNNING', teamsInArena: undefined}))).toBeNull()
    })

    /** Erhoben und niemand draußen ist eine Aussage — die 0 wird gezeigt, nicht verschluckt. */
    it('zeigt die erhobene Null', () => {
        expect(arenaChip(status({state: 'UPCOMING', teamsInArena: 0}))).toEqual({
            labelKey: 'event.match.status.inArena',
            values: {inArena: 0, total: 6},
            color: 'default',
        })
    })

    it('zeigt den Stand, solange nicht alle Crews draußen sind', () => {
        expect(arenaChip(status({state: 'UPCOMING', teamsInArena: 4}))).toEqual({
            labelKey: 'event.match.status.inArena',
            values: {inArena: 4, total: 6},
            color: 'default',
        })
    })

    it('entfällt, sobald alle Crews draußen sind', () => {
        expect(arenaChip(status({state: 'RUNNING', teamsInArena: 6}))).toBeNull()
    })

    /** Beim Lauf am Start ist die Frage, wer schon draußen ist, am dringendsten. */
    it('steht auch bei einem Lauf in Vorbereitung', () => {
        expect(arenaChip(status({state: 'PREPARING', teamsInArena: 2}))).toEqual({
            labelKey: 'event.match.status.inArena',
            values: {inArena: 2, total: 6},
            color: 'default',
        })
    })

    it('entfällt bei einem Lauf ohne Mannschaften', () => {
        expect(arenaChip(status({state: 'UPCOMING', teamsTotal: 0, teamsInArena: 0}))).toBeNull()
    })

    it('entfällt bei beendeten und abgesagten Läufen', () => {
        expect(arenaChip(status({state: 'FINISHED', teamsInArena: 0}))).toBeNull()
        expect(arenaChip(status({state: 'SKIPPED', teamsInArena: 0}))).toBeNull()
        expect(arenaChip(status({state: 'AWAITING_FINISH', teamsInArena: 0}))).toBeNull()
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

    /**
     * Eigener Topf, und zwar vor „läuft": ein Lauf am Start ist weder unterwegs noch bloß offen.
     * Dieselbe Aufteilung wie `MatchStatusLogic.roundCounters` im Backend.
     */
    it('zählt vorbereitete Läufe in einen eigenen Topf', () => {
        const chips = roundCounterChips([status({state: 'PREPARING'}), status({state: 'RUNNING'})])
        expect(chips).toEqual([
            {labelKey: 'event.match.status.counter.preparing', values: {n: 1}, color: 'info'},
            {labelKey: 'event.match.status.counter.running', values: {n: 1}, color: 'primary'},
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
    matchActivatedAt: null,
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
            slot({state: 'SKIPPED', matchActivatedAt: minutesAgo(4), matchStartedAt: minutesAgo(3)}),
        )
        expect(status?.state).toBe('RUNNING')
        expect(status?.startedAt).toBe(minutesAgo(3))
    })

    it('leitet PREPARING aus einem aktivierten, nicht gestarteten Slot ab', () => {
        const status = slotMatchStatus(
            slot({matchActivatedAt: minutesAgo(5), matchStartedAt: null}),
        )
        expect(status?.state).toBe('PREPARING')
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

describe('slotMatchStatus beim Freilos', () => {
    const slot = (overrides: Partial<EventScheduleSlotDto>): EventScheduleSlotDto =>
        ({
            id: 'slot-1',
            startTime: minutesAgo(10),
            state: 'LINKED',
            matchId: 'match-1',
            matchTeamsTotal: 1,
            matchTeamsScored: 1,
            ...overrides,
        }) as EventScheduleSlotDto

    it('trägt das Freilos in den Zeitplan-Status', () => {
        const status = slotMatchStatus(slot({bye: {cause: 'NO_OPPONENT', mustRace: false}}))
        expect(status?.bye).toEqual({cause: 'NO_OPPONENT', mustRace: false})
        expect(matchStatusChip(status!, slot({}).startTime, NOW).labelKey).toBe(
            'event.match.status.bye.open',
        )
    })
})

const structuralBye: MatchByeDto = {cause: 'NO_OPPONENT', mustRace: false}
const withdrawalBye: MatchByeDto = {
    cause: 'DEREGISTRATION',
    teamName: 'RV Hansa',
    reason: 'Krankheit',
    mustRace: false,
}

describe('matchStatusChip beim Freilos', () => {
    /**
     * „Muss gefahren werden": kein Freilos-Chip - der Lauf wird gefahren, auf sein Ergebnis
     * wartet jemand, also sagen die normalen Zustände die Wahrheit.
     */
    it('zeigt bei mustRace die normalen Zustände statt des Freilos-Chips', () => {
        const mustRaceBye: MatchByeDto = {cause: 'NO_OPPONENT', mustRace: true}
        expect(
            matchStatusChip(status({state: 'UPCOMING', bye: mustRaceBye}), minutesAgo(1), NOW)
                .labelKey,
        ).toBe('event.match.status.upcoming')
        expect(
            matchStatusChip(status({state: 'FINISHED', bye: mustRaceBye}), minutesAgo(30), NOW)
                .labelKey,
        ).toBe('event.match.status.finished')
    })

    it('sagt „offen", solange niemand quittiert hat', () => {
        const chip = matchStatusChip(
            status({state: 'AWAITING_FINISH', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.bye.open', color: 'info'})
    })

    /** „Überfällig" würde ein Ergebnis einfordern, auf das niemand wartet. */
    it('wird nie überfällig', () => {
        const chip = matchStatusChip(
            status({state: 'UPCOMING', bye: structuralBye}),
            minutesAgo(OVERDUE_GRACE_MINUTES + 60),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.bye.open')
    })

    /** Ebenso wenig „Teilweise gewertet": zu werten gibt es hier nichts. */
    it('wird nie teilweise gewertet', () => {
        const chip = matchStatusChip(
            status({state: 'UPCOMING', teamsTotal: 2, teamsScored: 1, bye: withdrawalBye}),
            minutesAgo(1),
            NOW,
        )
        expect(chip.labelKey).toBe('event.match.status.bye.open')
    })

    it('sagt „quittiert", sobald der Lauf beendet ist', () => {
        const chip = matchStatusChip(
            status({state: 'FINISHED', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({labelKey: 'event.match.status.bye.acknowledged', color: 'success'})
    })

    it('sagt „entfallen" und streicht durch, wenn der Slot abgesagt ist', () => {
        const chip = matchStatusChip(
            status({state: 'SKIPPED', bye: structuralBye}),
            minutesAgo(30),
            NOW,
        )
        expect(chip).toEqual({
            labelKey: 'event.match.status.bye.cancelled',
            color: 'default',
            strikeThrough: true,
        })
    })

    /** Was tatsächlich passiert, schlägt weiterhin alles: aktiviert heißt aktiviert. */
    it('tritt hinter einen aktivierten Lauf zurück', () => {
        expect(
            matchStatusChip(status({state: 'PREPARING', bye: structuralBye}), minutesAgo(1), NOW)
                .labelKey,
        ).toBe('event.match.status.preparing')
        expect(
            matchStatusChip(
                status({state: 'RUNNING', startedAt: minutesAgo(4), bye: structuralBye}),
                minutesAgo(5),
                NOW,
            ).labelKey,
        ).toBe('event.match.status.running')
    })

    it('lässt den Arena-Chip schweigen', () => {
        expect(
            arenaChip(
                status({state: 'UPCOMING', teamsTotal: 1, teamsInArena: 0, bye: structuralBye}),
            ),
        ).toBeNull()
    })
})
