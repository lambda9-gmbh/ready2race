import {
    EffectiveSeverity,
    LiveDashboardCrewMemberDto,
    LiveDashboardMatchDto,
    LiveDashboardTeamDto,
    MatchStatusDto,
    MatchTeamNoteDto,
    PendingSlotDto,
} from '@api/types.gen.ts'

/**
 * Die Läufe, die im Live-Tab stehen: die aktiven und die, die auf ihr Beenden warten. Gegenstück
 * zu `LiveDashboardLogic.selectForScope(LIVE)` im Backend — dort entscheidet dieselbe Regel, was
 * der Server im Live-Ausschnitt überhaupt ausliefert.
 *
 * AWAITING_FINISH gehört dazu, weil der Lauf sonst genau dort fehlte, wo jemand handeln muss:
 * alle Boote sind gewertet, aber niemand hat beendet.
 *
 * PREPARING ebenso: ein Lauf, der an den Start gerufen ist, liegt im Zugriff des Schiedsrichters —
 * er ist der nächste, der losgeht, und der Knopf „Läuft" sitzt auf seiner Karte.
 */
export const isLiveMatch = (match: LiveDashboardMatchDto): boolean =>
    match.state === 'PREPARING' || match.state === 'RUNNING' || match.state === 'AWAITING_FINISH'

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

/**
 * Anzahl der Schiedsrichter-Notizen eines Boots - der Marker auf der Karte erscheint nur, wenn es
 * welche gibt. `notes` ist im generierten Typ optional, ältere Server-Antworten lassen es weg.
 */
export const teamNoteCount = (team: Pick<LiveDashboardTeamDto, 'notes'>): number =>
    (team.notes ?? []).length

/**
 * Ob ein Notiz-Text abgeschickt werden kann - Leerraum zählt nicht. Dieselbe Regel wie im Backend
 * (Validator `notBlank`, DB-Check `btrim(note) <> ''`): der Knopf soll gar nicht erst anbieten,
 * was der Server ohnehin ablehnt.
 */
export const canSubmitNote = (text: string): boolean => text.trim() !== ''

/** Eine Person in der Crew-Zeile: `Meier · RC Bergedorf (Ste.)`, Rolle und Verein je optional. */
export const crewMemberLabel = (member: LiveDashboardCrewMemberDto): string => {
    const name = [member.lastName, member.clubShort].filter(Boolean).join(' · ')
    return member.role ? `${name} (${member.role})` : name
}

/**
 * Ob die Zeile eine Vereinskette bekommt. Sie steht immer in der kleinen grauen Zeile und nie in
 * der Überschrift — die trägt nur den Mannschaftsnamen (`#1`, `#2`), und den haben die wenigsten
 * Boote. Stand die Kette dort, wurde aus jedem vereinsgemischten Boot zwei Zeilen Titeltext.
 *
 * Weggelassen wird sie nur, wenn der Mannschaftsname den Verein bereits trägt — sonst stünde er
 * zweimal untereinander. Geprüft wird gegen beide Fassungen, weil die Karte je nach Breite die
 * eine oder die andere zeigt und die Entscheidung nur einmal fallen kann.
 */
export const teamShowsClubLine = (
    team: Pick<LiveDashboardTeamDto, 'teamName' | 'clubsShort' | 'clubsFull'>,
): boolean =>
    team.clubsFull !== '' &&
    (team.teamName == null ||
        (!team.teamName.includes(team.clubsFull) && !team.teamName.includes(team.clubsShort)))

/** Trennzeichen der Kette — dieselbe Zeichenfolge wie `ClubComposition.SEPARATOR` im Backend. */
export const CLUB_CHAIN_SEPARATOR = ' / '

/**
 * Wie viele Zeichen der Kurzform-Kette die schmale Karte trägt, bevor gekürzt wird.
 *
 * Nachgemessen am 09.08.2026 im Browser an der echten Karte (390 px Telefon, Roboto 14 px = MUI
 * `body2`, im Mittel 6,6 px je Zeichen): der Vereinszeile bleiben 277 px, also gut 42 Zeichen je
 * Zeile und rund 84 auf den zwei Zeilen, die sie hoch werden darf. Umgebrochen wird an den
 * Trennern, eine Zeile füllt sich also nie ganz — der Abschlag darauf ergibt die 72.
 */
