import {TimingConfigDto} from '@api/types.gen.ts'

export const MATCH_RESULT_OPTIONS = ['form', 'XLS', 'RACECLOCKER'] as const
export type MatchResultOption = (typeof MATCH_RESULT_OPTIONS)[number]

/**
 * Welche Wege der Ergebniseingabe ein Lauf anbietet.
 *
 * Bisher waren alle drei fest verdrahtet, auch bei Wettkämpfen, die nie einen RaceClocker-Feed haben.
 * Ist Webscorer gewählt, fällt das Abholen weg. Ohne gesetztes System bleibt alles stehen — sonst
 * verlöre ein bestehender Wettkampf ohne Zutun eine Funktion.
 */
export const matchResultOptions = (
    timingSystem: TimingConfigDto['timingSystem'],
): MatchResultOption[] =>
    timingSystem === 'WEBSCORER'
        ? MATCH_RESULT_OPTIONS.filter(o => o !== 'RACECLOCKER')
        : [...MATCH_RESULT_OPTIONS]
