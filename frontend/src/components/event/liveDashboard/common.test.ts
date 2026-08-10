import {describe, expect, it} from 'vitest'
import {LiveDashboardMatchDto, LiveDashboardTeamDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    buildLiveDashboardTimeline,
    competitionLabel,
    crewMemberLabel,
    dashboardCrew,
    dashboardEntryDomId,
    dashboardEntryDomIdCandidates,
    dashboardMatchStatus,
    dashboardScope,
    isLiveMatch,
    liveMatches,
    matchControls,
    nextUpEntry,
    openResultTeams,
    pendingSlotLabel,
    shortenClubChain,
    teamHasResult,
    teamsInDisplayOrder,
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
    inArenaRequired: false,
    inArenaSeverity: 'NEUTRAL',
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

describe('teamsInDisplayOrder', () => {
    const lanes = (teams: LiveDashboardTeamDto[]): (number | null | undefined)[] =>
        teamsInDisplayOrder(teams).map(t => t.startNumber)

    it('lässt die Reihenfolge nach Startnummer stehen, solange nichts gewertet ist', () => {
        const teams = [team({startNumber: 1}), team({startNumber: 2}), team({startNumber: 3})]

        expect(lanes(teams)).toEqual([1, 2, 3])
    })

    // Eine Abmeldung steht oft schon vor dem Start fest und sortiert deshalb noch nichts um.
    it('sortiert wegen einer bloßen Abmeldung noch nicht um', () => {
        const teams = [
            team({startNumber: 1, deregistered: true}),
            team({startNumber: 2}),
            team({startNumber: 3}),
        ]

        expect(lanes(teams)).toEqual([1, 2, 3])
    })

    it('stellt bei vollständiger Wertung den Ersten nach oben', () => {
        const teams = [
            team({startNumber: 1, place: 3}),
            team({startNumber: 2, place: 1}),
            team({startNumber: 3, place: 2}),
        ]

        expect(lanes(teams)).toEqual([2, 3, 1])
    })

    it('reiht offene Boote vor die ausgeschiedenen und die abgemeldeten ganz nach hinten', () => {
        const teams = [
            team({startNumber: 1, deregistered: true}),
            team({startNumber: 2, failed: true, failedReason: 'DSQ'}),
            team({startNumber: 3}),
            team({startNumber: 4, place: 2}),
            team({startNumber: 5, place: 1}),
        ]

        expect(lanes(teams)).toEqual([5, 4, 3, 2, 1])
    })

    // Der Fall von Thomas' Bildschirm: Nr. 1 Platz 1, Nr. 2 Platz 3, Nr. 3 DSQ, Nr. 4 Platz 2,
    // Nr. 5 DNF.
    it('bringt den echten Lauf in die Reihenfolge 1, 4, 2, 3, 5', () => {
        const teams = [
            team({startNumber: 1, place: 1}),
            team({startNumber: 2, place: 3}),
            team({startNumber: 3, failed: true, failedReason: 'DSQ'}),
            team({startNumber: 4, place: 2}),
            team({startNumber: 5, failed: true, failedReason: 'DNF'}),
        ]

        expect(lanes(teams)).toEqual([1, 4, 2, 3, 5])
    })

    it('lässt die übergebene Liste unangetastet', () => {
        const teams = [team({startNumber: 1, place: 2}), team({startNumber: 2, place: 1})]

        teamsInDisplayOrder(teams)

        expect(teams.map(t => t.startNumber)).toEqual([1, 2])
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
        const laeuft = match({matchId: 'laeuft', state: 'RUNNING'})
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
        expect(crewMemberLabel({lastName: 'Meier', clubShort: 'RC Bergedorf', role: 'Ste.'})).toBe(
            'Meier · RC Bergedorf (Ste.)',
        )
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
                team({
                    teamName: '#1',
                    clubsShort: 'RC Bergedorf',
                    clubsFull: 'Ruderclub Bergedorf e.V.',
                }),
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

    // Der Regelfall: die wenigsten Boote haben einen Mannschaftsnamen. Die Kette steht trotzdem
    // in der kleinen grauen Zeile und nie in der Überschrift.
    it('zeigt sie auch ohne Mannschaftsnamen', () => {
        expect(
            teamShowsClubLine(
                team({clubsShort: 'RC Bergedorf', clubsFull: 'Ruderclub Bergedorf e.V.'}),
            ),
        ).toBe(true)
    })

    it('lässt sie weg, wenn es gar keine Kette gibt', () => {
        expect(teamShowsClubLine(team({teamName: '#1'}))).toBe(false)
        expect(teamShowsClubLine(team({}))).toBe(false)
    })
})

describe('shortenClubChain', () => {
    it('lässt eine Kette, die in zwei Zeilen passt, unangetastet', () => {
        const fuenf = 'Mainzer RV / Marburger RV / RK Flensburg / RC Nürtingen / 1. KRC'

        expect(shortenClubChain(fuenf)).toBe(fuenf)
        expect(shortenClubChain('RC Bergedorf')).toBe('RC Bergedorf')
        expect(shortenClubChain('')).toBe('')
    })

    it('kürzt hinten mit "+n" und lässt die verbleibenden Vereinsnamen ganz', () => {
        expect(
            shortenClubChain(
                'Humlebæk Roklub / Nakskov Roklub / Kerteminde Roklub / Rungsted Roklub',
                40,
            ),
        ).toBe('Humlebæk Roklub / Nakskov Roklub +2')
    })

    // Entschieden wird nach Textlänge, nicht nach Vereinszahl: dieselbe Grenze lässt zwei kurze
    // dänische Vereine stehen und kappt zwei lange deutsche schon nach dem ersten.
    it('entscheidet nach Länge, nicht nach Anzahl', () => {
        expect(shortenClubChain('Humlebæk Roklub / Nakskov Roklub / Rungsted Roklub', 40)).toBe(
            'Humlebæk Roklub / Nakskov Roklub +1',
        )
        expect(
            shortenClubChain(
                'Rostocker Ruder-Club von 1885 / Akademischer Ruderverein Kiel / Erster Kieler Ruder-Club',
                40,
            ),
        ).toBe('Rostocker Ruder-Club von 1885 +2')
    })

    // Lieber ein einzelner überlanger Name, der in der Zeilenbegrenzung ausläuft, als ein halber
    // Verein: ein Vereinsname wird nie zerrissen.
    it('zerreißt keinen Vereinsnamen, auch wenn er allein zu lang ist', () => {
        expect(
            shortenClubChain(
                'Ruder-Club Allemannia von 1866 - Leuphana Universität Lüneburg / RC Bergedorf',
                40,
            ),
        ).toBe('Ruder-Club Allemannia von 1866 - Leuphana Universität Lüneburg +1')
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

describe('isLiveMatch', () => {
    /**
     * Ein Lauf am Start gehört in die Live-Spalte: auf ihm liegt die nächste Handlung des
     * Schiedsrichters. Gegenstück zu `LiveDashboardLogic.selectForScope(LIVE)` im Backend, das
     * PREPARING aus demselben Grund mitliefert.
     */
    it('zeigt einen Lauf in Vorbereitung im Live-Ausschnitt', () => {
        expect(isLiveMatch(match({state: 'PREPARING'}))).toBe(true)
    })
})

describe('matchControls', () => {
    it('bietet bei einem Lauf in Vorbereitung den Läuft-Knopf an', () => {
        const controls = matchControls(match({state: 'PREPARING'}), true, true)
        expect(controls.showMarkStarted).toBe(true)
        expect(controls.showActivationToggle).toBe(true)
    })

    it('bietet den Läuft-Knopf nicht mehr an, sobald der Lauf unterwegs ist', () => {
        expect(matchControls(match({state: 'RUNNING'}), true, true).showMarkStarted).toBe(false)
    })

    it('bietet bei einem laufenden Lauf Beenden und Deaktivieren an', () => {
        expect(matchControls(match({state: 'RUNNING'}), true, true)).toEqual({
            showFinish: true,
            showActivationToggle: true,
            showMarkStarted: false,
        })
    })

    it('ersetzt beim wartenden Lauf "Lauf aktivieren" durch "Lauf beenden"', () => {
        expect(matchControls(match({state: 'AWAITING_FINISH'}), true, true)).toEqual({
            showFinish: true,
            showActivationToggle: false,
            showMarkStarted: false,
        })
    })

    it('bietet bei einem beendeten Lauf nur noch das Aktivieren an', () => {
        expect(matchControls(match({state: 'FINISHED'}), true, true)).toEqual({
            showFinish: false,
            showActivationToggle: true,
            showMarkStarted: false,
        })
    })

    it('lässt einen abgesagten Lauf ohne jede Schaltfläche', () => {
        expect(matchControls(match({state: 'SKIPPED'}), true, true)).toEqual({
            showFinish: false,
            showActivationToggle: false,
            showMarkStarted: false,
        })
    })

    it('zeigt nichts, wozu die Rechte fehlen', () => {
        expect(matchControls(match({state: 'AWAITING_FINISH'}), false, true)).toEqual({
            showFinish: false,
            showActivationToggle: false,
            showMarkStarted: false,
        })
        expect(matchControls(match({state: 'RUNNING'}), false, false)).toEqual({
            showFinish: false,
            showActivationToggle: false,
            showMarkStarted: false,
        })
        // Ohne Steuerungsrecht auch am Start kein „Läuft" - der Knopf schreibt einen Zeitstempel.
        expect(matchControls(match({state: 'PREPARING'}), false, false)).toEqual({
            showFinish: false,
            showActivationToggle: false,
            showMarkStarted: false,
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

    it('setzt in der Kurzform das Kürzel an die Stelle des Wettkampfnamens', () => {
        expect(
            pendingSlotLabel(
                pendingSlot({
                    competitionName: 'Coastal Männer Einer',
                    competitionIdentifier: '12',
                    competitionShortName: 'CM 1x',
                    roundName: 'Achtelfinale',
                    matchName: 'AF1',
                }),
                'short',
            ),
        ).toBe('12 CM 1x · Achtelfinale · AF1')
    })

    it('lässt den Namen des Programmpunkts auch in der Kurzform stehen', () => {
        expect(pendingSlotLabel(pendingSlot({name: 'Mittagspause'}), 'short')).toBe('Mittagspause')
    })
})

describe('competitionLabel', () => {
    it('nimmt in der Kurzform Rennnummer und Kurznamen', () => {
        expect(
            competitionLabel(
                {
                    competitionName: 'Coastal Männer Doppelvierer mit Steuerfrau/mann',
                    competitionIdentifier: '17',
                    competitionShortName: 'CM 4x+',
                },
                'short',
            ),
        ).toBe('17 CM 4x+')
    })

    it('bleibt ohne Kürzel beim ausgeschriebenen Namen', () => {
        expect(
            competitionLabel(
                {
                    competitionName: 'Coastal Männer Einer',
                    competitionIdentifier: null,
                    competitionShortName: null,
                },
                'short',
            ),
        ).toBe('Coastal Männer Einer')
    })

    it('nimmt ohne Kurzform den ausgeschriebenen Namen', () => {
        expect(
            competitionLabel({
                competitionName: 'Coastal Männer Einer',
                competitionIdentifier: '12',
                competitionShortName: 'CM 1x',
            }),
        ).toBe('Coastal Männer Einer')
    })
})

describe('dashboardMatchStatus', () => {
    const match = (overrides: Partial<LiveDashboardMatchDto>): LiveDashboardMatchDto =>
        ({
            matchId: 'm-1',
            state: 'UPCOMING',
            competitionId: 'c-1',
            competitionName: 'Coastal Mixed 4x+',
            executionOrder: 1,
            teams: [],
            ...overrides,
        }) as LiveDashboardMatchDto

    it('zählt die gewerteten Boote nach derselben Regel wie das Backend', () => {
        const status = dashboardMatchStatus(
            match({
                teams: [
                    {place: 1} as never,
                    {failed: true} as never,
                    {deregistered: true} as never,
                    {} as never,
                ],
            }),
        )
        expect(status.teamsTotal).toBe(4)
        expect(status.teamsScored).toBe(3)
    })

    it('trägt das Freilos in den Dashboard-Status', () => {
        const status = dashboardMatchStatus(
            match({state: 'AWAITING_FINISH', bye: {cause: 'DEREGISTRATION', teamName: 'RV Hansa'}}),
        )
        expect(status.bye).toEqual({cause: 'DEREGISTRATION', teamName: 'RV Hansa'})
    })

    it('lässt einen gewöhnlichen Lauf ohne Freilos', () => {
        expect(dashboardMatchStatus(match({})).bye).toBeUndefined()
    })
})
