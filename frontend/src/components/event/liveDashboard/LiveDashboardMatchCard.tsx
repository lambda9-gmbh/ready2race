import {Fragment} from 'react'
import {Box, Button, Card, CardContent, Divider, Stack, Typography} from '@mui/material'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import StickyNote2OutlinedIcon from '@mui/icons-material/StickyNote2Outlined'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {MatchResultStatus, matchResultStatus} from '@utils/matchResultStatus.ts'
import {raceClockerPollStatus} from '@components/event/competition/excecution/raceClockerPollStatus.ts'
import {matchStatusChip} from '@components/event/match/matchStatusChip.ts'
import {byeExplanation} from '@components/event/match/matchBye.ts'
import {groupByRatingCategory, hasRatingCategories} from '@utils/ratingCategorySections.ts'
import {
    CLUB_CHAIN_NARROW_CHARS,
    CLUB_CHAIN_NARROW_RESULT_CHARS,
    competitionLabel,
    crewMemberLabel,
    dashboardMatchStatus,
    latestTeamNote,
    LiveDashboardDetailSettings,
    matchControls,
    teamNoteCount,
    matchHasResults,
    openResultTeams,
    pendingSlotLabel,
    shortenClubChain,
    showsSeverityIcon,
    teamShowsClubLine,
    teamShowsCrew,
    teamsInDisplayOrder,
} from './common.ts'
import FinishMatchButton from './FinishMatchButton.tsx'
import SeverityIcon from './SeverityIcon.tsx'

/**
 * Ab dieser Kartenbreite ist Platz für die Langform des Status. Darunter würde sie die Spalte im
 * Kopf-Grid so weit aufziehen, dass daneben nur noch der erste Buchstabe des Laufnamens bleibt —
 * beide Zeilen teilen sich dieselbe Spalte.
 */
const WIDE_CARD_PX = 480

/**
 * Ab hier trägt die Karte zusätzlich die Crew je Boot — Nachname, Vereinskurzform und Rolle. Auch
 * das entscheidet die Kartenbreite und nicht das Fenster: auf dem Tablet stehen zwei Spalten
 * nebeneinander, von denen keine so breit wird, obwohl das Fenster es wäre. Ob die Crew überhaupt
 * geladen wurde, hängt dagegen am Fenster (`dashboardCrew`) — die Nutzlast wird je Abruf
 * entschieden, nicht je Karte.
 */
const CREW_CARD_PX = 700

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (matchId: string, teamId: string) => void
    /** Nur gesetzt, wenn die Nutzerin den Ablauf steuern darf. */
    onFinish?: (matchId: string, openResults: MatchResultStatus | null) => Promise<void>
    onSetActivated?: (matchId: string, activated: boolean) => Promise<void>
    /** „Läuft": stellt fest, dass das Rennen unterwegs ist — löst keine Zeitnahme aus. */
    onMarkStarted?: (matchId: string) => Promise<void>
    /** Gibt den pausierten RaceClocker-Abruf dieses Laufs wieder frei. */
    onResumeAutoPull?: (matchId: string, competitionId: string) => Promise<void>
    /**
     * Ob der automatische RaceClocker-Abruf für diese Veranstaltung eingeschaltet ist. Dann trägt
     * der „Läuft"-Knopf den Hinweis, dass RaceClocker den Start ohnehin selbst meldet — bedienbar
     * bleibt er trotzdem (Feed-Ausfall, Zeitnahme ohne Startstempel).
     */
    raceClockerAutoPull?: boolean
    /** Rennen am Kürzel statt am ausgeschriebenen Wettkampfnamen (geteilt mit dem Zeitplan-Tab). */
    shortLabels: boolean
    /** Detailgrad der Bootszeilen (Notiz-Vorschau, Aufstellung) — geräte-lokal eingestellt. */
    detail: LiveDashboardDetailSettings
}

