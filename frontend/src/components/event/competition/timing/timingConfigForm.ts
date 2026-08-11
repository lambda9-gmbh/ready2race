import {TimingConfigDto, TimingConfigRequest} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/**
 * 'NONE' ist der Formular-Stellvertreter für „kein Zeitnahme-System gesetzt“ (Spalte null). Ein
 * Radio-Button braucht einen Wert; null lässt sich nicht auswählen.
 */
export type TimingFormSystem = 'NONE' | 'RACECLOCKER' | 'WEBSCORER'

export type TimingForm = {
    timingSystem: TimingFormSystem
    /** Das angewählte Rennen je Rundenart; null heißt „kein Rennen zugewiesen". */
    raceQualification: AutocompleteOption
    raceRounds: AutocompleteOption
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
    resultImportConfig: AutocompleteOption
    /**
     * Kein Eingabefeld, sondern eine mitgeführte Angabe des Servers: hat der Ablauf dieses Wettkampfs
     * eine Qualifikationsrunde? Sie hängt hier mit drin, weil der Zeitnahme- und der Durchführungs-Tab
     * die Zeitnahme-Konfiguration ohnehin beide laden — so kommen beide ohne zusätzliche Abfrage an den
     * Wert, und `timingConfigWarnings` bleibt eine reine Funktion über einem einzigen Objekt.
     * `mapTimingFormToRequest` lässt das Feld aus, es wird nie zurückgeschrieben.
     */
    hasQualificationRound: boolean
    /**
     * Ebenfalls nur mitgeführt: die Zeitnahme-Voreinstellung der Veranstaltung. Wettkämpfe erben
     * System und die beiden Dateiformate von dort, solange die eigenen Felder leer sind — die
     * RaceClocker-Rennen dagegen werden pro Wettkampf zugewiesen und nicht geerbt. Die Oberfläche
     * zeigt an, WAS geerbt würde, und die Warnungen rechnen auf den effektiven Werten statt auf den
     * lokalen.
     *
     * Die Formate stehen hier als blanke ID: den Namen schlägt die Komponente in den geladenen
     * Listen nach, damit dieses Modul ohne Netz testbar bleibt.
     */
    eventTimingSystem: TimingFormSystem
    eventStartlistConfigQualification: string
    eventStartlistConfigRounds: string
    eventResultImportConfig: string
}

export const emptyTimingForm: TimingForm = {
    timingSystem: 'NONE',
    raceQualification: null,
    raceRounds: null,
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
    hasQualificationRound: false,
    eventTimingSystem: 'NONE',
    eventStartlistConfigQualification: '',
    eventStartlistConfigRounds: '',
    eventResultImportConfig: '',
}

/** Wettkampf-Wert vor Veranstaltungs-Voreinstellung — dieselbe Regel wie das Backend (coalesce). */
export const effectiveTimingSystem = (form: TimingForm): TimingFormSystem =>
    form.timingSystem !== 'NONE' ? form.timingSystem : form.eventTimingSystem

/**
 * Weicht der Wettkampf von der Veranstaltung ab? Genau dann, wenn er selbst etwas gesetzt hat — auch
 * ein einzelnes eigenes Feld zählt, denn es zeigt auf ein anderes Rennen oder auf andere Spalten als
 * die Voreinstellung. Der „Überschreiben"-Schalter im Tab steht danach.
 */
export const overridesTiming = (form: TimingForm): boolean =>
    form.timingSystem !== 'NONE' ||
    form.raceQualification !== null ||
    form.raceRounds !== null ||
    form.startlistConfigQualification !== null ||
    form.startlistConfigRounds !== null ||
    form.resultImportConfig !== null

/**
 * Die Preset-Felder kommen als reine UUID aus dem Backend. Das Label füllt die Komponente nach, sobald
 * die Preset-Listen geladen sind — hier steht nur die ID, damit diese Funktion ohne Netz testbar bleibt.
 */
