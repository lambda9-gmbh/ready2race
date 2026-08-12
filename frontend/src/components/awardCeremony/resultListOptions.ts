import {DownloadResultListData} from '@api/types.gen.ts'

/**
 * Die Häkchen des Ergebnislisten-Dialogs. `largePrint` steht für den Aushang-Schriftgrad —
 * abgeschaltet setzt der Server im Schriftgrad des Siegerehrungsbogens.
 */
export type ResultListOptions = {
    /** Crew-Aufstellung: die Namen je Boot. */
    crew: boolean
    /** Zeiten und Zeitstrafen. */
    times: boolean
    /** Nur die Plätze 1 bis 3 statt aller platzierten Boote. */
    podiumOnly: boolean
    /** Je Wertungskategorie ein eigener Abschnitt statt des Gesamtfelds. */
    byRatingCategory: boolean
    /** Große Schrift für den Aushang. */
    largePrint: boolean
}

export type ResultListPreset = 'posting' | 'ceremony'

/**
 * Die beiden Startpunkte des Dialogs. Ein Preset ist nur eine Vorbelegung der Häkchen — jedes
 * lässt sich danach frei umstellen, und der Download schickt immer die einzelnen Schalter.
 */
export const presetOptions: Record<ResultListPreset, ResultListOptions> = {
    // Der Aushang: alles drauf, alle Plätze, groß gesetzt.
    posting: {crew: true, times: true, podiumOnly: false, byRatingCategory: true, largePrint: true},
    // Die Siegerehrung: dieselben Bestandteile wie der klassische Bogen — Podium, Pult-Schriftgrad.
    ceremony: {crew: true, times: true, podiumOnly: true, byRatingCategory: true, largePrint: false},
}

/**
 * Das Preset, dem die Häkchen gerade entsprechen — `null`, sobald eines verstellt ist. So zeigt
 * die Preset-Auswahl ehrlich „eigene Zusammenstellung" an, statt ein Preset zu behaupten, das
 * nicht mehr stimmt.
 */
export const matchingPreset = (options: ResultListOptions): ResultListPreset | null =>
    (Object.entries(presetOptions) as Array<[ResultListPreset, ResultListOptions]>).find(
        ([, preset]) =>
            (Object.keys(preset) as Array<keyof ResultListOptions>).every(
                key => preset[key] === options[key],
            ),
    )?.[0] ?? null

/** Die Häkchen als Query-Parameter des Endpoints — die einzige Stelle, die die Namen übersetzt. */
export const resultListQuery = (
    options: ResultListOptions,
): NonNullable<DownloadResultListData['query']> => ({
    crew: options.crew,
    times: options.times,
    podiumOnly: options.podiumOnly,
    byRatingCategory: options.byRatingCategory,
    size: options.largePrint ? 'POSTING' : 'CEREMONY',
})
