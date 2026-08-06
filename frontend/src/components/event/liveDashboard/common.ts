import {
    LiveDashboardMatchDto,
    LiveDashboardParticipantDto,
    LiveDashboardRequirementStatusDto,
    LiveDashboardTeamDto,
    PendingSlotDto,
} from '@api/types.gen.ts'

/**
 * Die Läufe, die im Live-Tab stehen: die aktiven und die, die auf ihr Beenden warten. Gegenstück
 * zu `LiveDashboardLogic.selectForScope(LIVE)` im Backend — dort entscheidet dieselbe Regel, was
 * der Server im Live-Ausschnitt überhaupt ausliefert.
 *
 * AWAITING_FINISH gehört dazu, weil der Lauf sonst genau dort fehlte, wo jemand handeln muss:
 * alle Boote sind gewertet, aber niemand hat beendet.
 */
export const isLiveMatch = (match: LiveDashboardMatchDto): boolean =>
    match.state === 'RUNNING' || match.state === 'AWAITING_FINISH'

export const liveMatches = (matches: LiveDashboardMatchDto[]): LiveDashboardMatchDto[] =>
    matches.filter(isLiveMatch)

/**
 * Welche Knöpfe die Karte anbietet — die Entscheidung liegt hier statt im JSX, damit sie ohne
 * Rendering prüfbar bleibt. `mayFinish`/`mayControl` sind die Rechte der Nutzerin.
 *
 * - Beendet wird nur durch aktiven Input (Entscheidung vom 04.08.2026), also solange der Lauf
 *   läuft ODER vollständig gewertet auf genau diesen Klick wartet.
 * - Bei AWAITING_FINISH tritt "Lauf beenden" an die Stelle von "Lauf aktivieren": das ist die
 *   Handlung, auf die alles wartet — ein Aktivieren würde den fertigen Lauf zurückwerfen.
 * - Ein abgesagter Lauf bietet gar nichts an: aktiviert wäre er abgesagt UND laufend zugleich,
 *   und beenden muss ihn niemand.
 */
export const matchControls = (
    match: LiveDashboardMatchDto,
    mayFinish: boolean,
    mayControl: boolean,
): {showFinish: boolean; showRunToggle: boolean} => {
    if (match.state === 'SKIPPED') {
        return {showFinish: false, showRunToggle: false}
    }
    return {
        showFinish: mayFinish && isLiveMatch(match),
        showRunToggle: mayControl && match.state !== 'AWAITING_FINISH',
    }
}

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

/**
 * Dieselbe Bewertung wie [requirementSeverity], nur aus den verdichteten Zahlen der Liste: die
 * Bedingungen selbst kommen erst mit dem Detail-Dialog.
 */
export const teamSeverity = (team: LiveDashboardTeamDto): Severity =>
    worstSeverity([
        team.requirements.missingRequired > 0 ? 'error' : 'neutral',
        team.requirements.timeIssues > 0 ? 'warning' : 'neutral',
        team.requirements.fulfilled > 0 ? 'ok' : 'neutral',
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

/**
 * Ein Eintrag im Referee-Dashboard: entweder ein wirklicher Lauf oder ein wartender Zeitplan-Slot,
 * dessen Runde noch nicht gesetzt ist (kein Match, keine Teams). Beide teilen sich eine Startzeit,
 * nach der die Anzeige sortiert.
 */
export type LiveDashboardTimelineEntry =
    | {kind: 'match'; match: LiveDashboardMatchDto}
    | {kind: 'pending'; slot: PendingSlotDto}

const timelineEntryStartTime = (entry: LiveDashboardTimelineEntry): string | null | undefined =>
    entry.kind === 'match' ? entry.match.startTime : entry.slot.startTime

/**
 * Läufe und wartende Slots gemeinsam nach Startzeit sortiert — ein Platzhalter reiht sich damit
 * genau dort ein, wo die noch nicht gesetzte Runde tatsächlich stattfindet. Einträge ohne
 * Startzeit (unplanmäßige Läufe) fallen ans Ende, statt die Sortierung zu stören.
 */
export const buildLiveDashboardTimeline = (
    matches: LiveDashboardMatchDto[],
    pendingSlots: PendingSlotDto[],
): LiveDashboardTimelineEntry[] =>
    [
        ...matches.map(match => ({kind: 'match' as const, match})),
        ...pendingSlots.map(slot => ({kind: 'pending' as const, slot})),
    ].sort((a, b) => {
        const timeA = timelineEntryStartTime(a)
        const timeB = timelineEntryStartTime(b)
        if (timeA == null) return timeB == null ? 0 : 1
        if (timeB == null) return -1
        return timeA.localeCompare(timeB)
    })

/**
 * Nachfrist, nach der ein Zeitplan-Platzhalter nicht mehr als "als Nächstes" gilt — dieselben 30
 * Minuten wie `AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES` im Backend.
 */
export const NEXT_UP_GRACE_MINUTES = 30

/**
 * Der Eintrag für "Als Nächstes" im Live-Tab: der chronologisch erste aus dem nächsten echten Lauf
 * und den wartenden Slots — Platzhalter allerdings nur, solange ihre Startzeit höchstens
 * [NEXT_UP_GRACE_MINUTES] zurückliegt. Sonst bliebe die Morgenbesprechung bis zum Abend als
 * "als Nächstes" stehen und verdeckte den Lauf, der wirklich ansteht.
 *
 * Bewusst nur auf Platzhalter angewandt, nicht auf den echten Lauf: Im Live-Tab (scope=LIVE)
 * liefert der Server ohnehin nur den einen nächsten anstehenden Lauf, ein Filter darüber ließe die
 * Karte leer statt den nächsten Lauf zu zeigen. Und ein überfälliger echter Lauf ist genau das, was
 * der Schiedsrichter noch starten muss. Die vollständige Liste im "Läufe"-Tab bleibt unberührt —
 * dort lässt sich ein überfälliger Slot weiterhin absagen oder setzen.
 *
 * [now] kommt von außen, damit die Auswahl ohne Uhr prüfbar bleibt.
 */
export const nextUpEntry = (
    nextUpcomingMatch: LiveDashboardMatchDto | undefined,
    pendingSlots: PendingSlotDto[],
    now: Date,
): LiveDashboardTimelineEntry | undefined => {
    const threshold = now.getTime() - NEXT_UP_GRACE_MINUTES * 60_000
    const stillUpcoming = pendingSlots.filter(slot => new Date(slot.startTime).getTime() > threshold)
    return buildLiveDashboardTimeline(nextUpcomingMatch ? [nextUpcomingMatch] : [], stillUpcoming)[0]
}

/**
 * Anzeige-Label eines Platzhalters — für Programmpunkte (FREE, `name` gesetzt) schlicht der Name,
 * für wartende Lauf-Slots dieselbe Zusammensetzung wie slotLabel im Zeitplan-Tab.
 */
export const pendingSlotLabel = (slot: PendingSlotDto): string =>
    slot.name ?? [slot.competitionName, slot.roundName, slot.matchName].filter(Boolean).join(' · ')
