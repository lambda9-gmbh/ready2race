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

/**
 * Ein Boot ist erledigt, sobald Platz, Zeit oder ein Ausscheidungsgrund vorliegt. Abgemeldete
 * Boote sind es ebenfalls — auf ihr Ergebnis wartet niemand mehr.
 */
export const teamHasResult = (team: LiveDashboardTeamDto): boolean =>
    team.deregistered || team.failed || team.place != null || team.time != null

/** Die Boote, für die beim Beenden eines Laufs noch zu entscheiden ist. */
export const openResultTeams = (match: {teams: LiveDashboardTeamDto[]}): LiveDashboardTeamDto[] =>
    match.teams.filter(team => !teamHasResult(team))

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

const CLUB_NAME_BALLAST = [
    /\s*\be\.?\s?V\.?(?=\s|$)/gi, // Rechtsform "e.V." / "eV"
    /\s*\([^)]*\d[^)]*\)/g, // Gründungsjahre in Klammern, z.B. "(1879/83)"
    /\s*\bvon\s+\d{4}\b/gi, // "von 1889"
    /\s+\d{4}\b/g, // nachgestellte Jahreszahl, z.B. "München 1972"
]

// Im Rudersport gängige Kürzel — Schiedsrichter lesen sie ohne Nachdenken.
const CLUB_TYPE_ABBREVIATIONS: [RegExp, string][] = [
    [/\bRudergesellschaft\b/gi, 'RG'],
    [/\bRuder-?vereinigung\b/gi, 'RVg'],
    [/\bRuder-?verein\b/gi, 'RV'],
    [/\bRuder-?club\b/gi, 'RC'],
    [/\bRuder-?klub\b/gi, 'RK'],
    [/\bSegel-?verein\b/gi, 'SV'],
    [/\bSegel-?club\b/gi, 'SC'],
    [/\bSportvereinigung\b/gi, 'SVg'],
    [/\bSportverein\b/gi, 'SV'],
    [/\bTurnverein\b/gi, 'TV'],
    [/\bAkademischer\b/gi, 'Akad.'],
]

/**
 * Kurzform eines Vereinsnamens für die Listenansicht: Rechtsform und
 * Gründungsjahre entfallen, gängige Vereinstypen werden abgekürzt.
 * Der vollständige Name bleibt im Detail-Dialog sichtbar.
 */
export const shortClubName = (name: string): string => {
    const withoutBallast = CLUB_NAME_BALLAST.reduce(
        (acc, pattern) => acc.replace(pattern, ' '),
        name,
    )
    const abbreviated = CLUB_TYPE_ABBREVIATIONS.reduce(
        (acc, [pattern, replacement]) => acc.replace(pattern, replacement),
        withoutBallast,
    )
    return abbreviated.replace(/\s{2,}/g, ' ').replace(/\s+,/g, ',').trim()
}
