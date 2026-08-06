import {ErrorCode} from '@api/types.gen.ts'
import {extractMaxReductionMinutes} from './common.ts'

// Alle Meldungen, die Verschieben-Dialog und Import-Dialog anzeigen können - als Literale, damit
// t() sie akzeptiert und ein Tippfehler beim Bauen auffällt statt erst am Renntag im Dialog.
const shiftKeys = {
    withoutChange: 'event.schedule.shift.error.withoutChange',
    targetNotAfterStart: 'event.schedule.shift.error.targetNotAfterStart',
    negativeDelay: 'event.schedule.shift.error.negativeDelay',
    leavesRaceDay: 'event.schedule.shift.error.leavesRaceDay',
    leavesRaceDayNoSlot: 'event.schedule.shift.error.leavesRaceDayNoSlot',
    leavesRaceDayPlain: 'event.schedule.shift.error.leavesRaceDayPlain',
    overtakesPredecessor: 'event.schedule.shift.error.overtakesPredecessor',
    overtakesPredecessorPlain: 'event.schedule.shift.error.overtakesPredecessorPlain',
    compressionImpossible: 'event.schedule.shift.impossible',
    unexpected: 'event.schedule.shift.error.unexpected',
} as const

const importKeys = {
    fileError: 'common.error.upload.FILE_ERROR',
    noHeaders: 'common.error.upload.NO_HEADERS',
    columnUnknown: 'common.error.upload.COLUMN_UNKNOWN',
    cellBlank: 'common.error.upload.CELL_BLANK',
    wrongCellType: 'common.error.upload.WRONG_CELL_TYPE',
    unparsableString: 'common.error.upload.UNPARSABLE_STRING',
    duplicateRows: 'event.schedule.importDialog.error.duplicateRows',
    unexpected: 'event.schedule.importDialog.error.unexpected',
} as const

export type ScheduleErrorKey =
    | (typeof shiftKeys)[keyof typeof shiftKeys]
    | (typeof importKeys)[keyof typeof importKeys]

/**
 * Ein übersetzbarer Meldungstext: i18n-Key plus die Werte, die er einsetzt. Bewusst nur der Key
 * statt der fertigen Meldung — so bleibt die Zuordnung ohne i18n-Kontext testbar (dasselbe Muster
 * wie bei deregistrationError.ts).
 */
export type ScheduleErrorText = {
    key: ScheduleErrorKey
    values?: Record<string, string | number>
}

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type ScheduleApiError = {
    message: string
    errorCode?: ErrorCode
    details?: unknown
}

const detailsOf = (error: ScheduleApiError): Record<string, unknown> =>
    (error.details as Record<string, unknown> | undefined) ?? {}

const asString = (value: unknown): string | undefined =>
    typeof value === 'string' ? value : undefined

const asNumber = (value: unknown): number | undefined =>
    typeof value === 'number' ? value : undefined

export const shiftUnexpectedKey = shiftKeys.unexpected
export const importUnexpectedKey = importKeys.unexpected

/**
 * Kontext, den nur der Dialog liefern kann: Slot-Namen und die Zeitformatierung des aktuellen
 * Gebietsschemas. Der Server schickt Slot-ID und ISO-Zeit; lesbar macht sie erst die Oberfläche,
 * die die Slot-Liste ohnehin schon hat.
 */
export type ShiftErrorContext = {
    slotName: (slotId: string) => string | undefined
    formatTime: (isoDateTime: string) => string
}

/**
 * Der Meldungstext zu einer abgelehnten Zeitplan-Verschiebung. Die vier Ablehnungsgründe des
 * Verschiebe-Dialogs (siehe EventScheduleError: ShiftWithoutChange, ShiftTargetInvalid,
 * ShiftLeavesRaceDay, ShiftOvertakesPredecessor) tragen dafür je einen ErrorCode; früher kamen
 * sie alle als ein und derselbe englische Satz an. Alles Unbekannte — auch ein Fehler ganz ohne
 * Code — läuft in die allgemeine Meldung, statt den Backend-Text durchzureichen.
 */
