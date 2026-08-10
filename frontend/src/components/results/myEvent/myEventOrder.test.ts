import {describe, expect, it} from 'vitest'
import {MyEventDto, MyEventRequirementDto} from '@api/types.gen.ts'
import {blockOrder, nothingToShow, openRequirements} from './myEventOrder.ts'

const requirement = (o: Partial<MyEventRequirementDto>): MyEventRequirementDto => ({
    id: crypto.randomUUID(),
    name: 'Bedingung',
    optional: false,
    fulfilled: false,
    ...o,
})

const dto = (o: Partial<MyEventDto>): MyEventDto => ({
    displayName: 'Ilka Heller',
    eventName: 'Veranstaltung',
    serverTime: '2026-08-14T10:00:00',
    refreshIntervalSeconds: 15,
    running: [],
    upcoming: [],
    results: [],
    unscheduled: [],
    requirements: [],
    ...o,
})

describe('openRequirements', () => {
    it('meldet nur nicht erfüllte Pflichtbedingungen', () => {
        const open = requirement({fulfilled: false, optional: false})
        const result = openRequirements([
            open,
            requirement({fulfilled: true, optional: false}),
            requirement({fulfilled: false, optional: true}),
        ])
        expect(result).toEqual([open])
    })
})

const match = (id: string) => ({matchId: id}) as never

describe('blockOrder', () => {
    it('zeigt kein Band, wenn alles erledigt ist', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: true})]}))
        expect(order).not.toContain('requirementBanner')
        // Die Liste bleibt als ruhige Bestätigung stehen — sie steht ganz unten und nicht
        // etwa an der Stelle, an der sonst das Band steht.
        expect(order[order.length - 1]).toBe('requirements')
    })

    it('zieht ein offenes Band ganz nach oben', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: false})]}))
        expect(order[0]).toBe('requirementBanner')
    })

    it('stellt kommende Läufe vor die Ergebnisse', () => {
        const order = blockOrder(dto({upcoming: [match('m1')], results: [match('m0')]}))
        expect(order.indexOf('next')).toBeLessThan(order.indexOf('results'))
    })

    it('stellt einen laufenden Lauf nach vorn, auch ohne kommende Läufe', () => {
        const order = blockOrder(dto({running: [match('m1')], results: [match('m0')]}))
        expect(order.indexOf('next')).toBeLessThan(order.indexOf('results'))
    })

    it('stellt die Ergebnisse nach vorn, wenn nichts mehr ansteht', () => {
        const order = blockOrder(dto({results: [match('m0')]}))
        expect(order).not.toContain('next')
        expect(order[0]).toBe('results')
    })

    it('lässt die Laufliste weg, wenn nichts mehr ansteht', () => {
        // Sonst stand unter den Ergebnissen des Tages "Für dich ist noch kein Lauf
        // eingetragen." — das Gegenteil dessen, was direkt darüber zu lesen war.
        const order = blockOrder(dto({results: [match('m0')]}))
        expect(order).not.toContain('matches')
    })

    it('lässt die Laufliste bei genau einem anstehenden Lauf weg', () => {
        // Der eine Lauf steht bereits als große Karte oben; die Liste wäre nur seine
        // Wiederholung.
        const order = blockOrder(dto({upcoming: [match('m1')]}))
        expect(order).toContain('next')
        expect(order).not.toContain('matches')
    })

    it('zeigt die Laufliste ab zwei anstehenden Läufen', () => {
        const order = blockOrder(dto({running: [match('m1')], upcoming: [match('m2')]}))
        expect(order.indexOf('next')).toBeLessThan(order.indexOf('matches'))
    })
})

describe('nothingToShow', () => {
    it('meldet leer, wenn weder Läufe noch Ergebnisse noch Meldungen vorliegen', () => {
        expect(nothingToShow(dto({requirements: [requirement({fulfilled: false})]}))).toBe(true)
    })

    it.each([
        ['running', dto({running: [match('m1')]})],
        ['upcoming', dto({upcoming: [match('m1')]})],
        ['results', dto({results: [match('m0')]})],
        ['unscheduled', dto({unscheduled: [{competitionId: 'c1'} as never]})],
    ])('meldet nicht leer, sobald %s gefüllt ist', (_name, data) => {
        expect(nothingToShow(data)).toBe(false)
    })
})