export const CLUB_CHAIN_NARROW_CHARS = 72

/**
 * Dasselbe für eine Karte, die bereits Ergebnisse zeigt: Zeit, Platz und Ampel nehmen sich feste
 * Spalten, und der Vereinszeile bleiben am Telefon nur noch 149 px (nachgemessen wie oben) — etwa
 * 22 Zeichen je Zeile. Ohne diesen zweiten Wert schnitte die Zeilenbegrenzung mitten im
 * Vereinsnamen ab, und genau das soll das `+n` verhindern.
 */
export const CLUB_CHAIN_NARROW_RESULT_CHARS = 40

/**
 * Die Kurzform-Kette für die schmale Karte: so viele **ganze** Vereinsnamen, wie in etwa zwei
 * Zeilen passen, der Rest als `+n`. Aus `Humlebæk Roklub / Nakskov Roklub / Kerteminde Roklub …`
 * wird `Humlebæk Roklub / Nakskov Roklub +1`.
 *
 * Entschieden wird nach Textlänge und nicht nach einer festen Vereinszahl: zwei kurze dänische
 * Vereine stehen noch nebeneinander, wo zwei lange deutsche schon abfallen. Ein Vereinsname wird
 * dabei nie zerrissen — lieber steht ein einzelner überlanger Name allein da (und läuft in der
 * Zeilenbegrenzung aus), als dass die Anzeige einen halben Verein behauptet.
 *
 * Nur für die schmale Stufe: ab 480 px zeigt die Karte die vollständige Kette in vollen Namen.
 */
export const shortenClubChain = (
    chain: string,
    maxChars: number = CLUB_CHAIN_NARROW_CHARS,
): string => {
    if (chain.length <= maxChars) return chain

    const clubs = chain.split(CLUB_CHAIN_SEPARATOR)
    for (let keep = clubs.length - 1; keep >= 1; keep--) {
        const shortened = `${clubs.slice(0, keep).join(CLUB_CHAIN_SEPARATOR)} +${clubs.length - keep}`
        if (shortened.length <= maxChars || keep === 1) return shortened
    }
    return chain
}

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
 * - "Läuft" gibt es nur am Start (PREPARING): der Knopf stellt fest, dass das Rennen unterwegs
 *   ist. Bei einem Lauf, der schon unterwegs ist, gäbe es nichts mehr festzustellen; bei einem,
 *   den niemand aufgerufen hat, wäre die Feststellung eine Behauptung.
 */
export const matchControls = (
    match: LiveDashboardMatchDto,
    mayFinish: boolean,
    mayControl: boolean,
): {showFinish: boolean; showActivationToggle: boolean; showMarkStarted: boolean} => {
    if (match.state === 'SKIPPED') {
        return {showFinish: false, showActivationToggle: false, showMarkStarted: false}
    }
    return {
        showFinish: mayFinish && isLiveMatch(match),
        showActivationToggle: mayControl && match.state !== 'AWAITING_FINISH',
        showMarkStarted: mayControl && match.state === 'PREPARING',
    }
}

/**
 * Ein Boot ist erledigt, sobald Platz, Zeit oder ein Ausscheidungsgrund vorliegt. Abgemeldete
 * Boote sind es ebenfalls — auf ihr Ergebnis wartet niemand mehr.
 */
export const teamHasResult = (team: LiveDashboardTeamDto): boolean =>
    team.deregistered || team.failed || team.place != null || team.time != null

/**
 * Der Zustand eines Dashboard-Laufs als [MatchStatusDto] — dieselbe Form, die Durchführung und
 * Zeitplan lesen. Stand bis hierher inline im JSX der Karte und war damit nicht prüfbar; die
 * Ableitung selbst bleibt unverändert.
 *
 * `teamsScored` zählt nach derselben Regel wie `MatchStatusLogic.scoredCount` im Backend (Platz,
 * ausgeschieden oder abgemeldet), damit „Teilweise gewertet" hier nichts anderes sagt als dort.
 */
