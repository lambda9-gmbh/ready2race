import {
    LiveDashboardParticipantDto,
    LiveDashboardRequirementStatusDto,
    LiveDashboardTeamDto,
} from '@api/types.gen.ts'

export type Severity = 'ok' | 'warning' | 'error' | 'neutral'

const rank: Record<Severity, number> = {neutral: 0, ok: 1, warning: 2, error: 3}

export const worstSeverity = (severities: Severity[]): Severity =>
    severities.reduce<Severity>((acc, s) => (rank[s] > rank[acc] ? s : acc), 'neutral')

export const requirementSeverity = (r: LiveDashboardRequirementStatusDto): Severity => {
    if (!r.checked) {
        return r.optional ? 'neutral' : 'error'
    }
    if (r.timeCheck && (r.timeCheck.status === 'LATE' || r.timeCheck.status === 'TOO_EARLY')) {
        return 'warning'
    }
    return 'ok'
}

export const participantSeverity = (p: LiveDashboardParticipantDto): Severity =>
    worstSeverity(p.requirements.map(requirementSeverity))

export const teamSeverity = (team: LiveDashboardTeamDto): Severity =>
    worstSeverity([
        ...team.participants.map(participantSeverity),
        team.invoiceState === 'OPEN' ? 'error' : 'neutral',
    ])

export const severityChipColor: Record<Severity, 'success' | 'warning' | 'error' | 'default'> = {
    ok: 'success',
    warning: 'warning',
    error: 'error',
    neutral: 'default',
}

export const formatMinutes = (totalMinutes: number): string => {
    const abs = Math.abs(totalMinutes)
    const h = Math.floor(abs / 60)
    const m = abs % 60
    return h > 0 ? `${h} h ${m} min` : `${m} min`
}

export const POLL_INTERVAL_OPTIONS_MS = [5_000, 10_000, 30_000, 60_000] as const
export const POLL_INTERVAL_STORAGE_KEY = 'live_dashboard_poll_interval'
const DEFAULT_POLL_INTERVAL_MS = 10_000

/** Refresh rate the referee picked on this device, falling back to the default. */
export const storedPollInterval = (): number => {
    const stored = Number(localStorage.getItem(POLL_INTERVAL_STORAGE_KEY))
    return POLL_INTERVAL_OPTIONS_MS.some(o => o === stored) ? stored : DEFAULT_POLL_INTERVAL_MS
}
