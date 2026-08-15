import {describe, expect, test} from 'vitest'
import {
    competitionLabel,
    coveringFulfillment,
    covers,
    preselectCompetition,
    requirementStatus,
} from './requirementScope.ts'

const wholeEvent = {perEventDay: false, perCompetition: false}
const perDay = {perEventDay: true, perCompetition: false}
const perCompetition = {perEventDay: false, perCompetition: true}
const perBoth = {perEventDay: true, perCompetition: true}

const heute = 'tag-1'
const gestern = 'tag-0'
const wettkampfA = 'wk-a'
const wettkampfB = 'wk-b'

describe('covers', () => {
    test('ohne Schalter zählt jede Bestätigung, auch eine mit Dimensionen', () => {
        // Der Bestand aus der Migration trägt einen Tag, obwohl die Bedingung
        // veranstaltungsweit gilt - er darf deshalb nicht durchfallen.
        expect(
            covers(wholeEvent, {id: 'r', eventDayId: gestern, competitionId: wettkampfB}, {
                todayEventDayId: heute,
                competitionId: wettkampfA,
            }),
        ).toBe(true)
    })

    test('perEventDay: gestern gewogen deckt heute nicht ab', () => {
        expect(
            covers(perDay, {id: 'r', eventDayId: gestern}, {todayEventDayId: heute}),
        ).toBe(false)
        expect(covers(perDay, {id: 'r', eventDayId: heute}, {todayEventDayId: heute})).toBe(true)
    })

    test('perEventDay: eine Zeile ohne Tag deckt keinen Tag ab', () => {
        expect(covers(perDay, {id: 'r'}, {todayEventDayId: heute})).toBe(false)
    })

    test('perCompetition: die Bestätigung des anderen Wettkampfs zählt nicht', () => {
        expect(
            covers(perCompetition, {id: 'r', competitionId: wettkampfB}, {
                competitionId: wettkampfA,
            }),
        ).toBe(false)
        expect(
            covers(perCompetition, {id: 'r', competitionId: wettkampfA}, {
                competitionId: wettkampfA,
            }),
        ).toBe(true)
    })

    test('perCompetition: ohne gewählten Wettkampf deckt nur die bezugslose Zeile ab', () => {
        expect(covers(perCompetition, {id: 'r'}, {})).toBe(true)
        expect(covers(perCompetition, {id: 'r', competitionId: wettkampfA}, {})).toBe(false)
    })

    test('beide Schalter: beide Dimensionen müssen stimmen', () => {
        const context = {todayEventDayId: heute, competitionId: wettkampfA}
        expect(
            covers(perBoth, {id: 'r', eventDayId: heute, competitionId: wettkampfA}, context),
        ).toBe(true)
        expect(
            covers(perBoth, {id: 'r', eventDayId: heute, competitionId: wettkampfB}, context),
        ).toBe(false)
        expect(
            covers(perBoth, {id: 'r', eventDayId: gestern, competitionId: wettkampfA}, context),
        ).toBe(false)
    })

    test('null und undefined sind dieselbe Aussage', () => {
        expect(covers(perDay, {id: 'r', eventDayId: null}, {todayEventDayId: undefined})).toBe(true)
    })
})

describe('coveringFulfillment', () => {
    const checked = [
        {id: 'waage', competitionId: wettkampfA, note: 'A'},
        {id: 'waage', competitionId: wettkampfB, note: 'B'},
        {id: 'pass', note: 'egal'},
    ]

    test('findet die Zeile des gewählten Wettkampfs samt ihrer Notiz', () => {
        expect(
            coveringFulfillment('waage', perCompetition, checked, {competitionId: wettkampfB}),
        ).toEqual({id: 'waage', competitionId: wettkampfB, note: 'B'})
    })

    test('andere Bedingung, gleicher Wettkampf: kein Treffer', () => {
        expect(
            coveringFulfillment('gewicht', perCompetition, checked, {competitionId: wettkampfA}),
        ).toBeUndefined()
    })

    test('ohne Schalter genügt irgendeine Zeile derselben Bedingung', () => {
        expect(coveringFulfillment('pass', wholeEvent, checked, {})?.note).toBe('egal')
    })
})