export const dashboardMatchStatus = (match: LiveDashboardMatchDto): MatchStatusDto => ({
    state: match.state,
    startedAt: match.startedAt ?? undefined,
    teamsTotal: match.teams.length,
    teamsScored: match.teams.filter(teamHasResult).length,
    bye: match.bye,
})

/**
 * Ob der Lauf überhaupt schon etwas gewertet hat — dieselbe Bedingung, unter der die Karte ihre
 * Ergebnisspalten aufzieht. Eine Abmeldung zählt bewusst nicht dazu: sie steht oft schon vor dem
 * Start fest und soll die Anzeige nicht umsortieren, solange nichts gefahren ist.
 */
export const matchHasResults = (teams: LiveDashboardTeamDto[]): boolean =>
    teams.some(team => team.time != null || team.place != null || team.failed)

/**
 * Rang der vier Gruppen, in denen die Boote untereinander stehen: gewertet, noch offen,
 * ausgeschieden, abgemeldet.
 *
 * Die noch offenen Boote stehen bewusst über den ausgeschiedenen — auf sie wartet noch jemand,
 * während bei DSQ/DNF nichts mehr zu entscheiden ist.
 */
const teamOrderGroup = (team: LiveDashboardTeamDto): number =>
    team.place != null ? 0 : team.deregistered ? 3 : team.failed ? 2 : 1

/**
 * Die Boote eines Laufs in Anzeigereihenfolge: nach Startnummer, solange nichts gewertet ist — und
 * nach Platz, sobald es etwas zu sehen gibt. Der Erste steht dann oben, so wie ihn der
 * Schiedsrichter ins Ziel kommen sieht; ohne das musste er die Plätze in der Startliste
 * zusammensuchen.
 *
 * **Die Zahl links bleibt die Startnummer** und wird nie zur Zählnummer der Liste — sie ist die
 * einzige Verbindung zwischen der Karte und dem, was in der Arena steht. („Bahn" stand hier bis
 * zum 10.08.2026; es gibt im Datenmodell nur eine Zahl je Boot und Lauf,
 * `competition_match_team.start_number`, und die gesamte übrige Anwendung nennt sie Startnummer.)
 *
 * Solange kein einziges Boot ein Ergebnis hat, bleibt die Reihenfolge des Backends stehen (dort
 * nach Startnummer sortiert): ein Umsortieren, das nichts aussagt, verwirrt nur.
 */
export const teamsInDisplayOrder = (teams: LiveDashboardTeamDto[]): LiveDashboardTeamDto[] => {
    if (!matchHasResults(teams)) return teams

    return [...teams].sort((a, b) => {
        const group = teamOrderGroup(a) - teamOrderGroup(b)
        if (group !== 0) return group
        // Innerhalb der gewerteten Boote entscheidet der Platz, in allen anderen Gruppen die
        // Startnummer. Boote ohne Startnummer fallen ans Ende ihrer Gruppe, statt die Sortierung
        // zu stören.
        if (a.place != null && b.place != null) return a.place - b.place
        if (a.startNumber == null) return b.startNumber == null ? 0 : 1
        if (b.startNumber == null) return -1
        return a.startNumber - b.startNumber
    })
}

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
 * Ziel-scrollTop, das ein Element mittig in seinen Scroll-Container stellt — das rechnerische
 * Gegenstück zu `scrollIntoView({block: 'center'})`, nur eben auf einen einzigen Container
 * bezogen statt auf alle scrollbaren Vorfahren. `elementTop` ist die Oberkante des Elements im
 * Scroll-Inhalt des Containers (nicht im Viewport). Begrenzt auf den fahrbaren Bereich, damit
 * Einträge am Anfang und Ende der Liste nicht überschießen.
 */
