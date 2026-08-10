import {
    CheckSeverity,
    CheckSeverityCompetitionDto,
    CheckSeverityConfigDto,
    CheckSeverityEntryDto,
    CheckSeverityRowDto,
} from '@api/types.gen.ts'

export type RowSummary =
    | {kind: 'uniform'; severity: CheckSeverity}
    | {kind: 'mixed'}
    | {kind: 'empty'}

/**
 * Der verdichtete Zustand einer Zeile. Er steht eingeklappt neben dem Namen der Prüfung und
 * beantwortet die einzige Frage, die man ohne Aufklappen hat: Ist hier vom Standard abgewichen
 * worden, und wenn ja, überall gleich?
 *
 * Ohne Wettkämpfe gibt es nichts zu verdichten - "leer" ist dafür die eigene, dritte Antwort
 * (nicht "gemischt", das wäre falsch, und keine Ausnahme, damit ein Renderpfad ohne
 * Wettkämpfe nicht die ganze Seite mitreißt). Was für "leer" angezeigt wird, entscheidet der
 * Aufrufer.
 */
export const rowSummary = (severities: CheckSeverity[]): RowSummary => {
    if (severities.length === 0) {
        return {kind: 'empty'}
    }
    return severities.every(s => s === severities[0])
        ? {kind: 'uniform', severity: severities[0]}
        : {kind: 'mixed'}
}

/**
 * Wettkämpfe ohne `checkInOutRequired` gehören für die Prüfung "Nicht in der Arena" gar nicht
 * erst in die Matrix - dort gibt es nichts einzustellen. Für alle anderen Prüfungen ist jeder
 * Wettkampf anwendbar.
 */
export const isRowApplicable = (
    row: Pick<CheckSeverityRowDto, 'checkType'>,
    competition: Pick<CheckSeverityCompetitionDto, 'checkInOutRequired'>,
): boolean => row.checkType !== 'NOT_IN_ARENA' || competition.checkInOutRequired

/**
 * Der eingestellte Wert eines Feldes der Matrix, sonst der vom Server gelieferte Standard.
 *
 * Der letzte `?? 'CRITICAL'` ist keine dritte, dokumentierte Stufe, sondern eine Notbremse: Sie
 * greift nur, wenn weder ein Eintrag noch ein Standard zur Kombination passt - ein Zustand, der
 * bei widersprüchlichen Serverdaten entstehen kann (z.B. eine Zeile ohne zugehörigen Standard),
 * im Normalbetrieb aber nicht vorkommen sollte.
 */
export const severityAt = (
    config: CheckSeverityConfigDto,
    entries: CheckSeverityEntryDto[],
    competitionId: string,
    checkType: CheckSeverityEntryDto['checkType'],
    requirementId: string | null,
): CheckSeverity =>
    entries.find(
        e =>
            e.competitionId === competitionId &&
            e.checkType === checkType &&
            (e.requirementId ?? null) === requirementId,
    )?.severity ??
    config.defaults.find(
        d => d.checkType === checkType && (d.requirementId ?? null) === requirementId,
    )?.severity ??
    'CRITICAL'

/**
 * Eine Zelle der bearbeitbaren Matrix: eine Kombination aus Wettkampf und Zeile, für die
 * `isRowApplicable` zutrifft.
 */
type MatrixCell = {competition: CheckSeverityCompetitionDto; row: CheckSeverityRowDto}

/**
 * Alle Zellen der bearbeitbaren Matrix - dieselbe Kombination aus `config.competitions` und
 * `config.rows`, gefiltert mit `isRowApplicable`, mit der auch der Verwaltungsdialog seine Matrix
 * aufbaut. Das ist bewusst die einzige Stelle, an der diese Kombination gebildet wird: Sowohl der
 * Dialog als auch `preservedEntries` leiten sich davon ab und können so nicht auseinanderlaufen.
 */
export const applicableCells = (
    config: Pick<CheckSeverityConfigDto, 'competitions' | 'rows'>,
): MatrixCell[] =>
    config.competitions.flatMap(competition =>
        config.rows
            .filter(row => isRowApplicable(row, competition))
            .map(row => ({competition, row})),
    )

const entryKey = (
    e: Pick<CheckSeverityEntryDto, 'competitionId' | 'checkType' | 'requirementId'>,
) => `${e.competitionId}:${e.checkType}:${e.requirementId ?? ''}`

const cellKey = ({competition, row}: MatrixCell) =>
    entryKey({
        competitionId: competition.competitionId,
        checkType: row.checkType,
        requirementId: row.requirementId ?? null,
    })

/**
 * Die gespeicherten Einträge, deren Kombination aus Wettkampf, Prüfungsart und Bedingung in der
 * bearbeitbaren Matrix gerade nicht vorkommt. Der Grund dafür ist unerheblich - die Matrix deckt
 * die Kombination heute nicht ab, sei es weil `checkInOutRequired` abgeschaltet wurde, der
 * Wettkampf gelöscht ist, ein Prüf-Zeitfenster entfernt wurde oder aus einem anderen Grund, der
 * eine Zeile oder einen Wettkampf aus `config` verschwinden lässt. Der Dialog zeigt solche
 * Einträge nicht an und lässt sie nicht bearbeiten, muss sie aber beim Speichern unverändert an
 * die Nutzlast anhängen - sonst ersetzt `replaceForEvent` sie durch den Standard und der zuvor
 * eingestellte Wert ist unwiederbringlich weg.
 */
export const preservedEntries = (config: CheckSeverityConfigDto): CheckSeverityEntryDto[] => {
    const keys = new Set(applicableCells(config).map(cellKey))
    return config.entries.filter(e => !keys.has(entryKey(e)))
}

/**
 * Die Nutzlast fürs Speichern: die bearbeitete Matrix, ergänzt um die bewahrten Einträge nicht
 * (mehr) abgedeckter Kombinationen. Ein bewahrter Eintrag, dessen Kombination sich - z.B. durch
 * eine zwischenzeitliche Änderung der Konfiguration - inzwischen doch schon in der Matrix
 * wiederfindet, wird nicht noch einmal angehängt: Das Backend lehnt doppelte Einträge derselben
 * Kombination ab.
 */
export const buildSavePayload = (
    entries: CheckSeverityEntryDto[],
    preserved: CheckSeverityEntryDto[],
): CheckSeverityEntryDto[] => {
    const keys = new Set(entries.map(entryKey))
    return [...entries, ...preserved.filter(e => !keys.has(entryKey(e)))]
}
