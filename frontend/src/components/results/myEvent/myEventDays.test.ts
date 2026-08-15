import {describe, expect, it} from 'vitest'
import {dayKeyOf, groupByDay, showDayHeadings} from './myEventDays.ts'

describe('dayKeyOf', () => {
    it('liefert den Kalendertag eines Zeitstempels', () => {
        expect(dayKeyOf('2026-08-14T10:30:00')).toBe('2026-08-14')
    })

    it.each([[null], [undefined]])('liefert ohne Zeit (%s) null', value => {
        expect(dayKeyOf(value)).toBeNull()
    })
})

describe('groupByDay', () => {
    const entry = (startTime: string | null) => ({startTime})

    it('fasst aufeinanderfolgende Einträge desselben Tages zusammen', () => {
        const morgens = entry('2026-08-14T09:00:00')
        const mittags = entry('2026-08-14T12:00:00')
        const samstag = entry('2026-08-15T10:00:00')
        expect(groupByDay([morgens, mittags, samstag])).toEqual([
            {day: '2026-08-14', items: [morgens, mittags]},
            {day: '2026-08-15', items: [samstag]},
        ])
    })

    it('lässt die Reihenfolge des Servers unangetastet (Ergebnisse: neuestes zuerst)', () => {
        const samstag = entry('2026-08-15T10:00:00')
        const freitag = entry('2026-08-14T15:00:00')
        expect(groupByDay([samstag, freitag])).toEqual([
            {day: '2026-08-15', items: [samstag]},
            {day: '2026-08-14', items: [freitag]},
        ])
    })

    it('sammelt Einträge ohne Startzeit in Gruppen ohne Tag', () => {
        const terminiert = entry('2026-08-14T09:00:00')
        const offen = entry(null)
        expect(groupByDay([terminiert, offen])).toEqual([
            {day: '2026-08-14', items: [terminiert]},
            {day: null, items: [offen]},
        ])
    })

    it('fasst eine spätere Gruppe desselben Tages nicht über die Lücke hinweg zusammen', () => {
        // Überfällige Läufe wandern serverseitig ans Ende der kommenden — derselbe Tag kann
        // dadurch zweimal vorkommen. Die Gruppen bleiben getrennt und ehrlich.
        const heute = entry('2026-08-15T10:00:00')
        const gestern = entry('2026-08-14T09:00:00')
        expect(groupByDay([heute, gestern, entry('2026-08-15T14:00:00')])).toEqual([
            {day: '2026-08-15', items: [heute]},
            {day: '2026-08-14', items: [gestern]},
            {day: '2026-08-15', items: [{startTime: '2026-08-15T14:00:00'}]},
        ])
    })

    it('liefert für eine leere Liste keine Gruppen', () => {
        expect(groupByDay([])).toEqual([])
    })
})

describe('showDayHeadings', () => {
    const base = {running: [], upcoming: [], results: [], serverTime: '2026-08-15T10:00:00'}
    const entry = (startTime: string | null) => ({startTime})

    it('bleibt bei einer eintägigen Veranstaltung schlank', () => {
        expect(
            showDayHeadings({
                ...base,
                upcoming: [entry('2026-08-15T11:00:00')],
                results: [entry('2026-08-15T09:00:00')],
            }),
        ).toBe(false)
    })

    it('zeigt Tage, sobald die eigenen Einträge zwei Kalendertage berühren', () => {
        // Der Fall vom Regattatag: Ergebnisse von gestern, Läufe von heute.
        expect(
            showDayHeadings({
                ...base,
                upcoming: [entry('2026-08-15T11:00:00')],
                results: [entry('2026-08-14T15:00:00')],
            }),
        ).toBe(true)
    })

    it('zeigt Tage, wenn alles an einem anderen Tag als heute liegt', () => {
        // Blick am Vorabend: „10:30" allein läse sich wie gleich, nicht wie morgen.
        expect(showDayHeadings({...base, upcoming: [entry('2026-08-16T10:30:00')]})).toBe(true)
    })

    it('zählt Einträge ohne Startzeit nicht als eigenen Tag', () => {
        expect(
            showDayHeadings({
                ...base,
                upcoming: [entry('2026-08-15T11:00:00'), entry(null)],
            }),
        ).toBe(false)
    })

    it('zeigt ganz ohne terminierte Einträge keine Tage', () => {
        expect(showDayHeadings({...base, upcoming: [entry(null)]})).toBe(false)
    })
})
