import {AwardCeremonyChoiceDto, AwardCeremonyKeyRequest} from '@api/types.gen.ts'

/**
 * Der Schlüssel, unter dem eine Ehrung in der Auswahl geführt wird.
 *
 * Die Id der Wertung, nicht ihr Name: zwei gleichnamige Kategorien wären sonst dieselbe Ehrung, und
 * der Haken an der einen setzte den an der anderen mit.
 *
 * Der Server lässt null-Felder weg (`JsonInclude.NON_ABSENT`), also kommt die Ehrung ohne Wertung mit
 * `ratingCategoryId === undefined` an, während dieselbe Ehrung in einer selbst gebauten Auswahl
 * leicht als `null` entsteht. Beide müssen denselben Schlüssel ergeben, sonst findet der Dialog
 * seine eigene Vorauswahl nicht wieder und der Haken erscheint nie.
 */
export const ceremonyKey = (choice: {
    competitionId: string
    ratingCategoryId?: string | null
}): string => JSON.stringify([choice.competitionId, choice.ratingCategoryId ?? null])

/** Die Ehrung als Schlüssel für den Server - `null` heißt dort "der Wettkampf als Ganzes". */
export const ceremonyRequestKey = (choice: AwardCeremonyChoiceDto): AwardCeremonyKeyRequest => ({
    competitionId: choice.competitionId,
    ratingCategoryId: choice.ratingCategoryId ?? null,
})

export type CeremonyGroup = {
    competitionId: string
    competitionIdentifier: string
    competitionShortName?: string | null
    competitionName: string
    ceremonies: Array<AwardCeremonyChoiceDto>
}

/**
 * Fasst die Ehrungen je Wettkampf zusammen und behält dabei die Reihenfolge des Servers bei - die
 * ist bereits die Reihenfolge, in der geehrt wird, und darf im Dialog nicht neu sortiert werden.
 */
export const groupByCompetition = (
    choices: Array<AwardCeremonyChoiceDto>,
): Array<CeremonyGroup> => {
    const groups: Array<CeremonyGroup> = []
    const byCompetition = new Map<string, CeremonyGroup>()

    for (const choice of choices) {
        let group = byCompetition.get(choice.competitionId)
        if (group === undefined) {
            group = {
                competitionId: choice.competitionId,
                competitionIdentifier: choice.competitionIdentifier,
                competitionShortName: choice.competitionShortName,
                competitionName: choice.competitionName,
                ceremonies: [],
            }
            byCompetition.set(choice.competitionId, group)
            groups.push(group)
        }
        group.ceremonies.push(choice)
    }

    return groups
}

/**
 * Ein Wettkampf, der nur als Ganzes geehrt wird, braucht keine eigene Zeile unter der Kopfzeile -
 * dort stünde nur "Ohne Wertungskategorie" unter dem Wettkampfnamen.
 */
export const isSingleUncategorized = (group: CeremonyGroup): boolean =>
    group.ceremonies.length === 1 && (group.ceremonies[0].ratingCategoryId ?? null) === null