const LiveDashboardMatchCard = ({
    match,
    onTeamClick,
    onFinish,
    onSetActivated,
    onMarkStarted,
    onResumeAutoPull,
    raceClockerAutoPull = false,
    shortLabels,
    detail,
}: Props) => {
    const {t} = useTranslation()

    const running = match.state === 'RUNNING'
    // An den Start gerufen, aber noch nicht unterwegs — der Lauf, der als Nächstes losgeht.
    const preparing = match.state === 'PREPARING'
    // Abgesagt wird gekennzeichnet, nicht versteckt: der Schiedsrichter muss die Absage sehen, um
    // sie im Zeitplan zurücknehmen zu können. Solange sie steht, gibt es hier aber nichts zu
    // steuern - aktiviert würde der Lauf sonst wieder abgesagt UND laufend zugleich.
    const skipped = match.state === 'SKIPPED'
    // Vollständig gewertet, aber nicht beendet: der Lauf wartet auf den Beenden-Klick.
    const awaitingFinish = match.state === 'AWAITING_FINISH'
    const {showFinish, showActivationToggle, showMarkStarted} = matchControls(
        match,
        onFinish != null,
        onSetActivated != null,
    )
    /**
     * Der Zustandstext der Karte kommt aus derselben Ableitung wie in Durchführung und Zeitplan —
     * die Karte entscheidet nichts mehr selbst, sie malt nur. Vorher stand hier eigene Textlogik
     * („läuft seit …", Lang-/Kurzform für „wartet auf Beenden"), die dieselbe Aussage anders
     * formulierte; genau daran ließ sich nicht mehr erkennen, ob zwei Ansichten dasselbe meinen.
     *
     * `teamsScored` zählt nach derselben Regel wie `MatchStatusLogic.scoredCount` im Backend
     * (Platz, ausgeschieden oder abgemeldet), damit „Teilweise gewertet" hier nichts anderes sagt
     * als dort. Die Uhr ist die des Browsers: die Seite rendert bei jedem Abruf und mindestens alle
     * 30 Sekunden neu (siehe `useLocalClock` in LiveDashboardPage), die Minutenangabe zählt also
     * zwischen zwei Abrufen weiter.
     *
     * Die Zusammensetzung selbst liegt in `common.ts`, damit sie ohne Rendering prüfbar bleibt.
     */
    const status = dashboardMatchStatus(match)
    const statusChip = matchStatusChip(status, match.startTime, new Date())
    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die gelockerte Signatur — dasselbe Muster
    // wie `StatusChip` in @components/event/match/StatusChip.tsx.
    const translate = t as (key: string, values?: Record<string, string | number>) => string
    // Einmal je Karte statt dreimal im JSX — dieselbe Stelle wie im Zeitplan-Tab.
    const bye = byeExplanation(match.bye)
    // Result columns are reserved for the whole match, not per row: times then line up
    // underneath each other and every team name keeps the same width.
    const hasResults = matchHasResults(match.teams)
    // Sobald gewertet wird, steht der Erste oben — die Zahl links bleibt dabei die Startnummer.
    const orderedTeams = teamsInDisplayOrder(match.teams)
    // Gewertet wird je Wertungskategorie. Solange kein Boot ein Ergebnis hat, bleibt die Karte
    // eine durchgehende Startnummernliste: am Steg wird sie gegen das Wasser gelesen, und
    // Zwischenüberschriften zerschnitten dort nur den Blick auf das Feld.
    const sections = hasResults
        ? groupByRatingCategory(orderedTeams, team => team.ratingCategory)
        : []
    const showSectionHeadings = hasResults && hasRatingCategories(sections)
    const teams = hasResults ? sections.flatMap(section => section.entries) : orderedTeams
    // Vor welchem Boot eine Kategorieüberschrift steht: dem jeweils ersten seines Abschnitts.
    const headingBeforeTeam = new Map(
        showSectionHeadings
            ? sections
                  .filter(section => section.entries.length > 0)
                  .map(section => [
                      section.entries[0].teamId,
                      section.category?.name ?? t('event.ratingCategory.withoutCategory'),
                  ])
            : [],
    )
    const openTeams = openResultTeams(match)
    const resultsComplete = match.teams.length > 0 && openTeams.length === 0
    const columns = hasResults ? '2ch minmax(0, 1fr) 10.5ch 2rem 26px' : '2ch minmax(0, 1fr) 26px'
    // Sobald Zeit und Platz ihre Spalten belegen, bleibt der Vereinszeile am Telefon noch die
    // Hälfte der Breite - die Kette muss dann früher aufs "+n" ausweichen.
    const narrowChainChars = hasResults ? CLUB_CHAIN_NARROW_RESULT_CHARS : CLUB_CHAIN_NARROW_CHARS

    return (
        <Card
            variant="outlined"
            sx={{
                minWidth: 0,
                overflow: 'hidden',
                // Die Karte richtet sich nach ihrer eigenen Breite, nicht nach der des Fensters:
                // nebeneinander stehende Spalten auf dem Tablet sind schmaler als ein Telefon,
                // ein Blick aufs Fenster würde dort die Langformen erzwingen.
                containerType: 'inline-size',
                // Accent bar instead of a full frame: marks the live race without shouting. Ein
                // Lauf am Start bekommt denselben Balken in seinem eigenen Ton — er ist der
                // nächste, der losgeht, und soll in der Liste genauso auffallen.
                borderLeft: running || preparing ? '6px solid' : undefined,
                borderLeftColor: running ? 'success.dark' : preparing ? 'info.main' : undefined,
            }}>
            <CardContent sx={{p: 1.25, '&:last-child': {pb: 0.5}}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) auto',
                        columnGap: 1.5,
                        alignItems: 'baseline',
                    }}>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        noWrap
                        sx={{textDecoration: skipped ? 'line-through' : 'none'}}>
                        {match.matchName ??
                            match.roundName ??
                            competitionLabel(match, shortLabels ? 'short' : 'full')}
                    </Typography>
                    <Box sx={{justifySelf: 'end', textAlign: 'right'}}>
                        <Typography
                            variant="subtitle1"
                            fontWeight={700}
                            sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.900'}}>
                            {match.startTime
                                ? t('event.liveDashboard.plannedAt', {
                                      time: format(new Date(match.startTime), t('format.time')),
                                  })
                                : '—'}
                        </Typography>
                        {match.startedAt && (
                            <Typography
                                variant="caption"
                                display="block"
                                sx={{color: 'success.dark', fontVariantNumeric: 'tabular-nums'}}>
                                {t('event.liveDashboard.startedAtLabel', {
                                    time: format(new Date(match.startedAt), t('format.time')),
                                })}
                            </Typography>
                        )}
                    </Box>
                    <Typography
                        variant="body2"
                        sx={{
                            color: 'grey.800',
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                        }}>
                        {[
                            competitionLabel(match, shortLabels ? 'short' : 'full'),
                            match.categoryName,
                            match.roundName,
                        ]
                            .filter(Boolean)
                            .join(' · ')}
                    </Typography>
                    <Box sx={{justifySelf: 'end'}}>
                        <Box
                            component="span"
                            sx={{
                                display: 'inline-block',
                                px: 0.75,
                                py: 0.25,
                                borderRadius: 1,
                                fontSize: '0.8rem',
                                fontWeight: 700,
                                whiteSpace: 'nowrap',
                                // Die Farbe bleibt die Betonung der Karte (der laufende Lauf trägt
                                // den kräftigsten Ton), der Text kommt aus der geteilten
                                // Ableitung. „In Vorbereitung" bekommt den helleren Blauton, weil
                                // „wartet auf Beenden" den dunklen schon belegt.
                                backgroundColor: running
                                    ? 'success.dark'
                                    : preparing
                                      ? 'info.main'
                                      : skipped
                                        ? 'warning.dark'
                                        : awaitingFinish
                                          ? 'info.dark'
                                          : 'grey.200',
                                color:
                                    running || preparing || skipped || awaitingFinish
                                        ? 'common.white'
                                        : 'grey.900',
                                textDecoration: statusChip.strikeThrough ? 'line-through' : 'none',
                            }}>
                            {translate(statusChip.labelKey, statusChip.values)}
                        </Box>
                    </Box>
                    {/*
                        Muss der letzte Kind-Knoten dieses Grids bleiben: die vier Kinder davor
                        füllen Zeile 1 und 2 exakt wie vor der Freilos-Anzeige, erst danach bleibt
                        für dieses volle-Breite-Element nur die eigene Zeile 3 übrig. Stünde es
                        früher, würde die Statuskennzeichnung in Zeile 2 keinen Platz mehr finden
                        und in Zeile 4 an die Spaltenkante statt an die Kartenkante rutschen.
                    */}
                    {bye && (
                        <Box sx={{gridColumn: '1 / -1'}}>
                            <Typography variant="caption" sx={{color: 'grey.700'}}>
                                {translate(bye.key, bye.values) +
                                    (bye.mustRace
                                        ? ` – ${translate('event.match.bye.mustRace')}`
                                        : '')}
                            </Typography>
                        </Box>
                    )}
                </Box>
                {match.pairingsRecalculatedAt && (
                    <Typography variant={'caption'} color={'warning.main'}>
                        {t('event.competition.execution.pairingsRecalculated')}
                    </Typography>
                )}
                {(() => {
                    const pollStatus = raceClockerPollStatus(match)
                    if (pollStatus.kind === 'none' || pollStatus.kind === 'ok') return null

                    return (
                        <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            flexWrap="wrap"
                            useFlexGap>
                            <Typography variant={'caption'} color={'warning.main'}>
                                {pollStatus.kind === 'paused'
                                    ? t(
                                          'event.competition.execution.results.raceclocker.poll.paused',
                                      )
                                    : t(
                                          'event.competition.execution.results.raceclocker.poll.error',
                                          {
                                              reason: pollStatus.errorKey
                                                  ? t(pollStatus.errorKey)
                                                  : t('common.error.unexpected'),
                                          },
                                      )}
                            </Typography>
                            {/*
                                Der Weg zurück gehört dorthin, wo pausiert wurde: Deaktivieren
                                pausiert den automatischen Abruf (sonst aktivierte ihn der Job im
                                nächsten Takt wieder), und deaktiviert wird hier im Dashboard.
                                Bisher gab es diesen Knopf nur im Durchführungs-Tab — der
                                Schiedsrichter am Steg käme dort nicht hin.
                            */}
                            {pollStatus.kind === 'paused' && onResumeAutoPull && (
                                <Button
                                    size="small"
                                    variant="text"
                                    onClick={() =>
                                        onResumeAutoPull(match.matchId, match.competitionId)
                                    }>
                                    {t(
                                        'event.competition.execution.results.raceclocker.poll.resume',
                                    )}
                                </Button>
                            )}
                        </Stack>
                    )
                })()}
                <Divider sx={{mt: 1.5}} />
                {teams.map((team, index) => {
                    const substituted = team.substituted
                    const showClubLine = teamShowsClubLine(team)
                    // Die Aufstellung nur, wenn die Daten sie tragen UND die Einstellung sie
                    // will — „Aufstellung anzeigen" (aus) räumt die Karten radikaler auf als
                    // der Kompaktmodus, der nur verdichtet.
                    const showCrew = teamShowsCrew(team) && detail.showCrew
                    const notePreview = detail.notePreview ? latestTeamNote(team) : null
                    const heading = headingBeforeTeam.get(team.teamId)

                    return (
                        <Fragment key={team.teamId}>
                            {heading !== undefined && (
                                <Typography
                                    variant="body2"
                                    fontWeight={700}
                                    color="text.secondary"
                                    sx={{mt: 1}}>
                                    {heading}
                                </Typography>
                            )}
                            <Box
                                onClick={() => onTeamClick(match.matchId, team.teamId)}
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: columns,
                                    columnGap: 0.75,
                                    alignItems: 'center',
                                    py: 1.25,
                                    mx: -1,
                                    px: 1,
                                    cursor: 'pointer',
                                    borderRadius: 1,
                                    borderBottom: index < teams.length - 1 ? '1px solid' : 'none',
                                    borderBottomColor: 'divider',
                                    '&:active': {backgroundColor: 'action.selected'},
                                    '@media (hover: hover)': {
                                        '&:hover': {backgroundColor: 'action.hover'},
                                    },
                                }}>
                                <Typography
                                    variant="subtitle1"
                                    fontWeight={700}
                                    sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.700'}}>
                                    {team.startNumber ?? '–'}
                                </Typography>
                                <Box sx={{minWidth: 0}}>
                                    <Stack
                                        direction="row"
                                        spacing={0.5}
                                        alignItems="center"
                                        sx={{minWidth: 0}}>
                                        <Box sx={{minWidth: 0}}>
                                            {/*
                                            Die Vereinskette steht oben und trägt die Zeile: sie
                                            sagt, wer da rudert. Der Mannschaftsname ist dagegen
                                            nur ein Zähler (`#1`, `#2`) für Vereine mit mehreren
                                            Booten und steht deshalb klein darunter — er
                                            unterscheidet, er benennt nicht.

                                            Fehlt die Kette (der Name trägt den Verein schon),
                                            rückt der Name an ihre Stelle und bekommt ihr Gewicht:
                                            prominent ist immer das, was oben steht.
                                        */}
                                            {showClubLine && (
                                                <Typography
                                                    variant="body2"
                                                    aria-label={t('event.liveDashboard.team.clubs')}
                                                    sx={{
                                                        color: 'grey.800',
                                                        // Zwei Zeilen hoch; schmal ist die Kette
                                                        // vorher schon auf ganze Vereinsnamen samt
                                                        // "+n" gekürzt, breit läuft sie hier aus.
                                                        display: '-webkit-box',
                                                        WebkitLineClamp: 2,
                                                        WebkitBoxOrient: 'vertical',
                                                        overflow: 'hidden',
                                                    }}>
                                                    <Box
                                                        component="span"
                                                        sx={{
                                                            display: 'inline',
                                                            [`@container (min-width: ${WIDE_CARD_PX}px)`]:
                                                                {
                                                                    display: 'none',
                                                                },
                                                        }}>
                                                        {shortenClubChain(
                                                            team.clubsShort,
                                                            narrowChainChars,
                                                        )}
                                                    </Box>
                                                    <Box
                                                        component="span"
                                                        sx={{
                                                            display: 'none',
                                                            [`@container (min-width: ${WIDE_CARD_PX}px)`]:
                                                                {
                                                                    display: 'inline',
                                                                },
                                                        }}>
                                                        {team.clubsFull}
                                                    </Box>
                                                </Typography>
                                            )}
                                            {team.teamName != null && (
                                                <Typography
                                                    variant={showClubLine ? 'caption' : 'subtitle1'}
                                                    sx={{
                                                        lineHeight: 1.25,
                                                        overflowWrap: 'break-word',
                                                        color: showClubLine
                                                            ? 'grey.600'
                                                            : undefined,
                                                        display: '-webkit-box',
                                                        WebkitLineClamp: 2,
                                                        WebkitBoxOrient: 'vertical',
                                                        overflow: 'hidden',
                                                    }}>
                                                    {team.teamName}
                                                </Typography>
                                            )}
                                        </Box>
                                        {substituted && (
                                            <SwapHorizIcon
                                                sx={{
                                                    fontSize: 22,
                                                    flexShrink: 0,
                                                    color: 'info.dark',
                                                }}
                                                titleAccess={t(
                                                    'event.liveDashboard.substitution.short',
                                                )}
                                            />
                                        )}
                                        {/*
                                            Schiedsrichter-Notizen zu diesem Boot - derselbe Platz
                                            wie der Ummeldungs-Marker daneben: ein Zeichen an der
                                            Zeile, die Notizen selbst stehen im Detail-Dialog.
                                            Nur wenn es welche gibt; eine Null hätte nichts zu sagen.
                                        */}
                                        {teamNoteCount(team) > 0 && (
                                            <Stack
                                                direction="row"
                                                spacing={0.25}
                                                alignItems="center"
                                                sx={{flexShrink: 0, color: 'info.dark'}}
                                                title={t('event.liveDashboard.notes.indicator', {
                                                    count: teamNoteCount(team),
                                                })}>
                                                <StickyNote2OutlinedIcon sx={{fontSize: 20}} />
                                                <Typography variant="caption" fontWeight={700}>
                                                    {teamNoteCount(team)}
                                                </Typography>
                                            </Stack>
                                        )}
                                    </Stack>
                                    {/*
                                        Die jüngste Notiz als einzeilige Vorschau direkt an der
                                        Zeile — zusätzlich zum Icon+Zähler oben, der weiterhin
                                        sagt, WIE VIELE es sind. Einzeilig und ellipsiert: die
                                        Vorschau soll ein Blickfang sein, kein zweiter Dialog;
                                        den vollen Text zeigt wie bisher der Detail-Dialog.
                                    */}
                                    {notePreview && (
                                        <Typography
                                            variant="caption"
                                            display="block"
                                            noWrap
                                            sx={{color: 'info.dark', fontStyle: 'italic'}}>
                                            {notePreview.note}
                                        </Typography>
                                    )}
                                    {showCrew && (
                                        <Typography
                                            variant="caption"
                                            aria-label={t('event.liveDashboard.team.crew')}
                                            sx={{
                                                color: 'grey.700',
                                                display: 'none',
                                                [`@container (min-width: ${CREW_CARD_PX}px)`]: {
                                                    display: '-webkit-box',
                                                },
                                                WebkitLineClamp: 2,
                                                WebkitBoxOrient: 'vertical',
                                                overflow: 'hidden',
                                            }}>
                                            {(team.crew ?? []).map(crewMemberLabel).join(' / ')}
                                        </Typography>
                                    )}
                                    {/* Zwischenzeiten aus RaceClocker, sobald der Feed sie liefert -
                                        dieselben Laps wie auf den Boards, hier in der Zeile mit. */}
                                    {(team.laps ?? []).length > 0 && (
                                        <Typography
                                            variant="caption"
                                            display="block"
                                            sx={{
                                                color: 'grey.700',
                                                fontVariantNumeric: 'tabular-nums',
                                            }}>
                                            {(team.laps ?? [])
                                                .map(lap => `${lap.name} ${lap.timeString}`)
                                                .join(' · ')}
                                        </Typography>
                                    )}
                                    {/*
                                        Vor dem ersten Ergebnis gibt es keine Kategorie-Abschnitte
                                        (die Karte bleibt Startnummernliste, siehe oben) - die
                                        Wertungskategorie soll aber trotzdem lesbar sein, BEVOR das
                                        erste Boot einläuft. Deshalb hier klein je Boot; sobald die
                                        Abschnitte übernehmen, entfällt sie, sonst stünde sie doppelt.
                                    */}
                                    {!hasResults && team.ratingCategory?.name && (
                                        <Typography
                                            variant="caption"
                                            display="block"
                                            sx={{color: 'grey.600'}}>
                                            {team.ratingCategory.name}
                                        </Typography>
                                    )}
                                    {/*
                                        Beim Zeitfahren startet jedes Boot einzeln - "wer ist schon
                                        unterwegs?" beantwortet der gemessene Boot-Start aus der
                                        Zeitnahme, solange weder Zielzeit noch Ausscheidung da ist.
                                    */}
                                    {running &&
                                        team.startedAt &&
                                        !team.time &&
                                        !team.failed && (
                                            <Typography
                                                variant="caption"
                                                display="block"
                                                sx={{
                                                    color: 'primary.main',
                                                    fontVariantNumeric: 'tabular-nums',
                                                }}>
                                                {t('event.liveDashboard.team.startedAt', {
                                                    time: format(
                                                        new Date(team.startedAt),
                                                        t('format.timeWithSeconds'),
                                                    ),
                                                })}
                                            </Typography>
                                        )}
                                    {team.inArenaRequired && team.inArenaAt && (
                                        <Typography
                                            variant="caption"
                                            display="block"
                                            sx={{
                                                color: 'success.dark',
                                                fontVariantNumeric: 'tabular-nums',
                                            }}>
                                            {t('event.liveDashboard.team.inArenaAt', {
                                                time: format(
                                                    new Date(team.inArenaAt),
                                                    t('format.time'),
                                                ),
                                            })}
                                        </Typography>
                                    )}
                                </Box>
                                {hasResults && (
                                    <>
                                        {/* Times share one right-aligned monospaced column, so they
                                        can be compared by scanning straight down. */}
                                        <Typography
                                            fontWeight={700}
                                            textAlign="right"
                                            sx={{
                                                fontSize: '0.9rem',
                                                fontFamily:
                                                    'ui-monospace, SFMono-Regular, Menlo, monospace',
                                                fontVariantNumeric: 'tabular-nums',
                                                letterSpacing: '-0.05em',
                                                color: team.failed
                                                    ? 'warning.dark'
                                                    : 'text.primary',
                                            }}>
                                            {team.failed
                                                ? (matchResultStatus(team.failedReason).status ??
                                                  t('event.liveDashboard.team.failedShort'))
                                                : (team.time ?? '')}
                                            {team.penaltySeconds != null && (
                                                <Typography
                                                    component="span"
                                                    color="warning.dark"
                                                    display="block"
                                                    sx={{
                                                        fontSize: '0.8rem',
                                                        fontVariantNumeric: 'tabular-nums',
                                                    }}>
                                                    {t('event.liveDashboard.penaltyIncluded', {
                                                        seconds: team.penaltySeconds,
                                                    })}
                                                </Typography>
                                            )}
                                        </Typography>
                                        <Box
                                            sx={{
                                                width: '2rem',
                                                height: '2rem',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                borderRadius: '50%',
                                                backgroundColor:
                                                    team.place != null
                                                        ? 'primary.main'
                                                        : 'transparent',
                                            }}>
                                            {team.place != null && (
                                                <Typography
                                                    fontWeight={700}
                                                    color="primary.contrastText"
                                                    sx={{
                                                        fontSize: '0.95rem',
                                                        fontVariantNumeric: 'tabular-nums',
                                                    }}>
                                                    {team.categoryPlace ?? team.place}
                                                </Typography>
                                            )}
                                        </Box>
                                    </>
                                )}
                                {/*
                                    „Nur kritische Prüfungen zeigen": OK/Warnung/Neutral treten
                                    ab, nur CRITICAL bleibt stehen (die verdichtete Severity je
                                    Boot kommt fertig bewertet aus dem Backend, siehe
                                    LiveDashboardTeamDto.severity). Die leere Box hält die
                                    Icon-Spalte des Grids besetzt, damit Zeilen mit und ohne
                                    Icon bündig bleiben.
                                */}
                                {showsSeverityIcon(team.severity, detail.criticalChecksOnly) ? (
                                    <SeverityIcon severity={team.severity} />
                                ) : (
                                    <Box />
                                )}
                            </Box>
                        </Fragment>
                    )
                })}
                {(showFinish || showActivationToggle || showMarkStarted) && (
                    /*
                        Fußzeile aufgeräumt (Rückmeldung vom 10.08.2026): Hinweistexte stehen
                        gedämpft ZEILEN­WEISE oben, die Knöpfe darunter in EINER rechtsbündigen
                        Reihe in fester Reihenfolge (deaktivieren · Läuft · Beenden). Vorher
                        mischten sich Texte und Knöpfe im selben umbrechenden Flexraum und
                        landeten am Telefon in vier verschiedenen Ausrichtungen.
                    */
                    <Stack spacing={1} sx={{pt: 1.5}}>
                        {running && !resultsComplete && (
                            <Typography variant="caption" sx={{color: 'grey.700'}}>
                                {t('event.liveDashboard.control.incompleteWarning')}
                            </Typography>
                        )}
                        {(awaitingFinish || (running && resultsComplete)) && (
                            <Typography variant="caption" sx={{color: 'success.dark'}}>
                                {t('event.liveDashboard.resultsCompleteWaiting')}
                            </Typography>
                        )}
                        {showMarkStarted && onMarkStarted && raceClockerAutoPull && (
                            <Typography variant="caption" sx={{color: 'grey.700'}}>
                                {t('event.liveDashboard.control.markStartedAutoHint')}
                            </Typography>
                        )}
                        <Stack
                            direction="row"
                            spacing={1}
                            flexWrap="wrap"
                            justifyContent="flex-end"
                            alignItems="center">
                            {showActivationToggle && onSetActivated && (
                                <Button
                                    size="small"
                                    variant="text"
                                    onClick={() =>
                                        onSetActivated(match.matchId, !(running || preparing))
                                    }>
                                    {running || preparing
                                        ? t('event.liveDashboard.control.deactivate')
                                        : t('event.liveDashboard.control.activate')}
                                </Button>
                            )}
                            {/*
                                „Läuft" statt „Start": der Klick löst keine Zeitnahme aus, er stellt
                                fest, dass das Rennen unterwegs ist. Bei eingeschaltetem RaceClocker-
                                Abruf bleibt er trotzdem bedienbar — der Feed kann ausfallen, und
                                manche Zeitnahme meldet gar keinen Startstempel.
                            */}
                            {showMarkStarted && onMarkStarted && (
                                <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => onMarkStarted(match.matchId)}>
                                    {t('event.liveDashboard.control.markStarted')}
                                </Button>
                            )}
                            {showFinish && onFinish && (
                                <FinishMatchButton
                                    openTeamCount={openTeams.length}
                                    onFinish={openResults => onFinish(match.matchId, openResults)}
                                />
                            )}
                        </Stack>
                    </Stack>
                )}
            </CardContent>
        </Card>
    )
}

