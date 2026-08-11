import {describe, expect, it} from 'vitest'
import {CompetitionMatchDto, CompetitionRoundDto, MatchByeDto} from '@api/types.gen.ts'
import {byeMatches, raceableMatches} from './byeMatches.ts'

const match = (
    id: string,
    overrides: {bye?: MatchByeDto; teams?: number; weighting?: number; order?: number} = {},
): CompetitionMatchDto =>
    ({
        id,
        teams: Array.from({length: overrides.teams ?? 2}, () => ({}) as never),
        weighting: overrides.weighting ?? 1,
        executionOrder: overrides.order ?? 1,
        skipped: false,
        status: {
            state: 'UPCOMING',
            teamsTotal: overrides.teams ?? 2,
            teamsScored: 0,
            bye: overrides.bye,
        },
    }) as CompetitionMatchDto

const round = (matches: CompetitionMatchDto[]): CompetitionRoundDto =>
    ({setupRoundId: 'r-1', name: 'Viertelfinale', matches, required: false, substitutions: []}) as CompetitionRoundDto

describe('byeMatches', () => {
    it('nimmt genau die Läufe mit Freilos, nach Gewichtung sortiert', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const result = byeMatches(
            round([
                match('a', {bye, teams: 1, weighting: 3}),
                match('b'),
                match('c', {bye, teams: 1, weighting: 1}),
            ]),
        )
        expect(result.map(m => m.id)).toEqual(['c', 'a'])
    })

    it('bleibt leer, wenn es keins gibt', () => {
        expect(byeMatches(round([match('a'), match('b')]))).toEqual([])
    })
})

describe('raceableMatches', () => {
    it('lässt Freilose weg und sortiert nach Startreihenfolge', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const result = raceableMatches(
            round([
                match('a', {order: 2}),
                match('b', {bye, teams: 1}),
                match('c', {order: 1}),
            ]),
        )
        expect(result.map(m => m.id)).toEqual(['c', 'a'])
    })

    it('lässt Läufe ohne Mannschaften weg', () => {
        expect(raceableMatches(round([match('a', {teams: 0})]))).toEqual([])
    })

    /** Panel und Kartenliste teilen die Runde vollständig und überschneidungsfrei auf. */
    it('teilt die Runde vollständig auf', () => {
        const bye: MatchByeDto = {cause: 'NO_OPPONENT'}
        const r = round([match('a'), match('b', {bye, teams: 1}), match('c')])
        expect([...byeMatches(r), ...raceableMatches(r)].map(m => m.id).sort()).toEqual([
            'a',
            'b',
            'c',
        ])
    })
})
