import {describe, expect, it} from 'vitest'
import {LiveDashboardMatchDto, LiveDashboardTeamDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    buildLiveDashboardTimeline,
    crewMemberLabel,
    dashboardCrew,
    dashboardEntryDomId,
    dashboardEntryDomIdCandidates,
    dashboardScope,
    liveMatches,
    matchControls,
    nextUpEntry,
    openResultTeams,
    pendingSlotLabel,
    teamHasResult,
    teamShowsClubLine,
    teamShowsCrew,
} from './common.ts'

const team = (overrides: Partial<LiveDashboardTeamDto>): LiveDashboardTeamDto => ({
    teamId: crypto.randomUUID(),
    clubsShort: '',
    clubsFull: '',
    failed: false,
    deregistered: false,
    invoiceState: 'NONE',
    severity: 'NEUTRAL',
    invoiceSeverity: 'NEUTRAL',
    onWaterRequired: false,
    onWaterSeverity: 'NEUTRAL',
    substituted: false,
    ...overrides,
})

describe('teamHasResult', () => {
    it('zählt Platz, Zeit und Ausscheiden als Ergebnis', () => {
        expect(teamHasResult(team({place: 1}))).toBe(true)
        expect(teamHasResult(team({time: '07:12.340'}))).toBe(true)
        expect(teamHasResult(team({failed: true, failedReason: 'DNF'}))).toBe(true)
    })

    it('erwartet von abgemeldeten Booten kein Ergebnis', () => {
        expect(teamHasResult(team({deregistered: true}))).toBe(true)
    })

    it('erkennt ein offenes Boot', () => {
        expect(teamHasResult(team({}))).toBe(false)
    })
})

describe('openResultTeams', () => {
    it('liefert nur die Boote ohne Ergebnis', () => {
        const open = team({startNumber: 3})
        const teams = [
            team({startNumber: 1, place: 1}),
            team({startNumber: 2, deregistered: true}),
            open,
            team({startNumber: 4, failed: true}),
        ]

        expect(openResultTeams({teams})).toEqual([open])
    })

    it('liefert bei vollständigen Ergebnissen nichts', () => {
        expect(openResultTeams({teams: [team({place: 1}), team({place: 2})]})).toEqual([])
    })
})

const match = (overrides: Partial<LiveDashboardMatchDto>): LiveDashboardMatchDto => ({
    matchId: crypto.randomUUID(),
    state: 'UPCOMING',
    competitionId: crypto.randomUUID(),
    competitionName: 'CM 1x',
    executionOrder: 0,
    currentlyRunning: false,
    teams: [],
    ...overrides,
})

const pendingSlot = (overrides: Partial<PendingSlotDto>): PendingSlotDto => ({
    slotId: crypto.randomUUID(),
    startTime: '2026-08-17T08:00:00',
    ...overrides,
})

describe('liveMatches', () => {
    it('nimmt laufende Läufe und solche, die auf ihr Beenden warten', () => {
        const laeuft = match({matchId: 'laeuft', state: 'RUNNING', currentlyRunning: true})
        const wartet = match({matchId: 'wartet', state: 'AWAITING_FINISH'})
        const beendet = match({matchId: 'beendet', state: 'FINISHED'})
        const anstehend = match({matchId: 'anstehend', state: 'UPCOMING'})
        const abgesagt = match({matchId: 'abgesagt', state: 'SKIPPED'})

        expect(
            liveMatches([beendet, laeuft, wartet, anstehend, abgesagt]).map(m => m.matchId),
        ).toEqual(['laeuft', 'wartet'])
    })
})

describe('dashboardScope', () => {
    it('holt schmal im Live-Tab nur den Live-Ausschnitt', () => {
        expect(dashboardScope(false, 'live')).toBe('LIVE')
    })

    it('holt schmal im Läufe-Tab die Gesamtliste', () => {
        expect(dashboardScope(false, 'matches')).toBe('ALL')
    })

    it('holt breit immer die Gesamtliste, weil beide Spalten sichtbar sind', () => {
        expect(dashboardScope(true, 'live')).toBe('ALL')
        expect(dashboardScope(true, 'matches')).toBe('ALL')
    })
})

describe('dashboardCrew', () => {
    it('bestellt die Crew erst am Laptop mit', () => {
        expect(dashboardCrew(1440)).toBe(true)
        expect(dashboardCrew(1920)).toBe(true)
    })

    it('lässt die Nutzlast am Telefon und in der Tablet-Spalte unverändert', () => {
        expect(dashboardCrew(390)).toBe(false)
        expect(dashboardCrew(1439)).toBe(false)
    })
})

