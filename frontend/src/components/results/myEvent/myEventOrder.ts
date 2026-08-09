import {MyEventDto, MyEventRequirementDto} from '@api/types.gen.ts'

export type MyEventBlock =
    | 'requirementBanner'
    | 'next'
    | 'matches'
    | 'results'
    | 'unscheduled'
    | 'requirements'

/**
 * Offen sind nur nicht erfüllte Pflichtbedingungen. Eine offene freiwillige Bedingung soll
 * niemanden am Renntag beunruhigen — sie steht in der Liste weiter unten.
 */
export const openRequirements = (requirements: MyEventRequirementDto[]): MyEventRequirementDto[] =>
    requirements.filter(r => !r.fulfilled && !r.optional)

/**
 * Die Seite sortiert sich nach der Tageszeit um: solange ein eigener Lauf aussteht, steht er
 * oben; ist alles gelaufen, rückt das Ergebnis nach vorn. Wer zwischen zwei Läufen aufs
 * Telefon schaut, will „wann muss ich wo sein" sehen, danach „wie lief es".
 */
export const blockOrder = (data: MyEventDto): MyEventBlock[] => {
    const blocks: MyEventBlock[] = []

    if (openRequirements(data.requirements).length > 0) {
        blocks.push('requirementBanner')
    }

    const somethingAhead = data.running.length > 0 || data.upcoming.length > 0
    if (somethingAhead) {
        blocks.push('next', 'matches', 'results')
    } else {
        blocks.push('results', 'matches')
    }

    blocks.push('unscheduled', 'requirements')
    return blocks
}
