import {ClubNameRuleDto, ClubNameRuleKind} from '@api/types.gen.ts'

/**
 * Die beiden strukturellen Arten sind Schalter, keine Listeneinträge - sie lassen sich nicht als
 * Wort aufschreiben. Vorhanden heißt an.
 */
export const switchRule = (
    rules: ClubNameRuleDto[],
    kind: ClubNameRuleKind,
): ClubNameRuleDto | undefined => rules.find(rule => rule.kind === kind)

export const rulesOfKind = (rules: ClubNameRuleDto[], kind: ClubNameRuleKind): ClubNameRuleDto[] =>
    rules.filter(rule => rule.kind === kind)

export type MoveDirection = 'up' | 'down'

/**
 * Die neue Reihenfolge aller Regeln, nachdem [ruleId] einen Platz nach oben oder unten gerückt ist.
 * `null` heißt: die Regel steht schon am Rand ihrer Liste.
 *
 * Getauscht wird mit dem Nachbarn **derselben Art**, aber an dessen Stelle in der Gesamtreihenfolge:
 * die Regeln greifen in einer Kette, in der Wortpaare und Streichliste ineinanderstehen. Die
 * Oberfläche zeigt zwei Listen, wirksam ist eine.
 *
 * Warum es die Pfeiltasten überhaupt gibt: stünde `Verein` vor `Ruder-Verein`, bliebe aus
 * "Ruder-Verein" ein "Ruder-V" stehen.
 */
export const reorderedRuleIds = (
    rules: ClubNameRuleDto[],
    ruleId: string,
    direction: MoveDirection,
): string[] | null => {
    const index = rules.findIndex(rule => rule.id === ruleId)
    if (index < 0) {
        return null
    }

    const kind = rules[index].kind
    const sameKind = (offset: number) => rules[offset]?.kind === kind

    let neighbour = direction === 'up' ? index - 1 : index + 1
    while (neighbour >= 0 && neighbour < rules.length && !sameKind(neighbour)) {
        neighbour += direction === 'up' ? -1 : 1
    }

    if (neighbour < 0 || neighbour >= rules.length) {
        return null
    }

    const ids = rules.map(rule => rule.id)
    ids[index] = rules[neighbour].id
    ids[neighbour] = rules[index].id
    return ids
}