describe('teamShowsCrew', () => {
    it('zeigt die Crew, sobald sie im Datensatz steht', () => {
        expect(teamShowsCrew(team({crew: [{lastName: 'Meier', clubShort: 'RC Bergedorf'}]}))).toBe(
            true,
        )
    })

    // Der Fall unmittelbar nach dem Verbreitern des Fensters: die Karte ist schon breit genug, der
    // Abruf mit crew=true aber noch unterwegs. Dann bleibt es bei Stufe 2.
    it('fällt ohne Crew im Datensatz auf Stufe 2 zurück', () => {
        expect(teamShowsCrew(team({}))).toBe(false)
        expect(teamShowsCrew(team({crew: null}))).toBe(false)
        expect(teamShowsCrew(team({crew: []}))).toBe(false)
    })
})

describe('crewMemberLabel', () => {
    it('setzt die Rolle in Klammern hinter Nachname und Vereinskurzform', () => {
        expect(
            crewMemberLabel({lastName: 'Meier', clubShort: 'RC Bergedorf', role: 'Ste.'}),
        ).toBe('Meier · RC Bergedorf (Ste.)')
    })

    it('lässt weg, was fehlt, statt Trennzeichen ins Leere zu setzen', () => {
        expect(crewMemberLabel({lastName: 'Meier'})).toBe('Meier')
        expect(crewMemberLabel({lastName: 'Meier', role: 'Ste.'})).toBe('Meier (Ste.)')
    })
})

describe('teamShowsClubLine', () => {
    it('zeigt die Kette unter einem Mannschaftsnamen, der sie nicht enthält', () => {
        expect(
            teamShowsClubLine(
                team({teamName: '#1', clubsShort: 'RC Bergedorf', clubsFull: 'Ruderclub Bergedorf e.V.'}),
            ),
        ).toBe(true)
    })

    it('lässt sie weg, wenn der Name den Verein schon trägt — in beiden Fassungen', () => {
        expect(
            teamShowsClubLine(
                team({
                    teamName: 'Ruderclub Bergedorf e.V. #1',
                    clubsShort: 'RC Bergedorf',
                    clubsFull: 'Ruderclub Bergedorf e.V.',
                }),
            ),
        ).toBe(false)
        expect(
            teamShowsClubLine(
                team({
                    teamName: 'RC Bergedorf #1',
                    clubsShort: 'RC Bergedorf',
                    clubsFull: 'Ruderclub Bergedorf e.V.',
                }),
            ),
        ).toBe(false)
    })

    // Ohne Mannschaftsname steht die Kette bereits als Überschrift der Zeile.
    it('lässt sie weg, wenn es keinen Mannschaftsnamen und keine Kette gibt', () => {
        expect(teamShowsClubLine(team({clubsShort: 'RC Bergedorf', clubsFull: 'Ruderclub Bergedorf'}))).toBe(
            false,
        )
        expect(teamShowsClubLine(team({teamName: '#1'}))).toBe(false)
    })
})

describe('dashboardEntryDomId', () => {
    it('unterscheidet dieselbe Karte in Live-Spalte und Gesamtliste', () => {
        expect(dashboardEntryDomId('abc', 'live')).not.toBe(dashboardEntryDomId('abc', 'list'))
    })

    it('sucht beim Zeitstrahl-Klick zuerst in der Gesamtliste', () => {
        expect(dashboardEntryDomIdCandidates('abc')).toEqual([
            dashboardEntryDomId('abc', 'list'),
            dashboardEntryDomId('abc', 'live'),
        ])
    })
})

describe('matchControls', () => {
    it('bietet bei einem laufenden Lauf Beenden und Deaktivieren an', () => {
        expect(
            matchControls(match({state: 'RUNNING', currentlyRunning: true}), true, true),
        ).toEqual({
            showFinish: true,
            showRunToggle: true,
        })
    })

    it('ersetzt beim wartenden Lauf "Lauf aktivieren" durch "Lauf beenden"', () => {
        expect(matchControls(match({state: 'AWAITING_FINISH'}), true, true)).toEqual({
            showFinish: true,
            showRunToggle: false,
        })
    })

    it('bietet bei einem beendeten Lauf nur noch das Aktivieren an', () => {
        expect(matchControls(match({state: 'FINISHED'}), true, true)).toEqual({
            showFinish: false,
            showRunToggle: true,
        })
    })

    it('lässt einen abgesagten Lauf ohne jede Schaltfläche', () => {
        expect(matchControls(match({state: 'SKIPPED'}), true, true)).toEqual({
            showFinish: false,
            showRunToggle: false,
        })
    })

    it('zeigt nichts, wozu die Rechte fehlen', () => {
        expect(matchControls(match({state: 'AWAITING_FINISH'}), false, true)).toEqual({
            showFinish: false,
            showRunToggle: false,
        })
        expect(
            matchControls(match({state: 'RUNNING', currentlyRunning: true}), false, false),
        ).toEqual({
            showFinish: false,
            showRunToggle: false,
        })
    })
})