export default LiveDashboardMatchCard

type PendingSlotCardProps = {
    slot: PendingSlotDto
    /** Nur gesetzt, wenn die Nutzerin den Ablauf steuern darf. */
    onSkip?: (slotId: string, label: string, time: string) => void
    shortLabels: boolean
}

/**
 * Platzhalter im Referee-Dashboard — entweder ein Programmpunkt (FREE, z.B. "Mittagspause") oder
 * ein wartender Lauf-Slot (Runde noch nicht gesetzt); `slot.name` unterscheidet die Fälle (siehe
 * `PendingSlotDto`). Bewusst ohne Teams oder Ergebnis-Spalten, die gibt es für beide Fälle nicht.
 */
export const LiveDashboardPendingSlotCard = ({slot, onSkip, shortLabels}: PendingSlotCardProps) => {
    const {t} = useTranslation()
    const isFree = slot.name != null
    const label = pendingSlotLabel(slot, shortLabels ? 'short' : 'full')
    const time = format(new Date(slot.startTime), t('format.time'))
    const stateLabel = t(isFree ? 'event.schedule.state.FREE' : 'event.schedule.state.WAITING')

    return (
        <Card variant="outlined" sx={{minWidth: 0, overflow: 'hidden'}}>
            <CardContent sx={{p: 1.25, '&:last-child': {pb: 0.75}}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) auto',
                        columnGap: 1.5,
                        alignItems: 'baseline',
                    }}>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        noWrap
                        sx={{color: 'grey.700'}}>
                        {label || stateLabel}
                    </Typography>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        textAlign="right"
                        sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.900'}}>
                        {time}
                    </Typography>
                </Box>
                <Box
                    sx={{
                        mt: 1,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: 1,
                    }}>
                    <Box
                        component="span"
                        sx={{
                            display: 'inline-block',
                            px: 0.75,
                            py: 0.25,
                            borderRadius: 1,
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            backgroundColor: 'grey.200',
                            color: 'grey.900',
                        }}>
                        {stateLabel}
                    </Box>
                    {/* Programmpunkte sagt nur die Orga ab (Zeitplan-Tab), nicht das
                        Schiedsrichter-Dashboard - das Backend lehnt das inzwischen auch ab. */}
                    {onSkip && !isFree && (
                        <Button
                            size="small"
                            variant="text"
                            onClick={() => onSkip(slot.slotId, label, time)}>
                            {t('event.schedule.skip')}
                        </Button>
                    )}
                </Box>
            </CardContent>
        </Card>
    )
}