describe('preselectCompetition', () => {
    const zwei = [
        {id: wettkampfA, name: 'Vierer', identifier: '12'},
        {id: wettkampfB, name: 'Zweier', identifier: '13'},
    ]

    test('der gemerkte Wettkampf bleibt stehen, wenn die Person dort gemeldet ist', () => {
        expect(preselectCompetition(wettkampfB, zwei)).toBe(wettkampfB)
    })

    test('ein gemerkter Wettkampf ohne Meldung wird nicht übernommen', () => {
        expect(preselectCompetition('wk-fremd', zwei)).toBeNull()
    })

    test('bei genau einem Wettkampf ist die Wahl eindeutig', () => {
        expect(preselectCompetition(null, [zwei[0]])).toBe(wettkampfA)
        expect(preselectCompetition('wk-fremd', [zwei[0]])).toBe(wettkampfA)
    })

    test('ohne Meldung gibt es nichts zu wählen', () => {
        expect(preselectCompetition(wettkampfA, [])).toBeNull()
    })
})

describe('competitionLabel', () => {
    test('Kennung und Kürzel', () => {
        expect(
            competitionLabel({id: 'x', identifier: '12', shortName: 'JM4x', name: 'Junioren'}),
        ).toBe('12 JM4x')
    })

    test('ohne Kürzel der volle Name, ohne Kennung nur der Name', () => {
        expect(competitionLabel({id: 'x', identifier: '12', name: 'Junioren'})).toBe('12 Junioren')
        expect(competitionLabel({id: 'x', name: 'Junioren'})).toBe('Junioren')
    })
})

describe('requirementStatus', () => {
    const wettkaempfe = [
        {id: wettkampfA, name: 'Vierer', identifier: '8', shortName: 'CMix4x+'},
        {id: wettkampfB, name: 'Zweier', identifier: '12', shortName: 'CM2x'},
    ]

    test('je Wettkampf eine Zeile - erledigt und offen nebeneinander', () => {
        // Der Fall an der Waage: Die Person startet zweimal, gewogen ist erst einer der beiden.
        const checked = [{id: 'waage', eventDayId: heute, competitionId: wettkampfA}]
        const status = requirementStatus(
            'waage',
            {perEventDay: true, perCompetition: true},
            checked,
            wettkaempfe,
            heute,
        )
        expect(status.map(s => [s.competitionLabel, s.fulfilled])).toEqual([
            ['8 CMix4x+', true],
            ['12 CM2x', false],
        ])
    })

    test('gestern gewogen zählt heute nicht', () => {
        const checked = [{id: 'waage', eventDayId: gestern, competitionId: wettkampfA}]
        const status = requirementStatus(
            'waage',
            {perEventDay: true, perCompetition: true},
            checked,
            wettkaempfe,
            heute,
        )
        expect(status.every(s => !s.fulfilled)).toBe(true)
    })

    test('ohne Wettkampfbezug genau eine Zeile, ohne Wettkampfnamen', () => {
        const status = requirementStatus(
            'pass',
            {perEventDay: false, perCompetition: false},
            [{id: 'pass', note: 'gesehen'}],
            wettkaempfe,
            heute,
        )
        expect(status).toEqual([
            {competitionId: null, competitionLabel: null, fulfilled: true, note: 'gesehen'},
        ])
    })

    test('nur tagesbezogen: eine Zeile, die auf heute schaut', () => {
        const scope = {perEventDay: true, perCompetition: false}
        expect(
            requirementStatus('waage', scope, [{id: 'waage', eventDayId: heute}], wettkaempfe, heute)[0]
                .fulfilled,
        ).toBe(true)
        expect(
            requirementStatus('waage', scope, [{id: 'waage', eventDayId: gestern}], wettkaempfe, heute)[0]
                .fulfilled,
        ).toBe(false)
    })

    test('ohne Meldung gibt es bei einer wettkampfbezogenen Bedingung nichts zu zeigen', () => {
        expect(
            requirementStatus('waage', {perEventDay: false, perCompetition: true}, [], [], heute),
        ).toEqual([])
    })
})
