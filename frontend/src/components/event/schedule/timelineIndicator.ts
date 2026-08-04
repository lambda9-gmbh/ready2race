import {format} from 'date-fns'
import {EventScheduleSlotDto, LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {slotLabel} from './common.ts'
import {pendingSlotLabel} from '@components/event/liveDashboard/common.ts'

/**
 * Generic, display-only state a timeline segment can be in - independent of whether it came from
 * an {@link EventScheduleSlotDto} (Zeitplan tab) or a live-dashboard match/pending slot (Referee
 * dashboard). Both call sites map their own richer state onto this small set so the indicator
 * component and its color/label mapping only need to know about one shape.
 */
export type TimelineEntryState = 'finished' | 'running' | 'linked' | 'waiting' | 'free' | 'skipped'

export type TimelineEntry = {
    id: string
    startTime: string
    state: TimelineEntryState
    label: string
    durationMinutes?: number | null
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
 */
export const scheduleSlotState = (slot: EventScheduleSlotDto): TimelineEntryState => {
    if (slot.matchFinishedAt) {
        return 'finished'
    }
    if (slot.matchStartedAt) {
        return 'running'
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
    }))

// ---- Referee dashboard: LiveDashboardMatchDto + PendingSlotDto --------------------------------

export const dashboardMatchState = (match: LiveDashboardMatchDto): TimelineEntryState => {
    switch (match.state) {
        case 'RUNNING':
            return 'running'
        case 'FINISHED':
            return 'finished'
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
 * Which day the dashboard indicator should show: the day of the first running match if one is
 * live, else the day of the chronologically next entry (upcoming match or pending slot), else
 * today - mirrors the "als Nächstes" fallback already used for the single-entry preview.
 */
export const resolveDashboardDay = (
    matches: LiveDashboardMatchDto[],
    pendingSlots: PendingSlotDto[],
    now: Date,
): string => {
    const running = matches.find(m => m.state === 'RUNNING' && m.startTime != null)
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

// ---- Position math -------------------------------------------------------------------------

const MIN_WIDTH_PERCENT = 3
const MIN_SPAN_MINUTES = 30

/**
 * Time-proportional x-position for every entry along the day's span (first entry start to last
 * entry end, at least MIN_SPAN_MINUTES wide so a single entry or a day with only one distinct
 * time doesn't degenerate to a division by zero). Entries get a minimum width so short/instant
 * slots stay clickable, and entries sharing the exact same start time stack into separate rows
 * (via `stackRow`) instead of drawing on top of each other.
 */
export const computeTimelinePositions = (entries: TimelineEntry[]): PositionedTimelineEntry[] => {
    if (entries.length === 0) {
        return []
    }
    const sorted = [...entries].sort((a, b) => a.startTime.localeCompare(b.startTime))
    const starts = sorted.map(e => new Date(e.startTime).getTime())
    const ends = sorted.map((e, i) => starts[i] + (e.durationMinutes ?? 0) * 60_000)
    const minStart = Math.min(...starts)
    const maxEnd = Math.max(...ends, ...starts)
    const spanMs = Math.max(maxEnd - minStart, MIN_SPAN_MINUTES * 60_000)

    const stackRowByStart = new Map<number, number>()

    return sorted.map((entry, i) => {
        const start = starts[i]
        const durationMs = (entry.durationMinutes ?? 0) * 60_000
        const leftPercent = ((start - minStart) / spanMs) * 100
        const widthPercent = Math.min(
            Math.max((durationMs / spanMs) * 100, MIN_WIDTH_PERCENT),
            100 - leftPercent,
        )
        const stackRow = stackRowByStart.get(start) ?? 0
        stackRowByStart.set(start, stackRow + 1)
        return {...entry, leftPercent, widthPercent, stackRow}
    })
}

/**
 * Position of the now-marker on the same axis as computeTimelinePositions, clamped to [0, 100] so
 * "now" before the first entry or after the last still shows at the respective edge instead of
 * disappearing off the bar. Null when there is nothing to position it against.
 */
export const computeNowMarkerPercent = (entries: TimelineEntry[], now: Date): number | null => {
    if (entries.length === 0) {
        return null
    }
    const starts = entries.map(e => new Date(e.startTime).getTime())
    const ends = entries.map((e, i) => starts[i] + (e.durationMinutes ?? 0) * 60_000)
    const minStart = Math.min(...starts)
    const maxEnd = Math.max(...ends, ...starts)
    const spanMs = Math.max(maxEnd - minStart, MIN_SPAN_MINUTES * 60_000)
    const percent = ((now.getTime() - minStart) / spanMs) * 100
    return Math.min(100, Math.max(0, percent))
}
