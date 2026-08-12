import {Box, Chip, Stack, Typography} from '@mui/material'
import {
    CheckCircle as CheckCircleIcon,
    RadioButtonUnchecked as RadioButtonUncheckedIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {
    AthleteBoardParticipant,
    AthleteBoardResultTeam,
    AthleteBoardTeam,
    BoardElement,
    BoardViewDto,
} from '@api/types.gen'
import {
    finishComplete,
    formatClockTime,
    formatPlace,
    scaled,
    sortRunningTeams,
    teamLabel,
} from '../info/athleteBoard/common'
import {byeExplanation} from '@components/event/match/matchBye.ts'
import {elementScale, slotForElement} from './boardView'

interface BoardMatchDetailElementProps {
    element: BoardElement
    view: BoardViewDto
    effectiveColumns: number
    heightFraction: number
}

/**
 * Die Sprecher-Kachel: EIN Lauf in maximaler Detailtiefe, als einziges Element eines
 * Vollbild-Boards (Backend-Validierung). Gedacht für den zweiten Browser-Tab der
 * Sprecherin — volle Aufstellung mit Jahrgängen und Vereinen, Weiterkommens-Regel,
 * Rundenzeiten und die freigegebenen Bedingungen je Person (erfüllt/offen). Der Server
 * filtert die Bedingungen auf `publicly_visible`; hier wird nur noch gezeigt.
 */
const BoardMatchDetailElement = ({
    element,
    view,
    effectiveColumns,
    heightFraction,
}: BoardMatchDetailElementProps) => {
    const {t} = useTranslation()
    // Der Freilos-Schlüssel steht erst zur Laufzeit fest — dieselbe gelockerte Signatur wie in
    // Zeitplan und Schiedsrichter-Dashboard.
    const translate = t as (key: string, values?: Record<string, string | number>) => string

    const offset = element.offset ?? 0
    const slot = slotForElement(view, element)
    const match = slot?.match ?? null
    const result = slot?.result ?? null
    const content = slot ? {match, result} : null

    // „Muss gefahren werden"-Freilos: eigene Zeile für die Sprecherin — Label mit Setzungszahl
    // („Freilos 1 …") plus die volle Begründung, direkt vorlesbar.
    const bye = byeExplanation(match?.bye)

    // Zustand für die Kopfzeile: dieselben Ableitungen wie überall (match.state vom
    // Server, Wartestand über finishComplete) — nur als ein Wort für die Ansage.
    const statusKey = result
        ? 'finished'
        : match
          ? match.state === 'PREPARING'
              ? 'preparing'
              : match.state === 'RUNNING'
                ? finishComplete(match.teams)
                    ? 'awaiting'
                    : 'running'
                : 'upcoming'
          : null

    const emptyText =
        offset === 0
            ? t('event.boards.element.emptyCurrent')
            : offset > 0
              ? t('event.boards.element.emptyUpcoming')
              : t('event.boards.element.emptyPast')

    const competitionName = match?.competitionName ?? result?.competitionName
    const roundName = match?.roundName ?? result?.roundName
    const matchName = match?.matchName ?? result?.matchName
    const categoryName = match?.categoryName ?? result?.categoryName
    const shortName = match?.competitionShortName ?? result?.competitionShortName
    const startTime = match?.startTime ?? result?.startTime
    const actualStartTime = match?.actualStartTime ?? result?.actualStartTime

    // Die Bedingungen kompakt hinter der Person: Icon + Name, erfüllt grün, offen als
    // Warnung — vorlesbar, ohne eine zweite Tabelle zu öffnen.
    const renderRequirements = (participant: AthleteBoardParticipant) =>
        (participant.requirements ?? []).map((requirement, index) => (
            <Chip
                key={index}
                size="small"
                variant="outlined"
                color={requirement.fulfilled ? 'success' : 'warning'}
                icon={
                    requirement.fulfilled ? (
                        <CheckCircleIcon fontSize="small" />
                    ) : (
                        <RadioButtonUncheckedIcon fontSize="small" />
                    )
                }
                label={requirement.name}
                sx={{
                    height: 'auto',
                    '& .MuiChip-label': {fontSize: scaled('0.65rem', '0.9vw', '1.2rem'), py: 0.1},
                }}
            />
        ))

    const renderCrew = (participants: AthleteBoardParticipant[]) => (
        <Stack gap={scaled('0.1rem', '0.15vw', '0.3rem')}>
            {participants.map((participant, index) => (
                <Stack
                    key={index}
                    direction="row"
                    gap={1}
                    alignItems="center"
                    flexWrap="wrap"
                    sx={{minWidth: 0}}>
                    <Typography sx={{fontSize: scaled('0.85rem', '1.3vw', '1.9rem')}}>
                        {[
                            participant.role
                                ? `${participant.name} (${participant.role})`
                                : participant.name,
                            participant.clubName,
                            participant.year != null
                                ? t('event.info.athleteBoard.birthYear', {year: participant.year})
                                : null,
                        ]
                            .filter(Boolean)
                            .join(' · ')}
                    </Typography>
                    {renderRequirements(participant)}
                </Stack>
            ))}
        </Stack>
    )

    const boatRow = (
        key: string,
        startNumber: number,
        team: AthleteBoardTeam | AthleteBoardResultTeam,
        trailing: {label: string | null; muted: boolean},
        subline: string | null,
        participants: AthleteBoardParticipant[],
        laps: string | null,
    ) => (
        <Stack
            key={key}
            direction="row"
            gap={scaled('0.5rem', '1vw', '1.5rem')}
            alignItems="flex-start"
            sx={{
                borderTop: '1px solid',
                borderColor: 'divider',
                py: scaled('0.3rem', '0.5vw', '0.8rem'),
                minWidth: 0,
            }}>
            <Typography
                sx={{
                    fontSize: scaled('1.2rem', '2.2vw', '3rem'),
                    fontWeight: 800,
                    minWidth: '1.6em',
                    lineHeight: 1.1,
                }}>
                {startNumber}
            </Typography>
            <Box sx={{minWidth: 0, flex: 1}}>
                <Typography sx={{fontSize: scaled('0.95rem', '1.6vw', '2.2rem'), fontWeight: 700}}>
                    {teamLabel(team, t, 'full')}
                </Typography>
                {subline && (
                    <Typography
                        sx={{fontSize: scaled('0.75rem', '1.1vw', '1.5rem')}}
                        color="text.secondary">
                        {subline}
                    </Typography>
                )}
                {renderCrew(participants)}
                {laps && (
                    <Typography
                        sx={{fontSize: scaled('0.75rem', '1.1vw', '1.5rem')}}
                        color="text.secondary">
                        {laps}
                    </Typography>
                )}
            </Box>
            {trailing.label && (
                <Typography
                    sx={{
                        fontSize: scaled('1rem', '1.7vw', '2.4rem'),
                        fontWeight: 700,
                        flexShrink: 0,
                        textAlign: 'right',
                    }}
                    color={trailing.muted ? 'text.secondary' : 'text.primary'}>
                    {trailing.label}
                </Typography>
            )}
        </Stack>
    )

    const lapsLine = (laps: {name: string; timeString: string}[] | undefined) =>
        laps && laps.length > 0
            ? laps.map(lap => `${lap.name} ${lap.timeString}`).join(' · ')
            : null

    // Laufende/anstehende Aufstellung: sobald Zwischenstände da sind, sortiert die
    // Platzierung (dieselbe Regel wie die „Im Rennen"-Karte).
    const matchRows = match
        ? sortRunningTeams(match.teams).map(team =>
              boatRow(
                  `m-${team.startNumber}`,
                  team.startNumber,
                  team,
                  {
                      // Platz und/oder Zeit, sobald vorhanden — beim Zeitfahren kommt der
                      // Platz oft vor der übertragenen Zeit an und soll nicht darauf warten.
                      label: team.failed
                          ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                          : team.place != null || team.timeString
                            ? `${team.place != null ? `${formatPlace(team.place, t)} ` : ''}${team.timeString ?? ''}`.trim()
                            : null,
                      muted: team.failed,
                  },
                  team.registeringClub
                      ? t('event.info.athleteBoard.registeringClub', {club: team.registeringClub})
                      : null,
                  team.participants,
                  lapsLine(team.laps),
              ),
          )
        : null

    const resultRows = result
        ? result.teams.map(team =>
              boatRow(
                  `r-${team.startNumber}`,
                  team.startNumber,
                  team,
                  {
                      label: team.deregistered
                          ? team.deregisteredReason
                              ? `${t('event.info.athleteBoard.deregistered')} — ${team.deregisteredReason}`
                              : t('event.info.athleteBoard.deregistered')
                          : team.failed
                            ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                            : `${team.place != null ? `${formatPlace(team.place, t)} ` : ''}${team.timeString ?? ''}`.trim() ||
                              null,
                      muted: team.failed || team.deregistered,
                  },
                  team.ratingCategory
                      ? `${team.ratingCategory.name}${team.categoryPlace != null ? ` — ${formatPlace(team.categoryPlace, t)}` : ''}`
                      : null,
                  team.participants ?? [],
                  lapsLine(team.laps),
              ),
          )
        : null

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                '--ab-scale': elementScale(element, content, effectiveColumns, heightFraction),
                // Wie jede Kachel: bei Überlauf innen scrollen statt abschneiden.
                overflow: 'auto',
                p: scaled('0.5rem', '1vw', '1.5rem'),
            }}>
            {!match && !result ? (
                <Typography
                    sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem')}}
                    color="text.secondary">
                    {emptyText}
                </Typography>
            ) : (
                <>
                    <Stack
                        direction="row"
                        justifyContent="space-between"
                        alignItems="flex-start"
                        gap={2}>
                        <Box sx={{minWidth: 0}}>
                            <Typography
                                sx={{
                                    fontSize: scaled('1.4rem', '2.6vw', '3.6rem'),
                                    fontWeight: 800,
                                }}>
                                {competitionName}
                            </Typography>
                            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                                {shortName && (
                                    <Chip label={shortName} size="small" variant="outlined" />
                                )}
                                {roundName && (
                                    <Typography
                                        sx={{fontSize: scaled('0.9rem', '1.4vw', '2rem')}}
                                        color="text.secondary">
                                        {roundName}
                                    </Typography>
                                )}
                                {matchName && matchName !== roundName && (
                                    <Chip label={matchName} size="small" variant="outlined" />
                                )}
                                {categoryName && (
                                    <Chip
                                        label={categoryName}
                                        size="small"
                                        color="primary"
                                        variant="outlined"
                                    />
                                )}
                                {statusKey && (
                                    <Chip
                                        label={t(`event.boards.detail.status.${statusKey}`)}
                                        size="small"
                                        color={
                                            statusKey === 'running'
                                                ? 'primary'
                                                : statusKey === 'awaiting'
                                                  ? 'warning'
                                                  : 'default'
                                        }
                                    />
                                )}
                            </Stack>
                            {match?.nextRoundName && (
                                <Typography
                                    sx={{
                                        fontSize: scaled('0.85rem', '1.3vw', '1.8rem'),
                                        fontWeight: 600,
                                    }}
                                    color="primary">
                                    {match.advancingSeats != null
                                        ? t('event.info.athleteBoard.advancing', {
                                              count: match.advancingSeats,
                                              round: match.nextRoundName,
                                          })
                                        : t('event.info.athleteBoard.advancingUnsized', {
                                              round: match.nextRoundName,
                                          })}
                                </Typography>
                            )}
                            {bye?.mustRace && (
                                <Typography
                                    sx={{fontSize: scaled('0.85rem', '1.3vw', '1.8rem')}}
                                    color="text.secondary">
                                    {`${translate(bye.key, bye.values)} – ${t('event.match.bye.mustRaceExplanation')}`}
                                </Typography>
                            )}
                        </Box>
                        <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                            {startTime && (
                                <Typography
                                    sx={{
                                        fontSize: scaled('1.3rem', '2.4vw', '3.2rem'),
                                        fontWeight: 700,
                                        lineHeight: 1.1,
                                    }}>
                                    {formatClockTime(startTime)}
                                </Typography>
                            )}
                            {actualStartTime && (
                                <Typography
                                    sx={{fontSize: scaled('0.8rem', '1.2vw', '1.6rem')}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.startedAt', {
                                        time: formatClockTime(actualStartTime),
                                    })}
                                </Typography>
                            )}
                        </Stack>
                    </Stack>

                    <Box sx={{mt: scaled('0.4rem', '0.7vw', '1rem')}}>
                        {matchRows ?? resultRows}
                    </Box>
                </>
            )}
        </Box>
    )
}

export default BoardMatchDetailElement
