import {describe, expect, it} from 'vitest'
import {
    ScheduleApiError,
    advanceErrorText,
    importErrorText,
    importUnexpectedKey,
    roundSkipErrorText,
    roundSkipUnexpectedKey,
    shiftErrorText,
    shiftUnexpectedKey,
    slotActionErrorText,
    slotActionUnexpectedKey,
} from './scheduleError.ts'
import deTranslations from '@i18n/de/translations.json'
import enTranslations from '@i18n/en/translations.json'
import daTranslations from '@i18n/da/translations.json'

// Der Kontext, den sonst der Dialog liefert: Slot-Namen und Zeitformatierung. Hier bewusst
// vorhersagbar, damit die Zuordnung ohne i18n und ohne Gebietsschema prüfbar bleibt.
const ctx = {
    slotName: (slotId: string) => (slotId === 'slot-7' ? 'Vorlauf 3' : undefined),
    formatTime: (iso: string) => `[${iso}]`,
}

const error = (partial: Partial<ScheduleApiError>): ScheduleApiError => ({
    message: 'irgendein englischer Backend-Text',
    ...partial,
})

/** Löst einen Punkt-getrennten i18n-Key in einer Übersetzungsdatei auf. */
const lookup = (translations: object, key: string): unknown =>
    key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], translations)

const de = (key: string): unknown => lookup(deTranslations, key)

describe('shiftErrorText', () => {
    it('nennt bei 0 Minuten, dass sich nichts ändern würde', () => {
        expect(shiftErrorText(error({errorCode: 'SCHEDULE_SHIFT_WITHOUT_CHANGE'}), ctx)).toEqual({
            key: 'event.schedule.shift.error.withoutChange',
        })
    })

    it('unterscheidet die beiden Gründe für ein untaugliches Aufhol-Ziel', () => {
        expect(
            shiftErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_TARGET_INVALID',
                    details: {problem: 'NEGATIVE_DELAY'},
                }),
                ctx,
            ),
        ).toEqual({key: 'event.schedule.shift.error.negativeDelay'})

        expect(
            shiftErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_TARGET_INVALID',
                    details: {problem: 'TARGET_NOT_AFTER_START'},
                }),
                ctx,
            ),
        ).toEqual({key: 'event.schedule.shift.error.targetNotAfterStart'})
    })

    it('benennt den Slot, der über den Renntag hinausrutscht', () => {
        expect(
            shiftErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_LEAVES_RACE_DAY',
                    details: {
                        slotId: 'slot-7',
                        newStartTime: '2026-08-18T01:15',
                        raceDay: '2026-08-17',
                    },
                }),
                ctx,
            ),
        ).toEqual({
            key: 'event.schedule.shift.error.leavesRaceDay',
            values: {slot: 'Vorlauf 3', time: '[2026-08-18T01:15]'},
        })
    })

    it('bleibt ohne auflösbaren Slot bei der Zeitangabe', () => {
        expect(
            shiftErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_LEAVES_RACE_DAY',
                    details: {slotId: 'unbekannt', newStartTime: '2026-08-18T01:15'},
                }),
                ctx,
            ),
        ).toEqual({
            key: 'event.schedule.shift.error.leavesRaceDayNoSlot',
            values: {slot: '', time: '[2026-08-18T01:15]'},
        })
    })

    it('nennt beim Vorziehen die Grenze und den verbleibenden Spielraum', () => {
        expect(
            shiftErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR',
                    details: {earliestStartTime: '2026-08-17T09:40', maxAdvanceMinutes: 20},
                }),
                ctx,
            ),
        ).toEqual({
            key: 'event.schedule.shift.error.overtakesPredecessor',
            values: {earliest: '[2026-08-17T09:40]', max: 20},
        })
    })

    it('fällt ohne verwertbare details auf die schlichte Variante zurück', () => {
        expect(
            shiftErrorText(error({errorCode: 'SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR'}), ctx),
        ).toEqual({key: 'event.schedule.shift.error.overtakesPredecessorPlain'})
    })

    it('behält den bestehenden Weg für CompressionImpossible bei', () => {
        expect(
            shiftErrorText(
                error({message: 'Cannot compress: only 20 minutes available', details: {maxReductionMinutes: 20}}),
                ctx,
            ),
        ).toEqual({key: 'event.schedule.shift.impossible', values: {max: 20}})
    })

    it('reicht Unbekanntes nicht als englischen Backend-Text durch', () => {
        expect(shiftErrorText(error({}), ctx)).toEqual({key: shiftUnexpectedKey})
    })
})

