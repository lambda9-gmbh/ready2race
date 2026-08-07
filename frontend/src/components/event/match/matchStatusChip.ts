import {EventScheduleSlotDto, MatchStatusDto} from '@api/types.gen.ts'

/**
 * Die Farben, die die Oberflächen tatsächlich vergeben — eine Teilmenge von MUIs `ChipProps`,
 * damit dieses Modul ohne einen einzigen Import aus dem Rendering auskommt und die Ableitung
 * ohne DOM prüfbar bleibt (dasselbe Muster wie `liveDashboard/common.ts`).
 */
export type ChipColor = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'

/**
 * Ein Chip als Datensatz statt als Element: die aufrufende Komponente übersetzt [labelKey] mit
 * [values] und malt. So steht die Entscheidung, WAS ein Lauf gerade ist, an genau einer Stelle —
 * und die drei Oberflächen (Durchführung, Zeitplan, später weitere) können sie nicht
 * auseinanderlaufen lassen.
 */
export type MatchChip = {
    labelKey: string
    values?: Record<string, string | number>
    color: ChipColor
    /** Nur „Abgesagt": der Lauf steht noch da, gilt aber nicht mehr. */
    strikeThrough?: boolean
}

/**
 * Ab wann ein anstehender Lauf rot wird. Zwei Minuten Verzug sind Regattaalltag und sollen nicht
 * leuchten — sonst steht die halbe Liste in Rot und niemand sieht mehr den einen Lauf, der
 * wirklich hängt.
 */
export const OVERDUE_GRACE_MINUTES = 5

const MS_PER_MINUTE = 60_000

/**
 * Verstrichene volle Minuten seit [from], nie negativ. Die Untergrenze ist kein Schönheitsfehler,
 * sondern der Fall „Uhr des Browsers geht nach": eine negative Laufzeit wäre eine Falschaussage,
 * die 0 ist bloß ungenau.
 */
const elapsedMinutes = (from: string, now: Date): number =>
    Math.max(0, Math.floor((now.getTime() - new Date(from).getTime()) / MS_PER_MINUTE))

/** Nicht aktiv, nicht beendet, aber ein Teil der Boote hat schon ein Ergebnis. */
const isPartiallyScored = (status: MatchStatusDto): boolean =>
    status.teamsTotal > 0 && status.teamsScored > 0 && status.teamsScored < status.teamsTotal

/**
 * Der Chip eines Laufs — die Übersetzung von [MatchStatusDto] in genau eine Aussage.
 *
 * Die Reihenfolge der Zweige folgt bewusst der von `LiveDashboardLogic.deriveMatchState` im
 * Backend: Was tatsächlich passiert, schlägt den zurückgenommenen Plan. Ein abgesagter Lauf, der
 * trotzdem aktiviert wurde, kommt vom Server als RUNNING und zeigt hier „Läuft" — die Anzeige
 * behauptet nicht, es passiere nichts, während Boote auf dem Wasser sind.
 *
 * „Überfällig" und „Teilweise gewertet" sind keine Zustände, sondern Ablesungen zusätzlicher
 * Felder (siehe KDoc von `MatchStatusDto`). Sie stehen deshalb erst hinter den echten Zuständen:
 * SKIPPED darf nicht als „Teilweise gewertet" durchgehen, sonst läse sich ein abgesagter Lauf mit
 * nachgetragenen Abmeldungen wie einer, der gerade gefahren wird.
 *
 * Unter den verbliebenen Zuständen (UPCOMING, UNSCHEDULED) gewinnt die Teilwertung vor
 * „Überfällig": liegen bereits Ergebnisse vor, ist der Lauf faktisch unterwegs, und der Verzug
 * gegen die geplante Zeit sagt dann nichts mehr aus.
 *
 * [now] kommt von außen, damit die Ableitung ohne Uhr prüfbar bleibt. Die verstrichenen Minuten
 * rechnet bewusst das Frontend: beide Ansichten ticken ohnehin, und so zählt der Chip zwischen
 * zwei Abrufen weiter, statt zu stehen.
 */
export const matchStatusChip = (
    status: MatchStatusDto,
    startTime: string | null | undefined,
    now: Date,
): MatchChip => {
    if (status.state === 'RUNNING') {
        // Ohne Startstempel gibt es keine Laufzeit — der Lauf ist als aktiv gesetzt, aber niemand
        // hat einen Start gedrückt. Dann lieber „Läuft" ohne Zahl als eine erfundene.
        return status.startedAt
            ? {
                  labelKey: 'event.match.status.running',
                  values: {minutes: elapsedMinutes(status.startedAt, now)},
                  color: 'primary',
              }
            : {labelKey: 'event.match.status.runningPlain', color: 'primary'}
    }

    if (status.state === 'FINISHED') {
        return {labelKey: 'event.match.status.finished', color: 'success'}
    }

    if (status.state === 'SKIPPED') {
        return {labelKey: 'event.match.status.cancelled', color: 'default', strikeThrough: true}
    }

    if (status.state === 'AWAITING_FINISH') {
        return {labelKey: 'event.match.status.awaitingFinish', color: 'warning'}
    }

    if (isPartiallyScored(status)) {
        return {
            labelKey: 'event.match.status.partiallyScored',
            values: {scored: status.teamsScored, total: status.teamsTotal},
            color: 'warning',
        }
    }

    if (status.state === 'UNSCHEDULED') {
        // Ohne Plan gibt es keinen Verzug — ein ungeplanter Lauf wird niemals „Überfällig".
        return {labelKey: 'event.match.status.unscheduled', color: 'default'}
    }

    if (startTime != null) {
        const overdueBy = elapsedMinutes(startTime, now)
        if (overdueBy >= OVERDUE_GRACE_MINUTES) {
            return {
                labelKey: 'event.match.status.overdue',
                values: {minutes: overdueBy},
                color: 'error',
            }
        }
    }

    return {labelKey: 'event.match.status.upcoming', color: 'default'}
}