export const centeredScrollTop = (
    elementTop: number,
    elementHeight: number,
    containerClientHeight: number,
    containerScrollHeight: number,
): number => {
    const centered = elementTop - (containerClientHeight - elementHeight) / 2
    const maxScrollTop = Math.max(0, containerScrollHeight - containerClientHeight)
    return Math.min(Math.max(0, centered), maxScrollTop)
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

// === Geräte-lokale Anpassungen des Boards (12.08.2026) ==========================================
// Die Logik der acht Einstellungen aus dem Einstellungs-Popover — als reine Funktionen hier statt
// im JSX, damit sie ohne Rendering prüfbar bleibt (siehe common.test.ts). Die Persistenz liegt in
// deviceSettings.ts, hier steht nur, WAS die Einstellungen mit den gepollten Daten machen.

/** Ein Eintrag der Wettkampf-Mehrfachauswahl: die Id trägt die Wahl, das Label die Anzeige. */
export type DashboardCompetitionOption = {competitionId: string; label: string}

/**
 * Die wählbaren Wettkämpfe, abgeleitet aus den im Dashboard vorhandenen Läufen — es gibt keinen
 * eigenen Abruf dafür, und was keinen Lauf hat, gäbe gefiltert ohnehin eine leere Liste. Das Label
 * folgt der Kurz-/Langform-Wahl des Boards. Sortiert nach Label, damit die Liste im Popover der
 * Leserichtung folgt und nicht der Ausführungsreihenfolge der Läufe.
 */
export const dashboardCompetitionOptions = (
    matches: LiveDashboardMatchDto[],
    mode: 'full' | 'short' = 'full',
): DashboardCompetitionOption[] => {
    const byId = new Map<string, string>()
    for (const match of matches) {
        if (!byId.has(match.competitionId)) {
            byId.set(match.competitionId, competitionLabel(match, mode) ?? match.competitionName)
        }
    }
    return [...byId.entries()]
        .map(([competitionId, label]) => ({competitionId, label}))
        .sort((a, b) => a.label.localeCompare(b.label))
}

/**
 * Der Wettkampf-Filter über den Läufen: leer heißt „alle" — der Filter ist eine Fokushilfe, kein
 * Pflichtfeld. Ids, die in den Daten gar nicht vorkommen (z. B. eine gespeicherte Wahl von
 * gestern), filtern schlicht nichts Zusätzliches weg.
 */
export const filterMatchesByCompetitions = (
    matches: LiveDashboardMatchDto[],
    selectedIds: string[],
): LiveDashboardMatchDto[] =>
    selectedIds.length === 0
        ? matches
        : matches.filter(match => selectedIds.includes(match.competitionId))

/**
 * Dieselbe Auswahl für die wartenden Slots. Ein PendingSlotDto trägt keine competitionId, nur den
 * Namen — verglichen wird deshalb gegen die Namen der gewählten Wettkämpfe aus den Läufen.
 * Programmpunkte (FREE, `name` gesetzt) bleiben immer stehen: die Mittagspause gehört zu keinem
 * Wettkampf und soll durch keinen Filter verschwinden. Ebenso bleibt ein Slot stehen, dessen
 * Wettkampfname sich aus den Läufen nicht auflösen lässt — lieber einer zu viel als ein
 * „verlorener" Lauf am Renntag.
 */
export const filterPendingSlotsByCompetitions = (
    pendingSlots: PendingSlotDto[],
    matches: LiveDashboardMatchDto[],
    selectedIds: string[],
): PendingSlotDto[] => {
    if (selectedIds.length === 0) {
        return pendingSlots
    }
    const knownNames = new Set(matches.map(match => match.competitionName))
    const selectedNames = new Set(
        filterMatchesByCompetitions(matches, selectedIds).map(match => match.competitionName),
    )
    return pendingSlots.filter(
        slot =>
            slot.name != null ||
            slot.competitionName == null ||
            !knownNames.has(slot.competitionName) ||
            selectedNames.has(slot.competitionName),
    )
}

/**
 * Kalendertag (YYYY-MM-DD) einer naiven lokalen Zeitangabe — dieselbe Konvention wie `dayOf` in
 * timelineIndicator.ts, hier dupliziert statt importiert: timelineIndicator.ts importiert bereits
 * aus dieser Datei, die Gegenrichtung wäre ein Import-Kreis.
 */
const entryDay = (isoLikeTime: string): string => isoLikeTime.slice(0, 10)

/**
 * Der Tagesfilter der Läufe-Spalte: nur die Einträge des Tages [day] — desselben Tages, den auch
 * der Zeitstrahl-Indikator zeigt (heute bzw. der nächste Renntag, siehe resolveDashboardDay).
 * Bewusst nicht wörtlich „heute": am Vorabend einer Regatta wäre die Spalte sonst leer, obwohl der
 * Indikator darüber bereits den Renntag anzeigt. Einträge ohne Startzeit bleiben stehen — ohne
 * Zeit gehören sie zu keinem Tag, und Verstecken wäre die falsche Antwort darauf.
 */
export const filterTimelineEntriesForDay = (
    entries: LiveDashboardTimelineEntry[],
    day: string,
): LiveDashboardTimelineEntry[] =>
    entries.filter(entry => {
        const startTime = entry.kind === 'match' ? entry.match.startTime : entry.slot.startTime
        return startTime == null || entryDay(startTime) === day
    })

/**
 * Wie viele beendete Läufe beim Ausblenden als Kontext stehen bleiben. Bewusst fest statt als
 * eigener Zahlenwähler (Entscheidung vom 12.08.2026): zwei reichen, um „was war eben?" zu
 * beantworten, und jede weitere Stellschraube macht das Popover am Steg unbedienbarer.
 */
export const KEEP_FINISHED_CONTEXT = 2

/**
 * „Beendete ausblenden": versteckt beendete Läufe der Läufe-Spalte bis auf die [keepLast]
 * jüngsten — [entries] ist nach Startzeit sortiert (buildLiveDashboardTimeline), die letzten
 * beendeten der Liste sind also die zuletzt gefahrenen. Nur FINISHED zählt als beendet;
 * abgesagte Läufe (SKIPPED) bleiben sichtbar, ihre Absage muss auffindbar bleiben, um sie im
 * Zeitplan zurücknehmen zu können.
 */
export const hideFinishedTimelineEntries = (
    entries: LiveDashboardTimelineEntry[],
    keepLast: number = KEEP_FINISHED_CONTEXT,
): LiveDashboardTimelineEntry[] => {
    const finishedIds = entries.flatMap(entry =>
        entry.kind === 'match' && entry.match.state === 'FINISHED' ? [entry.match.matchId] : [],
    )
    const hidden = new Set(finishedIds.slice(0, Math.max(0, finishedIds.length - keepLast)))
    return entries.filter(
        entry => !(entry.kind === 'match' && hidden.has(entry.match.matchId)),
    )
}

/**
 * Wie lange nach einer Hand-Interaktion das automatische Nachführen pausiert — derselbe Wert wie
 * MANUAL_SCROLL_IDLE_MS der Tagesprogramm-Kachel (BoardMatchListElement): wer gerade selbst liest,
 * soll nicht vom nächsten Datenupdate weggezogen werden.
 */
export const FOLLOW_MANUAL_IDLE_MS = 30_000

/**
 * Welche Karte „Folge dem aktuellen Lauf" in der Läufe-Spalte zentriert: der erste Lauf, der im
 * Live-Sinn läuft (an den Start gerufen, unterwegs oder wartet auf Beenden), sonst der nächste
 * anstehende. [matches] kommt in Server-Reihenfolge (nach Startzeit) — „der erste" ist also der
 * früheste. Null, wenn es nichts zu folgen gibt (alles beendet oder Liste leer).
 */
export const followTargetMatchId = (matches: LiveDashboardMatchDto[]): string | null =>
    (matches.find(isLiveMatch) ?? matches.find(match => match.state === 'UPCOMING'))?.matchId ??
    null

/**
 * Der Detailgrad der Karten, gebündelt durchgereicht (Seite → Spalten → Karte) wie die
 * [LiveDashboardActions]: die Werte kommen aus den deviceSettings-Schlüsseln, die Karte selbst
 * kennt nur noch das Ergebnis. Ein Bündel statt einzelner Props, damit jede weitere Einstellung
 * nicht drei Komponenten-Signaturen gleichzeitig aufbohrt.
 */
export type LiveDashboardDetailSettings = {
    /** Jüngste Schiedsrichter-Notiz einzeilig an der Bootszeile (zusätzlich zu Icon+Zähler). */
    notePreview: boolean
    /** Crew-Zeilen (Aufstellung) zeigen — aus ist radikaler als der Kompaktmodus. */
    showCrew: boolean
    /** An den Bootszeilen nur noch Prüfungs-Icons mit Severity CRITICAL (siehe showsSeverityIcon). */
    criticalChecksOnly: boolean
}

/**
 * Die jüngste Schiedsrichter-Notiz eines Boots für die einzeilige Vorschau an der Bootszeile —
 * `notes` kommt vom Server älteste zuerst (siehe LiveDashboardTeamDto), die jüngste ist also die
 * letzte. Null ohne Notizen; die Vorschau erscheint dann gar nicht.
 */
export const latestTeamNote = (
    team: Pick<LiveDashboardTeamDto, 'notes'>,
): MatchTeamNoteDto | null => {
    const notes = team.notes ?? []
    return notes.length > 0 ? notes[notes.length - 1] : null
}

/**
 * Ob die Bootszeile ihr Prüfungs-Icon zeigt. Mit „Nur kritische Prüfungen zeigen" bleiben nur
 * CRITICAL-Icons stehen — OK/Warnung/Neutral verschwinden, damit auf einem vollen Board nur noch
 * das ins Auge springt, was ein Eingreifen verlangt. Standard ist aus: alle Icons.
 */
export const showsSeverityIcon = (severity: EffectiveSeverity, onlyCritical: boolean): boolean =>
    !onlyCritical || severity === 'CRITICAL'

/** Die drei Stufen der Karten-Schriftgröße — eigene Einstellung NEBEN dem Kompaktmodus. */
export const DASHBOARD_FONT_SCALES = ['normal', 'large', 'xlarge'] as const
export type DashboardFontScale = (typeof DASHBOARD_FONT_SCALES)[number]

/** Skalierungsfaktor je Stufe — moderat, damit auch „Sehr groß" die Kartenzeilen nicht sprengt. */
const FONT_SCALE_FACTORS: Record<DashboardFontScale, number> = {
    normal: 1,
    large: 1.15,
    xlarge: 1.3,
}

/**
 * Die rem-Schriftgrößen der drei Typography-Varianten, die der Karten-Wrapper übersteuert —
 * dasselbe Muster wie der Kompaktmodus, nur berechnet statt fest verdrahtet: Basis sind die
 * MUI-Standardgrößen bzw. die kleineren Kompakt-Stufen, darauf multipliziert der Faktor der
 * gewählten Stufe. Kompakt und Groß schließen sich damit nicht aus, sie verrechnen sich —
 * „kompakt + groß" ergibt dichte Karten mit größerer Schrift, genau das Steg-Szenario
 * (wenig Platz, viel Sonne). Null bei „nichts zu übersteuern", damit der Wrapper im
 * Normalzustand gar keine CSS-Regeln aufspannt.
 */
export const dashboardTypographySizes = (
    compact: boolean,
    scale: DashboardFontScale,
): {subtitle1: string; body2: string; caption: string} | null => {
    if (!compact && scale === 'normal') {
        return null
    }
    // Kompakt-Basen wie bisher (0.875/0.8/0.7), sonst die MUI-Standardgrößen der Varianten.
    const base = compact
        ? {subtitle1: 0.875, body2: 0.8, caption: 0.7}
        : {subtitle1: 1, body2: 0.875, caption: 0.75}
    const factor = FONT_SCALE_FACTORS[scale]
    const rem = (value: number) => `${Math.round(value * factor * 1000) / 1000}rem`
    return {subtitle1: rem(base.subtitle1), body2: rem(base.body2), caption: rem(base.caption)}
}
