import {describe, expect, it} from 'vitest'
import {
    ScheduleApiError,
    importErrorText,
    importUnexpectedKey,
    shiftErrorText,
    shiftUnexpectedKey,
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
})