/**
 * Der zweite, leise Chip: wie viele Crews des Laufs schon abgelegt haben.
 *
 * „Auf dem Wasser" ist bewusst kein eigener Zustand — es ist eine Eigenschaft des
 * Vorbereitungsstands, keine Phase des Laufs, und würde als Zustand mit „Läuft" konkurrieren.
 *
 * `teamsOnWater == null` heißt „in dieser Ansicht nicht erhoben" (Zeitplan, öffentliche Anzeigen)
 * und ist etwas anderes als 0 („erhoben, aber niemand draußen"). Solange die Abfrage dafür nicht
 * gestellt wird, ist null der Normalfall und der Chip entfällt vollständig.
 *
 * Er entfällt außerdem, sobald er nichts mehr aussagt: bei einem Lauf ohne Mannschaften, nach
 * Beenden oder Absage, und wenn ohnehin alle Crews draußen sind. Die Regel aus Abschnitt 6 der
 * Spec („keine Anzeige, wenn kein Team der Runde je einen Scan hatte") gehört zur Runde, nicht zum
 * einzelnen Lauf, und wird mit der Abfrage in Schritt 7 nachgezogen.
 */
export const waterChip = (status: MatchStatusDto): MatchChip | null => {
    const onWater = status.teamsOnWater
    if (onWater == null) return null
    if (status.teamsTotal === 0) return null
    if (status.state !== 'UPCOMING' && status.state !== 'RUNNING') return null
    if (onWater >= status.teamsTotal) return null
    return {
        labelKey: 'event.match.status.water',
        values: {onWater, total: status.teamsTotal},
        color: 'default',
    }
}

/**
 * Die Zählerleiste über einer Runde („1 läuft · 1 offen · 3 beendet · 1 abgesagt").
 *
 * Jeder Lauf zählt in genau einen Topf; die Zuordnung folgt derselben Aufteilung wie
 * `MatchStatusLogic.roundCounters` im Backend, damit die Leiste nichts anderes behauptet als die
 * Chips darunter. Töpfe ohne Läufe fallen weg — eine Null zu lesen kostet genauso viel Blick wie
 * eine Zahl, sagt aber nichts.
 *
 * [minMatches] hält die Leiste bei einer einzigen Lauf-Karte zurück: dort wiederholt sie nur den
 * Chip daneben und ist reines Rauschen (Entscheidung aus Abschnitt 9 der Spec).
 */
export const roundCounterChips = (statuses: MatchStatusDto[], minMatches = 2): MatchChip[] => {
    if (statuses.length < minMatches) return []

    const count = (predicate: (status: MatchStatusDto) => boolean) =>
        statuses.filter(predicate).length

    const buckets: {n: number; labelKey: string; color: ChipColor}[] = [
        {
            n: count(s => s.state === 'RUNNING'),
            labelKey: 'event.match.status.counter.running',
            color: 'primary',
        },
        {
            n: count(
                s =>
                    s.state === 'AWAITING_FINISH' ||
                    s.state === 'UPCOMING' ||
                    s.state === 'UNSCHEDULED',
            ),
            labelKey: 'event.match.status.counter.open',
            color: 'default',
        },
        {
            n: count(s => s.state === 'FINISHED'),
            labelKey: 'event.match.status.counter.finished',
            color: 'success',
        },
        {
            n: count(s => s.state === 'SKIPPED'),
            labelKey: 'event.match.status.counter.cancelled',
            color: 'default',
        },
    ]

    return buckets
        .filter(bucket => bucket.n > 0)
        .map(({n, labelKey, color}) => ({labelKey, values: {n}, color}))
}

/**
 * Der Lauf-Status eines Zeitplan-Slots, aus den Feldern, die die Slot-Abfrage ohnehin mitbringt.
 *
 * Der Zeitplan bekommt kein fertiges [MatchStatusDto] vom Server — er liefert eine Zeile je Slot,
 * nicht je Lauf. Die Zweig-Reihenfolge ist deshalb hier noch einmal nachgebildet, und zwar exakt
 * die von `LiveDashboardLogic.deriveMatchState`: aktiv schlägt beendet schlägt abgesagt schlägt
 * „alle gewertet". Ohne verknüpften Lauf gibt es nichts abzuleiten — dann bleibt der Slot bei
 * seinem eigenen Chip.
 *
 * UNSCHEDULED kann hier nicht entstehen: ein Slot hat immer eine Startzeit, sonst stünde er nicht
 * im Zeitplan.
 */
export const slotMatchStatus = (slot: EventScheduleSlotDto): MatchStatusDto | null => {
    if (!slot.matchId) return null

    const state = slot.matchCurrentlyRunning
        ? 'RUNNING'
        : slot.matchFinishedAt
          ? 'FINISHED'
          : slot.state === 'SKIPPED'
            ? 'SKIPPED'
            : slot.matchTeamsTotal > 0 && slot.matchTeamsScored >= slot.matchTeamsTotal
              ? 'AWAITING_FINISH'
              : 'UPCOMING'

    return {
        state,
        startedAt: slot.matchStartedAt ?? undefined,
        teamsTotal: slot.matchTeamsTotal,
        teamsScored: slot.matchTeamsScored,
    }
}
