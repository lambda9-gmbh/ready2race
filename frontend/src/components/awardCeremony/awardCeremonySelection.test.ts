import {describe, expect, it} from 'vitest'
import {
    ceremonyKey,
    ceremonyRequestKey,
    groupByCompetition,
    isSingleUncategorized,
} from './awardCeremonySelection.ts'
import {AwardCeremonyChoiceDto} from '@api/types.gen.ts'

const choice = (partial: Partial<AwardCeremonyChoiceDto>): AwardCeremonyChoiceDto => ({
    competitionId: 'c1',
    competitionIdentifier: '17-NC',
    competitionName: 'Männer Vierer',
    awardedTeams: 3,
    ...partial,
})

describe('ceremonyKey', () => {
    it('trifft die Ehrung ohne Wertung, ob sie nun als undefined oder als null ankommt', () => {
        // Der Server lässt null-Felder weg (JsonInclude.NON_ABSENT), eine selbst gebaute Auswahl
        // setzt dagegen leicht null. Unterschieden die beiden sich hier, fände der Dialog seine
        // eigene Vorauswahl nicht wieder und der Haken erschiene nie.
        expect(ceremonyKey(choice({}))).toBe(ceremonyKey(choice({ratingCategoryName: null})))
        expect(ceremonyKey(choice({ratingCategoryName: undefined}))).toBe(
            ceremonyKey(choice({ratingCategoryName: null})),
        )
    })

    it('hält die Ehrung ohne Wertung von einer benannten auseinander', () => {
        expect(ceremonyKey(choice({}))).not.toBe(
            ceremonyKey(choice({ratingCategoryName: 'Masters'})),
        )
    })

    it('hält gleichnamige Wertungen verschiedener Wettkämpfe auseinander', () => {
        expect(ceremonyKey(choice({competitionId: 'c1', ratingCategoryName: 'Masters'}))).not.toBe(
            ceremonyKey(choice({competitionId: 'c2', ratingCategoryName: 'Masters'})),
        )
    })
})

describe('ceremonyRequestKey', () => {
    it('schickt die fehlende Wertung als null, nicht als undefined', () => {
        expect(ceremonyRequestKey(choice({}))).toEqual({
            competitionId: 'c1',
            ratingCategoryName: null,
        })
    })
})

describe('groupByCompetition', () => {
    it('behält die Reihenfolge des Servers bei - sie ist die Reihenfolge der Ehrungen', () => {
        const groups = groupByCompetition([
            choice({competitionId: 'c2', competitionIdentifier: '2'}),
            choice({competitionId: 'c1', competitionIdentifier: '1', ratingCategoryName: 'A'}),
            choice({competitionId: 'c2', competitionIdentifier: '2', ratingCategoryName: 'B'}),
        ])

        expect(groups.map(it => it.competitionId)).toEqual(['c2', 'c1'])
        expect(groups[0].ceremonies).toHaveLength(2)
    })
})

describe('isSingleUncategorized', () => {
    it('erkennt den Wettkampf, der nur als Ganzes geehrt wird', () => {
        expect(isSingleUncategorized(groupByCompetition([choice({})])[0])).toBe(true)
    })

    it('erkennt ihn auch, wenn die Wertung als null statt als undefined ankommt', () => {
        expect(
            isSingleUncategorized(groupByCompetition([choice({ratingCategoryName: null})])[0]),
        ).toBe(true)
    })

    it('lässt eine benannte Wertung ihre eigene Zeile behalten', () => {
        expect(
            isSingleUncategorized(groupByCompetition([choice({ratingCategoryName: 'Masters'})])[0]),
        ).toBe(false)
    })
})