describe('buildLiveDashboardTimeline', () => {
    it('sortiert Läufe und wartende Slots gemeinsam nach Startzeit', () => {
        const early = match({matchId: 'early', startTime: '2026-08-17T08:00:00'})
        const late = match({matchId: 'late', startTime: '2026-08-17T09:00:00'})
        const between = pendingSlot({slotId: 'between', startTime: '2026-08-17T08:30:00'})

        const timeline = buildLiveDashboardTimeline([late, early], [between])

        expect(
            timeline.map(entry =>
                entry.kind === 'match' ? entry.match.matchId : entry.slot.slotId,
            ),
        ).toEqual(['early', 'between', 'late'])
    })

    it('reiht Läufe ohne Startzeit ans Ende ein', () => {
        const scheduled = match({matchId: 'scheduled', startTime: '2026-08-17T08:00:00'})
        const unscheduled = match({matchId: 'unscheduled', startTime: undefined})

        const timeline = buildLiveDashboardTimeline([unscheduled, scheduled], [])

        expect(
            timeline.map(entry =>
                entry.kind === 'match' ? entry.match.matchId : entry.slot.slotId,
            ),
        ).toEqual(['scheduled', 'unscheduled'])
    })
})

describe('nextUpEntry', () => {
    // Nachgestellt an der Förde Testregatta: um 22:32 stand die Wettkampfrichter-Besprechung von
    // 20:00 immer noch als "Als Nächstes", während der Lauf um 22:50 unsichtbar blieb.
    const now = new Date('2026-08-17T22:32:00')

    it('überspringt einen längst vergangenen Programmpunkt', () => {
        const besprechung = pendingSlot({
            slotId: 'besprechung',
            startTime: '2026-08-17T20:00:00',
            name: 'Wettkampfrichter-Besprechung',
        })
        const naechsterLauf = match({matchId: 'lauf', startTime: '2026-08-17T22:50:00'})

        const entry = nextUpEntry(naechsterLauf, [besprechung], now)

        expect(entry?.kind).toBe('match')
        expect(entry?.kind === 'match' && entry.match.matchId).toBe('lauf')
    })

    it('behält einen gerade erst überfälligen Slot', () => {
        const eben = pendingSlot({slotId: 'eben', startTime: '2026-08-17T22:20:00'})
        const spaeter = match({matchId: 'lauf', startTime: '2026-08-17T22:50:00'})

        const entry = nextUpEntry(spaeter, [eben], now)

        expect(entry?.kind === 'pending' && entry.slot.slotId).toBe('eben')
    })

    it('lässt einen Slot genau auf der Nachfrist fallen', () => {
        const grenze = pendingSlot({slotId: 'grenze', startTime: '2026-08-17T22:02:00'})
        const spaeter = match({matchId: 'lauf', startTime: '2026-08-17T22:50:00'})

        const entry = nextUpEntry(spaeter, [grenze], now)

        expect(entry?.kind === 'match' && entry.match.matchId).toBe('lauf')
    })

    it('zeigt einen überfälligen echten Lauf weiterhin an', () => {
        // Ein überfälliger Lauf ist genau das, was der Schiedsrichter noch starten muss — und im
        // Live-Tab liefert der Server ohnehin nur diesen einen anstehenden Lauf.
        const ueberfaellig = match({matchId: 'lauf', startTime: '2026-08-17T20:00:00'})

        const entry = nextUpEntry(ueberfaellig, [], now)

        expect(entry?.kind === 'match' && entry.match.matchId).toBe('lauf')
    })

    it('liefert nichts, wenn weder Lauf noch gültiger Slot übrig sind', () => {
        const alt = pendingSlot({slotId: 'alt', startTime: '2026-08-17T20:30:00'})

        expect(nextUpEntry(undefined, [alt], now)).toBeUndefined()
    })

    it('nimmt den nächsten gültigen Slot vor dem nächsten Lauf', () => {
        const alt = pendingSlot({slotId: 'alt', startTime: '2026-08-17T20:30:00'})
        const gueltig = pendingSlot({slotId: 'gueltig', startTime: '2026-08-17T22:40:00'})
        const lauf = match({matchId: 'lauf', startTime: '2026-08-17T22:50:00'})

        const entry = nextUpEntry(lauf, [alt, gueltig], now)

        expect(entry?.kind === 'pending' && entry.slot.slotId).toBe('gueltig')
    })
})

describe('pendingSlotLabel', () => {
    it('setzt Wettkampf, Runde und Lauf mit Trennzeichen zusammen', () => {
        expect(
            pendingSlotLabel(
                pendingSlot({
                    competitionName: 'CM 1x',
                    roundName: 'Achtelfinale',
                    matchName: 'AF1',
                }),
            ),
        ).toBe('CM 1x · Achtelfinale · AF1')
    })

    it('lässt fehlende Teile weg', () => {
        expect(pendingSlotLabel(pendingSlot({competitionName: 'CM 1x'}))).toBe('CM 1x')
    })
})
