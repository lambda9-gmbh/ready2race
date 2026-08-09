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
 * Zur Anzeige gehört alles, was noch aussteht oder schon gelaufen ist. Ist davon nichts da,
 * bleibt die ganze Ansicht leer — dann und nur dann trägt eine Leermeldung die Seite.
 */
export const nothingToShow = (data: MyEventDto): boolean =>
    data.running.length === 0 &&
    data.upcoming.length === 0 &&
    data.results.length === 0 &&
    data.unscheduled.length === 0

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

    const scheduled = data.running.length + data.upcoming.length
    if (scheduled > 0) {
        blocks.push('next')
        // „Meine Läufe" wiederholt den Lauf, der direkt darüber schon als große Karte steht.
        // Bei genau einem anstehenden Lauf bestünde der Block nur aus dieser Wiederholung —
        // der Tagesplan lohnt sich erst ab zwei Läufen.
        if (scheduled > 1) {
            blocks.push('matches')
        }
        blocks.push('results')
    } else {
        // Ohne anstehenden Lauf gibt es auch keine Liste anstehender Läufe. Der Block fiel
        // früher trotzdem an und behauptete unter den Ergebnissen des Tages, es sei kein
        // Lauf eingetragen.
        blocks.push('results')
    }

    blocks.push('unscheduled', 'requirements')
    return blocks
}