export const shiftErrorText = (
    error: ScheduleApiError,
    ctx: ShiftErrorContext,
): ScheduleErrorText => {
    const details = detailsOf(error)

    switch (error.errorCode) {
        case 'SCHEDULE_SHIFT_WITHOUT_CHANGE':
            return {key: shiftKeys.withoutChange}

        case 'SCHEDULE_SHIFT_TARGET_INVALID':
            return {
                key:
                    asString(details.problem) === 'NEGATIVE_DELAY'
                        ? shiftKeys.negativeDelay
                        : shiftKeys.targetNotAfterStart,
            }

        case 'SCHEDULE_SHIFT_LEAVES_RACE_DAY': {
            const slotId = asString(details.slotId)
            const newStartTime = asString(details.newStartTime)
            const slot = slotId !== undefined ? ctx.slotName(slotId) : undefined
            // Ohne auflösbaren Slot bleibt die Aussage dieselbe, nur ohne Namen — der ist Beiwerk,
            // die Ursache ("rutscht über den Renntag hinaus") steht auch so da.
            return newStartTime === undefined
                ? {key: shiftKeys.leavesRaceDayPlain}
                : {
                      key: slot === undefined ? shiftKeys.leavesRaceDayNoSlot : shiftKeys.leavesRaceDay,
                      values: {slot: slot ?? '', time: ctx.formatTime(newStartTime)},
                  }
        }

        case 'SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR': {
            const earliest = asString(details.earliestStartTime)
            const max = asNumber(details.maxAdvanceMinutes)
            return earliest === undefined || max === undefined
                ? {key: shiftKeys.overtakesPredecessorPlain}
                : {
                      key: shiftKeys.overtakesPredecessor,
                      values: {earliest: ctx.formatTime(earliest), max},
                  }
        }
    }

    // CompressionImpossible trägt (noch) keinen ErrorCode, wohl aber die Minutenzahl in details —
    // siehe extractMaxReductionMinutes samt Regex-Fallback auf den Freitext.
    const maxReduction = extractMaxReductionMinutes(error)
    return maxReduction !== undefined
        ? {key: shiftKeys.compressionImpossible, values: {max: maxReduction}}
        : {key: shiftKeys.unexpected}
}

/**
 * Der Meldungstext zu einem abgelehnten Excel-Import. Die Lesefehler benutzen dieselben
 * SPREADSHEET_*-Codes wie der Ergebnis-Upload und damit auch dessen Übersetzungen; {{row}} ist
 * die Zeilennummer, wie Excel sie anzeigt (Kopfzeile = 1, erste Datenzeile = 2).
 */
export const importErrorText = (error: ScheduleApiError): ScheduleErrorText => {
    const details = detailsOf(error)

    switch (error.errorCode) {
        case 'FILE_ERROR':
            return {key: importKeys.fileError}

        case 'SPREADSHEET_NO_HEADERS':
            return {key: importKeys.noHeaders}

        case 'SPREADSHEET_COLUMN_UNKNOWN':
            return {
                key: importKeys.columnUnknown,
                values: {expected: asString(details.expected) ?? ''},
            }

        case 'SPREADSHEET_CELL_BLANK':
            return {
                key: importKeys.cellBlank,
                values: {row: asNumber(details.row) ?? 0, column: asString(details.column) ?? ''},
            }

        case 'SPREADSHEET_WRONG_CELL_TYPE':
            return {
                key: importKeys.wrongCellType,
                values: {
                    row: asNumber(details.row) ?? 0,
                    column: asString(details.column) ?? '',
                    actual: asString(details.actual) ?? '',
                    expected: asString(details.expected) ?? '',
                },
            }

        case 'SPREADSHEET_UNPARSABLE_STRING':
            return {
                key: importKeys.unparsableString,
                values: {
                    row: asNumber(details.row) ?? 0,
                    column: asString(details.column) ?? '',
                    value: asString(details.value) ?? '',
                },
            }

        case 'SCHEDULE_IMPORT_DUPLICATE_ROWS': {
            const rows = Array.isArray(details.rowNumbers)
                ? (details.rowNumbers as unknown[]).filter((r): r is number => typeof r === 'number')
                : []
            return rows.length === 0
                ? {key: importKeys.unexpected}
                : {key: importKeys.duplicateRows, values: {rows: rows.join(', '), count: rows.length}}
        }
    }

    return {key: importKeys.unexpected}
}
