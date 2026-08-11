import {RoundProgressionConfigDto, RoundProgressionConfigRequest} from '@api/types.gen.ts'

/** Die drei Zustände als Auswahlwert — `null` lässt sich in einem Radio nicht abbilden. */
export type RoundProgressionChoice = 'INHERIT' | 'ENABLED' | 'DISABLED'

export const choiceFromDto = (dto: RoundProgressionConfigDto): RoundProgressionChoice =>
    dto.autoCreateFollowingRounds === null || dto.autoCreateFollowingRounds === undefined
        ? 'INHERIT'
        : dto.autoCreateFollowingRounds
          ? 'ENABLED'
          : 'DISABLED'

export const requestFromChoice = (choice: RoundProgressionChoice): RoundProgressionConfigRequest => ({
    autoCreateFollowingRounds:
        choice === 'INHERIT' ? null : choice === 'ENABLED',
})

/**
 * Was aus der Wahl folgt. Das Backend rechnet denselben Wert und schickt ihn mit; diese Funktion
 * ist nur für die Vorschau, solange der Nutzer noch nicht gespeichert hat.
 */
export const effectiveFromChoice = (choice: RoundProgressionChoice, eventDefault: boolean): boolean =>
    choice === 'INHERIT' ? eventDefault : choice === 'ENABLED'
