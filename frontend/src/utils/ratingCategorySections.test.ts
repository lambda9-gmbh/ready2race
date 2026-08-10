import {describe, expect, it} from 'vitest'
import {groupByRatingCategory, hasRatingCategories} from './ratingCategorySections'
import {RatingCategoryRefDto} from '@api/types.gen.ts'

const meisterschaft: RatingCategoryRefDto = {id: 'a', name: 'Meisterschaften', sortOrder: 0}
const breitensport: RatingCategoryRefDto = {id: 'b', name: 'Breitensport', sortOrder: 1}
const masters: RatingCategoryRefDto = {id: 'c', name: 'Masters', sortOrder: 2}

const boat = (name: string, ratingCategory: RatingCategoryRefDto | null) => ({
    name,
    ratingCategory,
})

type Boat = {name: string; ratingCategory?: RatingCategoryRefDto | null}

const group = (boats: Boat[]) => groupByRatingCategory(boats, boat => boat.ratingCategory)

describe('groupByRatingCategory', () => {
    it('orders sections by the configured sort order, not alphabetically', () => {
        const sections = group([
            boat('a', masters),
            boat('b', meisterschaft),
            boat('c', breitensport),
        ])

        expect(sections.map(s => s.category?.name)).toEqual([
            'Meisterschaften',
            'Breitensport',
            'Masters',
        ])
    })

    it('falls back to the name when the sort order is equal', () => {
        const sections = group([
            boat('a', {id: 'x', name: 'Zander', sortOrder: 7}),
            boat('b', {id: 'y', name: 'Aal', sortOrder: 7}),
        ])

        expect(sections.map(s => s.category?.name)).toEqual(['Aal', 'Zander'])
    })

    it('puts boats without a category in their own section at the end', () => {
        const sections = group([boat('ohne', null), boat('mit', masters)])

        expect(sections.map(s => s.category?.name ?? null)).toEqual(['Masters', null])
        expect(sections[1].entries.map(e => e.name)).toEqual(['ohne'])
    })

    it('keeps the input order within a section', () => {
        const sections = group([
            boat('erster', breitensport),
            boat('zweiter', breitensport),
            boat('dritter', breitensport),
        ])

        expect(sections[0].entries.map(e => e.name)).toEqual(['erster', 'zweiter', 'dritter'])
    })

    it('produces no section for a category without boats', () => {
        const sections = group([boat('a', meisterschaft)])

        expect(sections).toHaveLength(1)
        expect(sections[0].category?.name).toBe('Meisterschaften')
    })

    it('handles an empty field', () => {
        expect(group([])).toEqual([])
    })

    it('treats a missing ratingCategory like an explicit null', () => {
        const sections = group([{name: 'a'}, boat('b', null)])

        expect(sections).toHaveLength(1)
        expect(sections[0].category).toBeNull()
        expect(sections[0].entries).toHaveLength(2)
    })
})

describe('hasRatingCategories', () => {
    it('is false for a field without any category', () => {
        expect(hasRatingCategories(group([boat('a', null)]))).toBe(false)
    })

    it('is true as soon as one boat carries a category', () => {
        expect(hasRatingCategories(group([boat('a', null), boat('b', masters)]))).toBe(true)
    })
})
