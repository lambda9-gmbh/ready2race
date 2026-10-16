import {describe, expect, it} from 'vitest'
import {getNewPlaces} from './common'

describe('setup placing table during unfinished count edits', () => {
    const customPlaces = [{roundOutcome: 3, place: 7}]

    it.each([NaN, Infinity, -1, 1.5, 2 ** 32])(
        'preserves existing places when the current count is %s',
        count => {
            expect(getNewPlaces([], count, 2, customPlaces)).toBe(customPlaces)
        },
    )

    it.each([NaN, Infinity, -1, 1.5])(
        'does not write invalid places when the following count is %s',
        count => {
            expect(getNewPlaces([], 4, count, customPlaces)).toBe(customPlaces)
        },
    )

    it('resumes the existing calculation when counts are valid, keeping manual overrides', () => {
        expect(getNewPlaces([1, 2], 4, 2, customPlaces)).toEqual([
            {roundOutcome: 3, place: 7},
            {roundOutcome: 4, place: 3},
        ])
        expect(getNewPlaces([], 0, 0, customPlaces)).toEqual([])
    })
})