export const mapDtoToTimingForm = (dto: TimingConfigDto): TimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    raceQualification: dto.raceQualification ? {id: dto.raceQualification, label: ''} : null,
    raceRounds: dto.raceRounds ? {id: dto.raceRounds, label: ''} : null,
    startlistConfigQualification: dto.startlistConfigQualification
        ? {id: dto.startlistConfigQualification, label: ''}
        : null,
    startlistConfigRounds: dto.startlistConfigRounds
        ? {id: dto.startlistConfigRounds, label: ''}
        : null,
    resultImportConfig: dto.resultImportConfig ? {id: dto.resultImportConfig, label: ''} : null,
    hasQualificationRound: dto.hasQualificationRound ?? false,
    eventTimingSystem: dto.eventTimingSystem ?? 'NONE',
    eventStartlistConfigQualification: dto.eventStartlistConfigQualification ?? '',
    eventStartlistConfigRounds: dto.eventStartlistConfigRounds ?? '',
    eventResultImportConfig: dto.eventResultImportConfig ?? '',
})

/**
 * Felder, die für das gewählte System nicht sichtbar sind, werden bewusst geleert statt durchgereicht:
 * eine unsichtbare URL oder ein unsichtbares Preset wäre eine Einstellung, die niemand mehr findet.
 */
export const mapTimingFormToRequest = (form: TimingForm): TimingConfigRequest => {
    // Effektiv statt lokal: erbt der Wettkampf RaceClocker von der Veranstaltung, sind die
    // URL-Felder sichtbar und ihre Eingaben sind gezielte Overrides — die dürfen nicht beim
    // Speichern verschwinden, nur weil das lokale System-Feld auf "NONE" (= erben) steht.
    const raceClocker = effectiveTimingSystem(form) === 'RACECLOCKER'
    const configured = effectiveTimingSystem(form) !== 'NONE'

    return {
        timingSystem: form.timingSystem === 'NONE' ? null : form.timingSystem,
        raceQualification: raceClocker ? (form.raceQualification?.id ?? null) : null,
        raceRounds: raceClocker ? (form.raceRounds?.id ?? null) : null,
        startlistConfigQualification: raceClocker
            ? (form.startlistConfigQualification?.id ?? null)
            : null,
        startlistConfigRounds: configured ? (form.startlistConfigRounds?.id ?? null) : null,
        resultImportConfig: configured ? (form.resultImportConfig?.id ?? null) : null,
    }
}

export type TimingWarning =
    | 'raceRounds'
    | 'raceQualification'
    | 'startlistRounds'
    | 'startlistQualification'

/**
 * Was fehlt, um die Zeitnahme benutzen zu können.
 *
 * Das Zeitfahren-Rennen und das Qualifikations-Preset hängen an [TimingForm.hasQualificationRound]: ein
 * Wettkampf ohne Qualifikationsrunde braucht beides nie, und eine Warnung, die dort dauerhaft steht,
 * wird ignoriert. Hat er eine, sind beide genauso Pflicht wie die übrigen Felder — sonst antwortet der
 * Startlisten-Export mit STARTLIST_CONFIG_NOT_CONFIGURED und der Lauf-Pull findet keine Ergebnisse,
 * beides erst am Renntag sichtbar.
 *
 * Beide gelten nur für RaceClocker: Webscorer kennt die Zweiteilung nicht, füllt nur den Runden-Slot
 * und fällt für die Qualifikation darauf zurück (siehe StartListConfigTarget im Backend).
 */
export const timingConfigWarnings = (form: TimingForm): TimingWarning[] => {
    // Auf den effektiven Werten gerechnet: was die Veranstaltung vorbelegt, fehlt nicht.
    const system = effectiveTimingSystem(form)
    if (system === 'NONE') return []

    const raceClocker = system === 'RACECLOCKER'
    // Rennen werden pro Wettkampf zugewiesen, nicht von der Veranstaltung geerbt.
    const raceRounds = form.raceRounds?.id
    const raceQualification = form.raceQualification?.id
    // Auch die Formate können von der Veranstaltung kommen — dann fehlen sie nicht.
    const startlistRounds = form.startlistConfigRounds?.id || form.eventStartlistConfigRounds
    const startlistQualification =
        form.startlistConfigQualification?.id || form.eventStartlistConfigQualification

    const warnings: TimingWarning[] = []
    if (raceClocker && !raceRounds) {
        warnings.push('raceRounds')
    }
    if (raceClocker && form.hasQualificationRound && !raceQualification) {
        warnings.push('raceQualification')
    }
    if (!startlistRounds) {
        warnings.push('startlistRounds')
    }
    if (raceClocker && form.hasQualificationRound && !startlistQualification) {
        warnings.push('startlistQualification')
    }
    return warnings
}
