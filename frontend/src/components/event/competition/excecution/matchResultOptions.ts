import {TimingFormSystem} from '@components/event/competition/timing/timingConfigForm.ts'

export const MATCH_RESULT_OPTIONS = ['form', 'XLS', 'RACECLOCKER'] as const
export type MatchResultOption = (typeof MATCH_RESULT_OPTIONS)[number]

/**
 * Welche Wege der Ergebniseingabe ein Lauf anbietet.
 *
 * Bisher waren alle drei fest verdrahtet, auch bei Wettkämpfen, die nie einen RaceClocker-Feed haben.
 * Ist Webscorer gewählt, fällt das Abholen weg. Ohne gesetztes System bleibt alles stehen — sonst
 * verlöre ein bestehender Wettkampf ohne Zutun eine Funktion.
 *
 * Erwartet wird das EFFEKTIVE System (`effectiveTimingSystem`), nicht die eigene Spalte des
 * Wettkampfs: Erbt er Webscorer von der Veranstaltung, hat er genauso wenig einen RaceClocker-Feed
 * wie einer, der es selbst eingetragen hat. Und es wäre ein eigener Begriff von „das
 * Zeitnahmesystem" neben dem seines Nachbarn in derselben Zeile.
 */
export const matchResultOptions = (timingSystem: TimingFormSystem): MatchResultOption[] =>
    timingSystem === 'WEBSCORER'
        ? MATCH_RESULT_OPTIONS.filter(o => o !== 'RACECLOCKER')
        : [...MATCH_RESULT_OPTIONS]
