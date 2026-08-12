import {format} from 'date-fns'
import {EventScheduleSlotDto, LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {slotLabel} from './common.ts'
import {isLiveMatch, pendingSlotLabel} from '@components/event/liveDashboard/common.ts'
import {ChipColor} from '@components/event/match/matchStatusChip.ts'

/**
 * Generic, display-only state a timeline segment can be in - independent of whether it came from
 * an {@link EventScheduleSlotDto} (Zeitplan tab) or a live-dashboard match/pending slot (Referee
 * dashboard). Both call sites map their own richer state onto this small set so the indicator
 * component and its color/label mapping only need to know about one shape.
 */
export type TimelineEntryState =
    | 'finished'
    // An den Start gerufen, aber noch nicht unterwegs — eigener Wert und nicht 'running', weil der
    // Balken sonst dasselbe behauptete wie bei einem fahrenden Lauf (und mitpulsierte).
    | 'preparing'
    | 'running'
    | 'awaitingFinish'
    | 'linked'
    | 'waiting'
    | 'free'
    | 'skipped'

export type TimelineEntry = {
    id: string
    startTime: string
    state: TimelineEntryState
    label: string
    durationMinutes?: number | null
    /**
     * Freilos, das nicht gefahren wird (`mustRace` gefahrene Freilose zählen NICHT — sie sind
     * echte Läufe, siehe matchStatusChip): bekommt auf dem Balken die Schraffur, weil hier
     * niemand auf ein Ergebnis wartet.
     */
    bye?: boolean
}

export type PositionedTimelineEntry = TimelineEntry & {
    leftPercent: number
    widthPercent: number
    stackRow: number
}

/** Calendar day (YYYY-MM-DD) of a naive local timestamp, matching groupSlotsByDay's convention. */
export const dayOf = (isoLikeTime: string): string => isoLikeTime.slice(0, 10)

// ---- Zeitplan tab: EventScheduleSlotDto -------------------------------------------------------

/**
 * Same precedence as the state chip in EventSchedule.tsx: a slot with a recorded finish counts as
 * finished regardless of its raw `state`, a started-but-not-finished slot is running, and OBSOLETE
 * behaves like SKIPPED for the indicator (both are "won't happen", just for different reasons).
 *
 * Kein 'awaitingFinish' hier: EventScheduleSlotDto trägt nur Start- und Endzeitpunkt des Laufs,
 * nicht die Vollständigkeit seiner Ergebnisse - diese Unterscheidung kann nur das Dashboard treffen
 * (siehe dashboardMatchState).
 */
export const scheduleSlotState = (slot: EventScheduleSlotDto): TimelineEntryState => {
    if (slot.matchFinishedAt) {
        return 'finished'
    }
    if (slot.matchStartedAt) {
        return 'running'
    }
    // Vor den Slot-Zuständen, aber nach dem Ist-Start: aktiviert und noch nicht losgefahren ist
    // "in Vorbereitung" — dieselbe Unterscheidung wie `slotMatchStatus` sie für den Chip trifft.
    if (slot.matchActivatedAt) {
        return 'preparing'
    }
    switch (slot.state) {
        case 'WAITING':
            return 'waiting'
        case 'LINKED':
            return 'linked'
        case 'SKIPPED':
        case 'OBSOLETE':
            return 'skipped'
        case 'FREE':
        default:
            return 'free'
    }
}

export const scheduleSlotsToEntries = (slots: EventScheduleSlotDto[]): TimelineEntry[] =>
    slots.map(slot => ({
        id: slot.id,
        startTime: slot.startTime,
        state: scheduleSlotState(slot),
        label: slotLabel(slot),
        durationMinutes: slot.durationMinutes,
        bye: slot.bye != null && !slot.bye.mustRace,
    }))

// ---- Referee dashboard: LiveDashboardMatchDto + PendingSlotDto --------------------------------

export const dashboardMatchState = (match: LiveDashboardMatchDto): TimelineEntryState => {
    switch (match.state) {
        case 'PREPARING':
            return 'preparing'
        case 'RUNNING':
            return 'running'
        case 'FINISHED':
            return 'finished'
        // Eigenes Aussehen, kein "beendet": der Lauf ist gewertet, aber niemand hat ihn beendet -
        // auf dem Balken soll genau das ins Auge fallen, weil dort noch eine Handlung aussteht.
        case 'AWAITING_FINISH':
            return 'awaitingFinish'
        // Same look as a cancelled slot in the Zeitplan tab: struck through and dimmed, not hidden.
        case 'SKIPPED':
            return 'skipped'
        case 'UPCOMING':
        case 'UNSCHEDULED':
        default:
            return 'linked'
    }
}

/**
 * A PendingSlotDto is either a waiting match slot (round not yet materialized) or a FREE
 * program item - `name` distinguishes the two, exactly as in pendingSlotLabel/common.ts.
 */
export const pendingSlotState = (slot: PendingSlotDto): TimelineEntryState =>
    slot.name != null ? 'free' : 'waiting'

/**
 * Entries for one day of the referee dashboard: matches and pending slots merged and sorted by
 * start time. Entries without a start time (unscheduled matches) are dropped - the indicator only
 * shows "where are we on the axis", which is meaningless without a position on it.
 */
export const dashboardEntriesForDay = (
    matches: LiveDashboardMatchDto[],
    pendingSlots: PendingSlotDto[],
    day: string,
): TimelineEntry[] => {
    const matchEntries: TimelineEntry[] = matches
        .filter((m): m is LiveDashboardMatchDto & {startTime: string} => m.startTime != null)
        .filter(m => dayOf(m.startTime) === day)
        .map(m => ({
            id: m.matchId,
            startTime: m.startTime,
            state: dashboardMatchState(m),
            label: m.matchName ?? m.roundName ?? m.competitionName,
            bye: m.bye != null && !m.bye.mustRace,
        }))
    const slotEntries: TimelineEntry[] = pendingSlots
        .filter(s => dayOf(s.startTime) === day)
        .map(s => ({
            id: s.slotId,
            startTime: s.startTime,
            state: pendingSlotState(s),
            label: pendingSlotLabel(s),
        }))
    return [...matchEntries, ...slotEntries].sort((a, b) => a.startTime.localeCompare(b.startTime))
}

/**
 * Which day the dashboard indicator should show: the day of the first match that is live in the
 * sense of the Live tab (running, or fully scored and waiting to be finished - see `isLiveMatch`),
 * else the day of the chronologically next entry (upcoming match or pending slot), else today -
 * mirrors the "als Nächstes" fallback already used for the single-entry preview.
 */
export const resolveDashboardDay = (
    matches: LiveDashboardMatchDto[],
    pendingSlots: PendingSlotDto[],
    now: Date,
): string => {
    const running = matches.find(m => isLiveMatch(m) && m.startTime != null)
    if (running?.startTime) {
        return dayOf(running.startTime)
    }
    const upcoming = [
        ...matches
            .filter(m => m.state === 'UPCOMING' && m.startTime != null)
            .map(m => m.startTime as string),
        ...pendingSlots.map(s => s.startTime),
    ].sort((a, b) => a.localeCompare(b))[0]
    if (upcoming) {
        return dayOf(upcoming)
    }
    return format(now, 'yyyy-MM-dd')
}

// ---- Aussehen der Segmente ----------------------------------------------------------------------

/**
 * Wie ein Segment gezeichnet wird — als Datensatz statt als Farbe, nach demselben Muster wie
 * {@link MatchChip}: die Komponente übersetzt [color] über die Theme-Palette und malt. So trägt
 * der Balken exakt dieselbe Farbsemantik wie die Status-Chips daneben, auf beiden Flächen
 * (Zeitplan-Tab und Schiedsrichter-Dashboard rendern dieselbe Komponente).
 */
export type TimelineAppearance = {
    /** Farbfamilie mit der Bedeutung der Status-Chips (matchStatusChip): primary = läuft usw. */
    color: ChipColor
    /** Anstehendes wird als Umriss gezeichnet, Geschehenes/Geschehendes gefüllt. */
    variant: 'filled' | 'outlined'
    /** Gestrichelter Umriss: der Lauf ist noch gar nicht gesetzt (Runde nicht materialisiert). */
    dashed: boolean
    /** Gedämpfte Füllung: erledigt bzw. entfallen — Vergangenes soll nicht mehr leuchten. */
    muted: boolean
    /** Nur „Abgesagt/Übersprungen": bleibt sichtbar, gilt aber nicht mehr (wie beim Chip). */
    strikeThrough: boolean
    /** Schraffur: hier fährt niemand — Programmpunkte und (nicht gefahrene) Freilose. */
    hatched: boolean
}

const appearance = (over: Partial<TimelineAppearance>): TimelineAppearance => ({
    color: 'default',
    variant: 'filled',
    dashed: false,
    muted: false,
    strikeThrough: false,
    hatched: false,
    ...over,
})

/**
 * Die Farb-/Muster-Entscheidung für ein Segment, deckungsgleich mit `matchStatusChip`:
 * beendet = success (gedämpft), läuft = primary, in Vorbereitung = info, wartet auf Beenden =
 * warning, anstehend = Umriss, abgesagt = durchgestrichen. Freilos und Programmpunkt tragen
 * zusätzlich die Schraffur — dort wartet niemand auf ein Ergebnis.
 *
 * Die Freilos-Vorrangregel ist ebenfalls die des Chips: Was tatsächlich passiert, schlägt das
 * Freilos — ein aktiviertes/fahrendes Freilos sieht aus wie jeder andere Lauf. Ein quittiertes
 * bleibt gedämpft-grün (wie „Freilos · quittiert"), ein entfallenes durchgestrichen, ein offenes
 * info-blau (wie „Freilos · offen").
 */
export const timelineEntryAppearance = (
    state: TimelineEntryState,
    bye = false,
): TimelineAppearance => {
    if (bye && state !== 'running' && state !== 'preparing') {
        if (state === 'finished') {
            return appearance({color: 'success', muted: true, hatched: true})
        }
        if (state === 'skipped') {
            return appearance({muted: true, strikeThrough: true, hatched: true})
        }
        return appearance({color: 'info', hatched: true})
    }
    switch (state) {
        case 'finished':
            return appearance({color: 'success', muted: true})
        case 'running':
            return appearance({color: 'primary'})
        case 'preparing':
            return appearance({color: 'info'})
        case 'awaitingFinish':
            return appearance({color: 'warning'})
        case 'waiting':
            return appearance({variant: 'outlined', dashed: true})
        case 'linked':
            return appearance({variant: 'outlined'})
        case 'skipped':
            return appearance({muted: true, strikeThrough: true})
        case 'free':
        default:
            return appearance({muted: true, hatched: true})
    }
}

// ---- Positionsrechnung ------------------------------------------------------------------------

/**
 * Mindestbreite eines Segments in Prozent der Achse. Der bewusste Kompromiss: Ein 4-Minuten-Lauf
 * auf einem 12-Stunden-Tag wäre zeitgetreu ~0,5 % breit — ein unklickbarer Strich. Die
 * Mindestbreite opfert an dieser einen Stelle die Zeittreue zugunsten der Bedienbarkeit; dicht
 * gestartete Kurzläufe werden dadurch breiter gezeichnet, als sie dauern, und weichen einander
 * über die Spurzuteilung (siehe `stackRow`) aus, statt sich zu überdecken.
 */
const MIN_WIDTH_PERCENT = 3

const HOUR_MS = 3_600_000

/** Volle Stunde vor [ms] — über die Date-API statt Modulo, damit auch halbstündige Zeitzonen stimmen. */
const floorToHour = (ms: number): number => {
    const d = new Date(ms)
    d.setMinutes(0, 0, 0)
    return d.getTime()
}

export type TimelineSpan = {startMs: number; endMs: number}

/**
 * Die Achse des Tages: von der vollen Stunde vor dem ersten Start bis zur vollen Stunde nach dem
 * letzten Ende. Auf volle Stunden gerundet, damit die Stundenmarken die Achse an beiden Enden
 * abschließen statt irgendwo im Nichts zu beginnen; mindestens eine Stunde breit, damit ein Tag
 * mit einem einzigen (oder dauerlosen) Eintrag nicht zur Division durch null entartet.
 */
export const timelineSpan = (entries: TimelineEntry[]): TimelineSpan | null => {
    if (entries.length === 0) {
        return null
    }
    const starts = entries.map(e => new Date(e.startTime).getTime())
    const ends = entries.map((e, i) => starts[i] + (e.durationMinutes ?? 0) * 60_000)
    const startMs = floorToHour(Math.min(...starts))
    const lastEnd = Math.max(...ends, ...starts)
    const endMs = Math.max(
        floorToHour(lastEnd) === lastEnd ? lastEnd : floorToHour(lastEnd) + HOUR_MS,
        startMs + HOUR_MS,
    )
    return {startMs, endMs}
}

export type HourMark = {percent: number; timeMs: number}

/**
 * Die beschrifteten Stundenmarken entlang der Achse. Die Schrittweite wächst mit der Spanne
 * (1 h, 2 h, 3 h, …), sodass höchstens ~10 Marken entstehen — mehr Beschriftung liefe auf
 * schmalen Flächen ineinander und sagte nichts, was die feineren Marken nicht auch sagen.
 */
export const computeHourMarks = (entries: TimelineEntry[]): HourMark[] => {
    const span = timelineSpan(entries)
    if (span == null) {
        return []
    }
    const spanMs = span.endMs - span.startMs
    const hours = spanMs / HOUR_MS
    const step = [1, 2, 3, 4, 6, 12].find(s => hours / s <= 10) ?? 24
    const marks: HourMark[] = []
    for (let timeMs = span.startMs; timeMs <= span.endMs; timeMs += step * HOUR_MS) {
        marks.push({percent: ((timeMs - span.startMs) / spanMs) * 100, timeMs})
    }
    return marks
}

/**
 * Zeitproportionale x-Position für jeden Eintrag entlang der Tagesachse (siehe {@link timelineSpan}).
 * Einträge bekommen eine Mindestbreite, damit kurze/dauerlose Slots klickbar bleiben (Kompromiss
 * siehe MIN_WIDTH_PERCENT), und Einträge, die sich GEZEICHNET überschneiden würden — gleiche
 * Startzeit, überlappende Dauern oder bloß dichter gestartet, als die Mindestbreite Platz lässt —
 * weichen in die erste freie Spur aus (`stackRow`, First-Fit) statt übereinander zu liegen.
 */
export const computeTimelinePositions = (entries: TimelineEntry[]): PositionedTimelineEntry[] => {
    const span = timelineSpan(entries)
    if (span == null) {
        return []
    }
    const spanMs = span.endMs - span.startMs
    const sorted = [...entries].sort((a, b) => a.startTime.localeCompare(b.startTime))

    // Rechte Kante (in Prozent) des jeweils letzten Eintrags je Spur — First-Fit reicht, weil die
    // Einträge nach Startzeit sortiert ankommen und Spuren damit nie rückwärts belegt werden.
    const laneRightEdges: number[] = []

    return sorted.map(entry => {
        const start = new Date(entry.startTime).getTime()
        const durationMs = (entry.durationMinutes ?? 0) * 60_000
        const leftPercent = ((start - span.startMs) / spanMs) * 100
        const widthPercent = Math.min(
            Math.max((durationMs / spanMs) * 100, MIN_WIDTH_PERCENT),
            100 - leftPercent,
        )
        let stackRow = laneRightEdges.findIndex(rightEdge => rightEdge <= leftPercent + 1e-6)
        if (stackRow === -1) {
            stackRow = laneRightEdges.length
            laneRightEdges.push(0)
        }
        laneRightEdges[stackRow] = leftPercent + widthPercent
        return {...entry, leftPercent, widthPercent, stackRow}
    })
}

/**
 * Position des Jetzt-Markers auf derselben Achse wie computeTimelinePositions, auf [0, 100]
 * geklemmt: "jetzt" vor der ersten vollen Stunde oder nach der letzten klebt am jeweiligen Rand,
 * statt von der Fläche zu verschwinden. Null, wenn es nichts zu positionieren gibt — oder wenn
 * die Einträge zu einem ANDEREN Kalendertag gehören: Wer den Zeitplan von übermorgen ansieht,
 * für den wäre ein an den Rand geklemmtes "Jetzt" eine Falschaussage.
 */
export const computeNowMarkerPercent = (entries: TimelineEntry[], now: Date): number | null => {
    const span = timelineSpan(entries)
    if (span == null) {
        return null
    }
    if (dayOf(entries[0].startTime) !== format(now, 'yyyy-MM-dd')) {
        return null
    }
    const percent = ((now.getTime() - span.startMs) / (span.endMs - span.startMs)) * 100
    return Math.min(100, Math.max(0, percent))
}