describe('importErrorText', () => {
    it('nennt Zeile, Spalte und den beanstandeten Wert', () => {
        expect(
            importErrorText(
                error({
                    errorCode: 'SPREADSHEET_UNPARSABLE_STRING',
                    details: {row: 3, column: 'Uhrzeit', value: 'viertel nach zehn'},
                }),
            ),
        ).toEqual({
            key: 'common.error.upload.UNPARSABLE_STRING',
            values: {row: 3, column: 'Uhrzeit', value: 'viertel nach zehn'},
        })
    })

    it('nennt bei einer leeren Pflichtzelle Zeile und Spalte', () => {
        expect(
            importErrorText(
                error({errorCode: 'SPREADSHEET_CELL_BLANK', details: {row: 5, column: 'Lauf'}}),
            ),
        ).toEqual({key: 'common.error.upload.CELL_BLANK', values: {row: 5, column: 'Lauf'}})
    })

    it('nennt beim falschen Zellentyp beide Typen', () => {
        expect(
            importErrorText(
                error({
                    errorCode: 'SPREADSHEET_WRONG_CELL_TYPE',
                    details: {row: 2, column: 'Dauer', actual: 'STRING', expected: 'NUMERIC'},
                }),
            ),
        ).toEqual({
            key: 'common.error.upload.WRONG_CELL_TYPE',
            values: {row: 2, column: 'Dauer', actual: 'STRING', expected: 'NUMERIC'},
        })
    })

    it('nennt die fehlende Spalte', () => {
        expect(
            importErrorText(
                error({errorCode: 'SPREADSHEET_COLUMN_UNKNOWN', details: {expected: 'Datum'}}),
            ),
        ).toEqual({key: 'common.error.upload.COLUMN_UNKNOWN', values: {expected: 'Datum'}})
    })

    it('listet die doppelten Zeilennummern auf', () => {
        expect(
            importErrorText(
                error({errorCode: 'SCHEDULE_IMPORT_DUPLICATE_ROWS', details: {rowNumbers: [4, 5]}}),
            ),
        ).toEqual({
            key: 'event.schedule.importDialog.error.duplicateRows',
            values: {rows: '4, 5', count: 2},
        })
    })

    it('reicht Unbekanntes nicht als englischen Backend-Text durch', () => {
        expect(importErrorText(error({}))).toEqual({key: importUnexpectedKey})
    })
})

