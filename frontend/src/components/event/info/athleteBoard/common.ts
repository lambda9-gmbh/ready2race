import {TFunction} from 'i18next'

export const formatClockTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

/**
 * Uhrzeit MIT Sekunden — für den gemessenen Boot-Start auf der Sprecher-Kachel: bei
 * Einzelstarts (Zeitfahren) liegen die Boote Sekunden auseinander, „10:31" hilft der
 * Sprecherin nicht.
 */
export const formatClockTimeWithSeconds = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    })

/** Tag und Monat ohne Jahr — genug, um "heute" von "nächste Woche" zu unterscheiden. */
export const formatShortDate = (value: string) =>
    new Date(value).toLocaleDateString(undefined, {day: '2-digit', month: '2-digit'})

/** Gleicher Kalendertag in der lokalen Zeitzone. */
export const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()

/**
 * Ab dieser Entfernung trägt ein Countdown nichts mehr bei: Statt einer sechsstelligen
 * Minutenzahl zeigt die Karte dann das Datum. Ein Lauf, der morgen stattfindet, braucht
 * keine Restzeit — er braucht ein Datum.
 */
export const COUNTDOWN_MAX_SECONDS = 24 * 60 * 60

/**
 * Restzeit bis zum Start, gestuft nach Größenordnung.
 *
 * Ohne Stundenstufe entstand für einen Lauf in einer Woche die Anzeige "in 9886 min" —
 * formal richtig, praktisch unlesbar. Aufrufer prüfen zusätzlich COUNTDOWN_MAX_SECONDS
 * und zeigen jenseits davon das Datum statt einer Restzeit.
 *
 * Wird auch von „Mein Event" genutzt: die Restzeit auf dem Telefon und die auf der
 * Wandanzeige müssen dieselbe Formulierung tragen.
 */
export const formatRemaining = (seconds: number, t: TFunction): string => {
    const total = Math.max(0, Math.floor(seconds))
    const hours = Math.floor(total / 3600)
    const minutes = Math.floor((total % 3600) / 60)

    if (hours > 0) {
        const h = `${hours} ${t('event.info.athleteBoard.hoursUnit')}`
        return minutes > 0 ? `${h} ${minutes} ${t('event.info.athleteBoard.minutesUnit')}` : h
    }
    if (minutes > 0) {
        return `${minutes} ${t('event.info.athleteBoard.minutesUnit')}`
    }
    return `${total % 60} ${t('event.info.athleteBoard.secondsUnit')}`
}

/**
 * Ein Platz als Ordnungszahl („1." / "1st" / „1."), sprachrichtig über die
 * Ordinal-Pluralregeln von i18next. Überall, wo ein Platz neben einer Startnummer
 * stehen kann, muss er als Ordnungszahl erscheinen — eine nackte „1" ist von der
 * Startnummer 1 nicht zu unterscheiden (Wunsch vom 10.08.2026, Handtest am Prod-Abzug).
 */
export const formatPlace = (place: number, t: TFunction): string =>
    t('event.info.athleteBoard.place', {count: place, ordinal: true})

/**
 * Reihenfolge der Boote im laufenden Lauf: Sobald Zwischenstände da sind, zählt die
 * aktuelle Platzierung mehr als die Startnummer (Wunsch vom 11.08.2026 — beim Zeitfahren
 * am Prod-Abzug standen die Boote 1–8 in Startreihenfolge, die Plätze kreuz und quer
 * daneben). Ohne jedes Teilergebnis bleibt die Startreihenfolge stehen — vor dem ersten
 * Zwischenstand gibt es nichts Relevanteres. Gewertete Boote nach Platz, noch fahrende
 * dahinter in Startreihenfolge, DNF/DQ ans Ende; die Startnummer bleibt als große Zahl
 * an der Zeile und trägt weiter die Zuordnung zum Boot.
 */
export const sortRunningTeams = <
    T extends {startNumber: number; place?: number | null; failed?: boolean; timeString?: string | null},
>(
    teams: T[],
): T[] => {
    const anyResult = teams.some(t => t.place != null || t.failed === true || t.timeString != null)
    if (!anyResult) return teams
    return [...teams].sort((a, b) => {
        const aFailed = a.failed === true
        const bFailed = b.failed === true
        if (aFailed !== bFailed) return aFailed ? 1 : -1
        if (a.place == null && b.place == null) return a.startNumber - b.startNumber
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place || a.startNumber - b.startNumber
    })
}

/**
 * „Zieleinlauf komplett": jedes Boot der Aufstellung eines laufenden Laufs hat ein Ergebnis —
 * Platz/Zeit oder gescheitert (DNS/DNF/DSQ). Der Lauf wartet dann nur noch auf die
 * Schiedsrichter-Entscheidung (Beenden), erst die verschiebt ihn in „Letztes Ergebnis".
 *
 * Dieselbe Auslegung wie `LiveDashboardLogic.teamHasResult` im Backend, so wie sie dort für
 * laufende Läufe angewandt wird (Conversions, `deregistered = false`): mehr als Platz und
 * Gescheitert-Status weiß über ein laufendes Feld auch das Schiedsrichter-Dashboard nicht.
 */
export const finishComplete = (teams: {place?: number | null; failed?: boolean}[]): boolean =>
    teams.length > 0 && teams.every(team => team.place != null || team.failed === true)

/**
 * Welche Form der Vereinskette in der Zeile steht. Die Entscheidung trifft die Breite des
 * Bildschirms, nicht der Inhalt: am Steg hängt ein großer Schirm, in der Hand ein Telefon.
 */
export type ClubChainVariant = 'full' | 'short'

export interface TeamWithClubs {
    clubsShort?: string | null
    clubsFull?: string | null
    teamName?: string | null
    teamNumber?: number | null
}

/**
 * Anzeigename einer Mannschaft. Fehlt der gepflegte Name, tritt die Nummer der Mannschaft
 * an seine Stelle — der Verein allein unterscheidet sonst zwei Boote desselben Vereins nicht.
 *
 * Vorangestellt sind die Vereine, die die Athleten tragen, als Kette in Bootsreihenfolge. Fehlt
 * die angefragte Form, tritt die andere ein: eine Zeile ohne jeden Verein wäre schlechter als
 * eine in der falschen Länge.
 */
export const teamLabel = (
    team: TeamWithClubs,
    t: TFunction,
    variant: ClubChainVariant,
): string => {
    const name =
        team.teamName ??
        (team.teamNumber != null
            ? t('event.info.athleteBoard.teamNumber', {number: team.teamNumber})
            : null)
    const clubs =
        variant === 'short'
            ? (team.clubsShort ?? team.clubsFull)
            : (team.clubsFull ?? team.clubsShort)
    return [clubs, name].filter(Boolean).join(' | ')
}

/**
 * Eine Größe, die mit der Dichte der Bühne mitskaliert.
 *
 * `--ab-scale` setzt die Bühne einmal aus [densityScale]; jede Schriftgröße und jeder Abstand der
 * Karten hängt daran. Der Vorgabewert 1 hält die Karten auch außerhalb der Bühne benutzbar
 * (Kiosk-Rotation, künftige Einzelansichten).
 */
export const scaled = (min: string, preferred: string, max: string): string =>
    `calc(var(--ab-scale, 1) * clamp(${min}, ${preferred}, ${max}))`
