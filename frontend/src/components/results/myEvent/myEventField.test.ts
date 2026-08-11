import {describe, expect, it} from 'vitest'
import {LatestMatchResultInfo, MatchResultTeamInfo} from '@api/types.gen.ts'
import {boatLabel, displayPlace, fieldSections} from './myEventField.ts'

const team = (o: Partial<MatchResultTeamInfo>): MatchResultTeamInfo => ({
    teamId: crypto.randomUUID(),
    failed: false,
    deregistered: false,
    participants: [],
    ...o,
})

const match = (teams: MatchResultTeamInfo[]): LatestMatchResultInfo =>
    ({
        matchId: 'm1',
        competitionId: 'c1',
        competitionName: 'Vierer',
        updatedAt: '2026-08-14T10:00:00',
        teams,
    }) as LatestMatchResultInfo

describe('fieldSections', () => {
    it('sortiert nach Platz und markiert das eigene Boot über die Meldung', () => {
        const own = team({teamId: 'eigene-meldung', place: 2})
        const winner = team({place: 1})
        const sections = fieldSections(match([own, winner]), 'eigene-meldung')

        expect(sections).toHaveLength(1)
        expect(sections[0].entries.map(t => t.place)).toEqual([1, 2])
        expect(sections[0].entries.map(t => t.own)).toEqual([false, true])
    })

    it('markiert ohne eigene Meldung kein Boot', () => {
        // Ein Ergebnis ohne teamId (Altbestand) darf nicht zufällig ein fremdes Boot als
        // das eigene ausweisen.
        const sections = fieldSections(match([team({place: 1})]), null)
        expect(sections[0].entries.map(t => t.own)).toEqual([false])
    })

    it('stellt Ausgeschiedene und Abgemeldete ans Ende', () => {
        const failed = team({failed: true})
        const placed = team({place: 1})
        const sections = fieldSections(match([failed, placed]), null)
        expect(sections[0].entries.map(t => t.failed)).toEqual([false, true])
    })

    it('trennt Wertungskategorien in Abschnitte', () => {
        const a = team({place: 1, ratingCategory: {id: 'k1', name: 'Junioren', sortOrder: 1}})
        const b = team({place: 2})
        const sections = fieldSections(match([b, a]), null)
        expect(sections.map(s => s.category?.name ?? null)).toEqual(['Junioren', null])
    })
})

describe('displayPlace', () => {
    it('bevorzugt den Platz innerhalb der Wertungskategorie', () => {
        // Dieselbe Zahl, die auch die Ergebnisseite führt.
        expect(displayPlace(team({place: 5, categoryPlace: 2}))).toBe(2)
    })

    it('fällt auf den Laufplatz zurück und meldet fehlende Wertung als null', () => {
        expect(displayPlace(team({place: 5}))).toBe(5)
        expect(displayPlace(team({}))).toBeNull()
    })
})

describe('boatLabel', () => {
    it('nimmt die Vereinskette vor dem meldenden Verein vor dem Mannschaftsnamen', () => {
        expect(
            boatLabel(team({clubsShort: 'RC A/RC B', clubName: 'RC A', teamName: 'Boot 1'})),
        ).toBe('RC A/RC B')
        expect(boatLabel(team({clubName: 'RC A', teamName: 'Boot 1'}))).toBe('RC A')
        expect(boatLabel(team({teamName: 'Boot 1'}))).toBe('Boot 1')
    })
})