describe('slotActionErrorText', () => {
    it.each([
        ['SCHEDULE_SLOT_MATCH_ALREADY_STARTED', 'event.schedule.error.matchAlreadyStarted'],
        ['SCHEDULE_SLOT_MATCH_ALREADY_FINISHED', 'event.schedule.error.matchAlreadyFinished'],
        ['SCHEDULE_SLOT_NOT_SKIPPABLE', 'event.schedule.error.slotNotSkippable'],
        ['SCHEDULE_SLOT_NOT_LINKED', 'event.schedule.error.slotNotLinked'],
        ['SCHEDULE_SETUP_MATCH_ALREADY_PLANNED', 'event.schedule.error.setupMatchAlreadyPlanned'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, key) => {
        expect(slotActionErrorText(error({errorCode}))).toEqual({key})
    })

    it('trennt "läuft schon" von "bereits beendet"', () => {
        // Bis zuletzt teilten sich beide common.error.unexpected, obwohl der eine Fall zum
        // Deaktivieren auffordert und der andere gar nicht mehr zu retten ist.
        expect(slotActionErrorText(error({errorCode: 'SCHEDULE_SLOT_MATCH_ALREADY_STARTED'}))).not
            .toEqual(slotActionErrorText(error({errorCode: 'SCHEDULE_SLOT_MATCH_ALREADY_FINISHED'})))
    })

    it('reicht Unbekanntes nicht als englischen Backend-Text durch', () => {
        expect(slotActionErrorText(error({}))).toEqual({key: slotActionUnexpectedKey})
    })
})

describe('roundSkipErrorText', () => {
    it('sagt bei einer ungesetzten Runde, dass es nichts zu entfallen gibt', () => {
        expect(roundSkipErrorText(error({errorCode: 'SCHEDULE_ROUND_NOT_MATERIALIZED'}))).toEqual({
            key: 'event.competition.execution.cancelRound.error.notMaterialized',
        })
    })

    it('nennt die Anzahl der noch zu fahrenden Läufe', () => {
        expect(
            roundSkipErrorText(
                error({
                    errorCode: 'SCHEDULE_ROUND_HAS_RUNS_TO_RACE',
                    details: {raceableMatchCount: 3},
                }),
            ),
        ).toEqual({
            key: 'event.competition.execution.cancelRound.error.hasRunsToRace',
            values: {count: 3},
        })
    })

    it('unterscheidet die beiden gegensätzlichen Runden-Gründe', () => {
        // Der eine Fall heißt "setz die Runde erst", der andere "diese Läufe müssen gefahren
        // werden" - genau die Verwechslung, die der gemeinsame Text cancelRound.error erzeugte.
        expect(roundSkipErrorText(error({errorCode: 'SCHEDULE_ROUND_NOT_MATERIALIZED'})).key).not.toBe(
            roundSkipErrorText(error({errorCode: 'SCHEDULE_ROUND_HAS_RUNS_TO_RACE'})).key,
        )
    })

    it('reicht Unbekanntes nicht als englischen Backend-Text durch', () => {
        expect(roundSkipErrorText(error({}))).toEqual({key: roundSkipUnexpectedKey})
    })
})

describe('Übersetzungen', () => {
    // Ein falsch geschriebener Key fällt sonst erst am Renntag auf - dann steht der rohe Key im
    // Dialog. Die Pluralformen (count) liegen unter _one/_other, deshalb hier getrennt geprüft.
    const singleKeys = [
        'event.schedule.shift.error.withoutChange',
        'event.schedule.shift.error.targetNotAfterStart',
        'event.schedule.shift.error.negativeDelay',
        'event.schedule.shift.error.leavesRaceDay',
        'event.schedule.shift.error.leavesRaceDayNoSlot',
        'event.schedule.shift.error.leavesRaceDayPlain',
        'event.schedule.shift.error.overtakesPredecessor',
        'event.schedule.shift.error.overtakesPredecessorPlain',
        'event.schedule.shift.impossible',
        shiftUnexpectedKey,
        importUnexpectedKey,
        'common.error.upload.CELL_BLANK',
        'common.error.upload.WRONG_CELL_TYPE',
        'common.error.upload.UNPARSABLE_STRING',
        'common.error.upload.COLUMN_UNKNOWN',
        'common.error.upload.NO_HEADERS',
        'common.error.upload.FILE_ERROR',
        'event.schedule.error.matchAlreadyStarted',
        'event.schedule.error.matchAlreadyFinished',
        'event.schedule.error.slotNotSkippable',
        'event.schedule.error.slotNotLinked',
        'event.schedule.error.setupMatchAlreadyPlanned',
        slotActionUnexpectedKey,
        'event.competition.execution.cancelRound.error.notMaterialized',
        roundSkipUnexpectedKey,
    ]

    it.each(singleKeys)('hat einen deutschen Text für %s', key => {
        expect(typeof de(key)).toBe('string')
    })

    // Eine Sprache mit Lücke fällt sonst erst auf, wenn jemand die Oberfläche umstellt.
    it.each(singleKeys)('hat auch einen englischen und dänischen Text für %s', key => {
        expect(typeof lookup(enTranslations, key)).toBe('string')
        expect(typeof lookup(daTranslations, key)).toBe('string')
    })

    it('hat beide Pluralformen für die doppelten Import-Zeilen', () => {
        for (const translations of [deTranslations, enTranslations, daTranslations]) {
            expect(
                typeof lookup(translations, 'event.schedule.importDialog.error.duplicateRows_one'),
            ).toBe('string')
            expect(
                typeof lookup(translations, 'event.schedule.importDialog.error.duplicateRows_other'),
            ).toBe('string')
        }
    })

    it('hat beide Pluralformen für die noch zu fahrenden Läufe', () => {
        for (const translations of [deTranslations, enTranslations, daTranslations]) {
            expect(
                typeof lookup(
                    translations,
                    'event.competition.execution.cancelRound.error.hasRunsToRace_one',
                ),
            ).toBe('string')
            expect(
                typeof lookup(
                    translations,
                    'event.competition.execution.cancelRound.error.hasRunsToRace_other',
                ),
            ).toBe('string')
        }
    })
})

describe('advanceErrorText', () => {
    it('nennt die drei eigenen Ablehnungsgründe des Vorziehens beim Namen', () => {
        expect(advanceErrorText(error({errorCode: 'SCHEDULE_ADVANCE_NO_DELTA'}), ctx)).toEqual({
            key: 'event.schedule.advance.error.noDelta',
        })
        expect(advanceErrorText(error({errorCode: 'SCHEDULE_SLOT_NOT_SKIPPED'}), ctx)).toEqual({
            key: 'event.schedule.advance.error.slotNotSkipped',
        })
        expect(
            advanceErrorText(error({errorCode: 'SCHEDULE_SHIFT_TARGET_INVALID'}), ctx),
        ).toEqual({key: 'event.schedule.advance.error.targetInvalid'})
    })

    it('reicht die Grenzen des Zeitplans an den Verschieben-Text weiter', () => {
        // Renntag und Vorgänger sind dieselbe Ablehnung wie beim Verschieben - und verdienen
        // deshalb denselben Satz, statt einer zweiten, leicht anderen Formulierung.
        expect(
            advanceErrorText(
                error({
                    errorCode: 'SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR',
                    details: {earliestStartTime: '2026-08-17T10:00:00', maxAdvanceMinutes: 12},
                }),
                ctx,
            ),
        ).toEqual({
            key: 'event.schedule.shift.error.overtakesPredecessor',
            values: {earliest: '[2026-08-17T10:00:00]', max: 12},
        })
    })

    it('faellt bei einem unbekannten Fehler auf die allgemeine Meldung zurueck', () => {
        expect(advanceErrorText(error({}), ctx)).toEqual({key: shiftUnexpectedKey})
    })

    it('hat jeden Text des Vorzieh-Dialogs in allen drei Sprachen', () => {
        const keys = [
            'event.schedule.advance.title',
            'event.schedule.advance.intro',
            'event.schedule.advance.targetSlot',
            'event.schedule.advance.help',
            'event.schedule.advance.apply',
            'event.schedule.advance.decline',
            'event.schedule.advance.success',
            'event.schedule.advance.error.noDelta',
            'event.schedule.advance.error.slotNotSkipped',
            'event.schedule.advance.error.targetInvalid',
        ]
        for (const translations of [deTranslations, enTranslations, daTranslations]) {
            for (const key of keys) {
                expect(typeof lookup(translations, key), key).toBe('string')
            }
        }
    })
})
