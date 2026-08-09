import {describe, expect, it} from 'vitest'
import {MyEventDto, MyEventRequirementDto} from '@api/types.gen.ts'
import {blockOrder, openRequirements} from './myEventOrder.ts'

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

describe('blockOrder', () => {
    it('zeigt kein Band, wenn alles erledigt ist', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: true})]}))
        expect(order).not.toContain('requirementBanner')
        // Die Liste bleibt als ruhige Bestaetigung stehen.
        expect(order).toContain('requirements')
    })

    it('zieht ein offenes Band ganz nach oben', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: false})]}))
        expect(order[0]).toBe('requirementBanner')
    })

    it('stellt kommende Läufe vor die Ergebnisse', () => {
        const order = blockOrder(
            dto({upcoming: [{matchId: 'm1'} as never], results: [{matchId: 'm0'} as never]}),
        )
        expect(order.indexOf('next')).toBeLessThan(order.indexOf('results'))
    })

    it('stellt die Ergebnisse nach vorn, wenn nichts mehr ansteht', () => {
        const order = blockOrder(dto({results: [{matchId: 'm0'} as never]}))
        expect(order.indexOf('results')).toBeLessThan(order.indexOf('matches'))
        expect(order).not.toContain('next')
    })
})
