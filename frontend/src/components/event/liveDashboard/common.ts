import {
    EffectiveSeverity,
    LiveDashboardCrewMemberDto,
    LiveDashboardMatchDto,
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

/** Die beiden Ansichten des Boards — schmal je eine, breit beide nebeneinander. */
export type LiveDashboardTab = 'live' | 'matches'

/**
 * Wie viel der Server liefern soll. Schmal entscheidet der Umschalter: der Live-Tab braucht nur die
 * laufenden Läufe (plus den nächsten), die vollständige Liste sieht sich dort niemand im
 * Sekundentakt an. Breit stehen beide Spalten gleichzeitig auf dem Schirm, also führt kein Weg an
 * der Gesamtliste vorbei.
 */
export const dashboardScope = (wide: boolean, tab: LiveDashboardTab): 'LIVE' | 'ALL' =>
    !wide && tab === 'live' ? 'LIVE' : 'ALL'

/**
 * Ab dieser Fensterbreite kann eine Karte breit genug werden, dass die Crew-Stufe überhaupt
 * erscheint — das Board steht dann auf einem Laptop, nicht auf einem Telefon oder einer
 * Tablet-Spalte.
 */
export const CREW_WINDOW_PX = 1440

/**
 * Ob der Abruf die Crew je Boot mitbestellt. Anders als die drei Anzeigestufen hängt das an der
 * *Fenster*breite und nicht an der Kartenbreite: die Nutzlast wird einmal je Abruf entschieden,
 * lange bevor eine Karte weiß, wie breit sie wird. Die Kartenbreite entscheidet danach nur noch,
 * ob die bereits geladene Crew auch gezeigt wird — großzügig zu laden ist billiger als eine
 * zweite Runde, sobald jemand das Fenster zieht.
 */
export const dashboardCrew = (windowWidth: number): boolean => windowWidth >= CREW_WINDOW_PX

/**
 * Ob die Karte überhaupt eine Crew-Zeile hat. Die Stufen selbst schaltet eine Container-Query, die
 * in jsdom nicht greift — diese eine Entscheidung hängt aber an den Daten und gehört deshalb
 * hierher: unmittelbar nach dem Verbreitern des Fensters ist die Karte schon breit genug, der
 * nächste Abruf mit `crew=true` aber noch unterwegs. Dann rendert die Karte Stufe 2 weiter, statt
 * eine leere Fläche aufzuziehen.
 */
export const teamShowsCrew = (team: Pick<LiveDashboardTeamDto, 'crew'>): boolean =>
    team.crew != null && team.crew.length > 0

/** Eine Person in der Crew-Zeile: `Meier · RC Bergedorf (Ste.)`, Rolle und Verein je optional. */
export const crewMemberLabel = (member: LiveDashboardCrewMemberDto): string => {
    const name = [member.lastName, member.clubShort].filter(Boolean).join(' · ')
    return member.role ? `${name} (${member.role})` : name
}

/**
 * Ob unter dem Mannschaftsnamen noch die Vereinskette steht. Trägt der Name den Verein bereits —
 * bei reinen Vereinsbooten der Regelfall —, stünde er sonst zweimal untereinander. Geprüft wird
 * gegen beide Fassungen der Kette, weil die Karte je nach Breite die eine oder die andere zeigt
 * und die Entscheidung nur einmal fallen kann.
 */
export const teamShowsClubLine = (
    team: Pick<LiveDashboardTeamDto, 'teamName' | 'clubsShort' | 'clubsFull'>,
): boolean =>
    team.teamName != null &&
    team.clubsFull !== '' &&
    !team.teamName.includes(team.clubsFull) &&
    !team.teamName.includes(team.clubsShort)

/**
 * DOM-Id der Karte eines Eintrags, geteilt zwischen den Render-Schleifen und dem
 * Klick-auf-den-Zeitstrahl. Breit steht ein laufender Lauf zweimal auf der Seite — links unter
 * "Live" und rechts in der Gesamtliste —, deshalb gehört die Spalte in die Id; sonst wären die Ids
 * doppelt und `getElementById` träfe die falsche Karte.
 */
export const dashboardEntryDomId = (id: string, column: 'live' | 'list'): string =>
    `live-dashboard-entry-${column}-${id}`

/**
 * Wohin der Klick auf den Zeitstrahl springt: bevorzugt in die Gesamtliste, ersatzweise in die
 * Live-Spalte. Breit ist die Live-Spalte ohnehin dauerhaft im Blick, dort zu scrollen brächte
 * nichts; schmal existiert je nach Tab nur eine der beiden Karten.
 */
export const dashboardEntryDomIdCandidates = (id: string): string[] => [
    dashboardEntryDomId(id, 'list'),
    dashboardEntryDomId(id, 'live'),
]

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

/**
 * Ein Boot ist erledigt, sobald Platz, Zeit oder ein Ausscheidungsgrund vorliegt. Abgemeldete
 * Boote sind es ebenfalls — auf ihr Ergebnis wartet niemand mehr.
 */
export const teamHasResult = (team: LiveDashboardTeamDto): boolean =>
    team.deregistered || team.failed || team.place != null || team.time != null

/** Die Boote, für die beim Beenden eines Laufs noch zu entscheiden ist. */
export const openResultTeams = (match: {teams: LiveDashboardTeamDto[]}): LiveDashboardTeamDto[] =>
    match.teams.filter(team => !teamHasResult(team))

export const severityChipColor: Record<
    EffectiveSeverity,
    'success' | 'warning' | 'error' | 'default'
> = {
    OK: 'success',
    WARNING: 'warning',
    CRITICAL: 'error',
    NEUTRAL: 'default',
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
    const stillUpcoming = pendingSlots.filter(
        slot => new Date(slot.startTime).getTime() > threshold,
    )
    return buildLiveDashboardTimeline(
        nextUpcomingMatch ? [nextUpcomingMatch] : [],
        stillUpcoming,
    )[0]
}

/**
 * Rennnummer und Kurzname eines Wettkampfs, z. B. "17 CM 4x+" — dieselbe Zusammensetzung wie
 * `competitionTag` im Zeitplan-Tab, hier für die DTOs des Boards. Leer, wo beides fehlt: bei
 * Programmpunkten und bei Wettkämpfen ohne gepflegten Kurznamen und ohne Nummer.
 */
export const competitionTag = (competition: {
    competitionIdentifier?: string | null
    competitionShortName?: string | null
}): string =>
    [competition.competitionIdentifier, competition.competitionShortName].filter(v => v).join(' ')

/**
 * Wie ein Lauf auf der Karte benannt wird: in der Langform der ausgeschriebene Wettkampfname,
 * in der Kurzform ("17 CM 4x+") das Kürzel. Ohne Kürzel bleibt es beim Namen — eine Karte ohne
 * jede Angabe zum Rennen wäre auf dem Board unbrauchbar.
 */
export const competitionLabel = (
    competition: {
        competitionName?: string | null
        competitionIdentifier?: string | null
        competitionShortName?: string | null
    },
    mode: 'full' | 'short' = 'full',
): string | null | undefined =>
    mode === 'short' && competitionTag(competition)
        ? competitionTag(competition)
        : competition.competitionName

/**
 * Anzeige-Label eines Platzhalters — für Programmpunkte (FREE, `name` gesetzt) schlicht der Name,
 * für wartende Lauf-Slots dieselbe Zusammensetzung wie slotLabel im Zeitplan-Tab.
 */
export const pendingSlotLabel = (slot: PendingSlotDto, mode: 'full' | 'short' = 'full'): string =>
    slot.name ??
    [competitionLabel(slot, mode), slot.roundName, slot.matchName].filter(Boolean).join(' · ')
