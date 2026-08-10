import {describe, expect, it} from 'vitest'
import {ClubNameRuleDto, ClubNameRuleKind} from '@api/types.gen.ts'
import {reorderedRuleIds, rulesOfKind, switchRule} from './clubNameRules.ts'

const rule = (id: string, kind: ClubNameRuleKind, term?: string): ClubNameRuleDto => ({
    id,
    kind,
    term,
    replacement: kind === 'ABBREVIATION' ? 'X' : undefined,
    sortOrder: 0,
})

// Eine Kette, wie sie nach Migration und Ruder-Seed dasteht: Streichliste, Schalter, Wortpaare.
const chain: ClubNameRuleDto[] = [
    rule('ev', 'REMOVE_TERM', 'e.V.'),
    rule('bracketed', 'REMOVE_BRACKETED'),
    rule('years', 'REMOVE_YEARS'),
    rule('verein', 'ABBREVIATION', 'Verein'),
    rule('ruderverein', 'ABBREVIATION', 'Ruder-Verein'),
]

describe('switchRule', () => {
    it('findet den Schalter, wenn er an ist', () => {
        expect(switchRule(chain, 'REMOVE_YEARS')?.id).toBe('years')
    })

    it('meldet einen ausgeschalteten Schalter als nicht vorhanden', () => {
        expect(switchRule(rulesOfKind(chain, 'ABBREVIATION'), 'REMOVE_YEARS')).toBeUndefined()
    })
})

describe('reorderedRuleIds', () => {
    it('tauscht mit dem Nachbarn derselben Art, auch über fremde Zeilen hinweg', () => {
        // "Ruder-Verein" muss vor "Verein" greifen, sonst bleibt ein "Ruder-V" stehen.
        expect(reorderedRuleIds(chain, 'ruderverein', 'up')).toEqual([
            'ev',
            'bracketed',
            'years',
            'ruderverein',
            'verein',
        ])
    })

    it('rückt auch nach unten', () => {
        expect(reorderedRuleIds(chain, 'verein', 'down')).toEqual([
            'ev',
            'bracketed',
            'years',
            'ruderverein',
            'verein',
        ])
    })

    it('springt über eine Zeile anderer Art hinweg', () => {
        const mixed: ClubNameRuleDto[] = [
            rule('a', 'ABBREVIATION', 'A'),
            rule('term', 'REMOVE_TERM', 'e.V.'),
            rule('b', 'ABBREVIATION', 'B'),
        ]

        expect(reorderedRuleIds(mixed, 'b', 'up')).toEqual(['b', 'term', 'a'])
    })

    it('lässt die erste Regel ihrer Art in Ruhe', () => {
        expect(reorderedRuleIds(chain, 'verein', 'up')).toBeNull()
        expect(reorderedRuleIds(chain, 'ruderverein', 'down')).toBeNull()
        expect(reorderedRuleIds(chain, 'ev', 'up')).toBeNull()
    })

    it('meldet eine unbekannte Regel als nicht verschiebbar', () => {
        expect(reorderedRuleIds(chain, 'gibtesnicht', 'up')).toBeNull()
    })
})
