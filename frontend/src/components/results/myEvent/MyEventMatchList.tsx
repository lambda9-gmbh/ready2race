import {Fragment, useState} from 'react'
import {Box, Card, CardContent, Chip, Collapse, Stack, Typography} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
    MyEventMatchDto,
    MyEventRegistrationDto,
    MyEventResultDto,
    MyEventTeamMemberDto,
} from '@api/types.gen.ts'
import AthleteBoardPenaltyNote from '@components/event/info/athleteBoard/AthleteBoardPenaltyNote.tsx'
import {
    formatClockTime,
    formatRemaining,
    formatShortDate,
    isSameDay,
} from '@components/event/info/athleteBoard/common.ts'
import PlaceOrdinal from '@components/PlaceOrdinal'
import {useServerClock} from '@components/event/info/athleteBoard/useServerClock.ts'
import {groupByDay} from './myEventDays.ts'
import {MyEventResultField} from './MyEventResultField.tsx'

type MyEventMatchListProps = {
    matches: MyEventMatchDto[]
    // Zeitpunkt der Antwort. Zwischen zwei Abrufen läuft die Uhr lokal weiter, aber gerechnet
    // wird gegen die Uhr des Servers: ein falsch gestelltes Telefon soll den Countdown nicht
    // um Stunden verschieben.
    serverTime: string
    // "next": nur der erste Eintrag, groß, mit Countdown. "list": alle als kompakte Zeilen.
    variant: 'next' | 'list'
    // Nur in "list": der Lauf, der oben schon als große Karte steht. Die Zeile wird markiert,
    // damit die Zuordnung zwischen Karte und Tagesplan ohne Nachdenken gelingt.
    highlightedMatchId?: string
    // Bei einer Regatta über mehrere Tage trennen Datums-Zwischenüberschriften die Läufe —
    // ob sie nötig sind, entscheidet der Aufrufer einmal für die ganze Seite (myEventDays).
    showDays?: boolean
}

type MyEventResultListProps = {
    results: MyEventResultDto[]
    // Für das Nachladen des kompletten Feldes beim Antippen eines Ergebnisses.
    eventId: string
    // Wie an der Laufliste: Tagesüberschriften nur, wenn die Seite mehrere Tage berührt.
    showDays?: boolean
}

type MyEventUnscheduledListProps = {registrations: MyEventRegistrationDto[]}

const competitionSubtitle = (match: {
    roundName?: string | null
    matchName?: string | null
    categoryName?: string | null
}) =>
    [
        match.roundName,
        match.matchName && match.matchName !== match.roundName ? match.matchName : null,
        match.categoryName,
    ]
        .filter(Boolean)
        .join(' · ')

/**
 * Die eigene Person steht fett in der Aufzählung: auf dem Handy im Vorstartbereich ist die
 * Frage "bin ich in dieser Mannschaft gemeint" wichtiger als die Reihenfolge der Namen.
 */
const TeamMemberLine = ({members}: {members: MyEventTeamMemberDto[]}) => {
    if (members.length === 0) return null
    return (
        <Typography variant="body2" color="text.secondary" sx={{mt: 0.5}}>
            {members.map((m, index) => (
                <Box
                    component="span"
                    key={`${m.name}-${index}`}
                    sx={{
                        fontWeight: m.self ? 700 : 400,
                        color: m.self ? 'text.primary' : undefined,
                    }}>
                    {m.role ? `${m.name} (${m.role})` : m.name}
                    {index < members.length - 1 ? ', ' : ''}
                </Box>
            ))}
        </Typography>
    )
}

/**
 * Datums-Zwischenüberschrift über einer Tagesgruppe — dasselbe Format wie die
 * Tagesüberschriften des Zeitplans und der öffentlichen Programmansicht (`format.date`).
 */
const DayHeading = ({startTime}: {startTime: string}) => {
    const {t} = useTranslation()
    return (
        <Typography variant="body2" color="text.secondary" sx={{fontWeight: 700, pt: 1, pb: 0.25}}>
            {format(new Date(startTime), t('format.date'))}
        </Typography>
    )
}

const MatchHeading = ({match}: {match: MyEventMatchDto}) => {
    const subtitle = competitionSubtitle(match)
    return (
        <Box sx={{minWidth: 0}}>
            <Typography sx={{fontWeight: 700}}>{match.competitionName}</Typography>
            {subtitle && (
                <Typography variant="body2" color="text.secondary">
                    {subtitle}
                </Typography>
            )}
        </Box>
    )
}

/**
 * Ein zurückgezogenes Boot, das noch kein öffentliches Ergebnis trägt, steht weiter unter den
 * kommenden Läufen — es zu verstecken wäre die unehrlichere Antwort. Damit niemand trotzdem an
 * den Start geht, trägt es hier die Abmeldung samt Grund und keinen Countdown.
 */
const DeregisteredNote = ({match}: {match: MyEventMatchDto}) => {
    const {t} = useTranslation()

    if (!match.deregistered) return null

    return (
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{mt: 1}}>
            <Chip size="small" color="warning" label={t('myEvent.deregistered')} />
            {match.deregisteredReason && (
                <Typography variant="body2" color="text.secondary">
                    {match.deregisteredReason}
                </Typography>
            )}
        </Stack>
    )
}

const MyEventNextCard = ({match, serverTime}: {match: MyEventMatchDto; serverTime: string}) => {
    const {t} = useTranslation()
    // Der Sekundentakt hängt an dieser Karte und nicht an der Liste: nur hier steht eine
    // Zahl, die sich jede Sekunde ändert.
    const now = useServerClock(serverTime)

    const startsInSeconds =
        match.startTime && !match.deregistered
            ? (new Date(match.startTime).getTime() - now.getTime()) / 1000
            : null

    // Der Server liefert den Zustand beim Abruf; zwischen zwei Abrufen läuft die Uhr weiter,
    // deshalb wird der Übergang zu "erwartet" hier noch einmal geprüft. Bei einer Abmeldung
    // entfällt beides: weder Countdown noch "erwartet" sagen dann noch etwas Wahres.
    const overdue =
        !match.deregistered &&
        (match.startState === 'OVERDUE' || (startsInSeconds !== null && startsInSeconds <= 0))

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="flex-start"
                    gap={1}>
                    <MatchHeading match={match} />
                    <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                        {match.startTime ? (
                            <>
                                {/* Ein Start an einem anderen Kalendertag bekommt sein Datum
                                    dazu: "10:30" allein liest sich sonst wie heute, auch wenn
                                    der Lauf erst morgen stattfindet — dieselbe Regel wie auf
                                    der Wandanzeige (AthleteBoardMatchCard). */}
                                {!isSameDay(new Date(match.startTime), now) && (
                                    <Typography variant="body2" color="text.secondary">
                                        {formatShortDate(match.startTime)}
                                    </Typography>
                                )}
                                <Typography
                                    sx={{
                                        fontSize: '1.8rem',
                                        fontWeight: 700,
                                        lineHeight: 1.1,
                                        textDecoration: match.deregistered
                                            ? 'line-through'
                                            : undefined,
                                    }}
                                    color={match.deregistered ? 'text.secondary' : undefined}>
                                    {formatClockTime(match.startTime)}
                                </Typography>
                                {match.deregistered ? null : match.actualStartTime ? (
                                    <Typography variant="body2" color="text.secondary">
                                        {t('event.info.athleteBoard.startedAt', {
                                            time: formatClockTime(match.actualStartTime),
                                        })}
                                    </Typography>
                                ) : overdue ? (
                                    <Typography variant="body2" color="text.secondary">
                                        {t('event.info.athleteBoard.expected')}
                                    </Typography>
                                ) : (
                                    startsInSeconds !== null && (
                                        <Typography variant="body2" color="text.secondary">
                                            {t('event.info.athleteBoard.startsIn', {
                                                time: formatRemaining(startsInSeconds, t),
                                            })}
                                        </Typography>
                                    )
                                )}
                            </>
                        ) : (
                            <Typography color="text.secondary">
                                {t('event.info.athleteBoard.unscheduled')}
                            </Typography>
                        )}
                    </Stack>
                </Stack>

                <DeregisteredNote match={match} />

                <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{mt: 1.5}}>
                    {/* Die Startposition bleibt bei einer Abmeldung weg: "Startposition 3"
                        neben einem zurückgezogenen Boot liest sich wie eine Anweisung. */}
                    {match.lane != null && !match.deregistered && (
                        <Chip
                            size="small"
                            color="primary"
                            label={`${t('myEvent.lane')} ${match.lane}`}
                        />
                    )}
                    {(match.teamName || match.clubName) && (
                        <Typography variant="body2">
                            {[match.clubName, match.teamName].filter(Boolean).join(' | ')}
                        </Typography>
                    )}
                </Stack>
                <TeamMemberLine members={match.teamMembers} />
            </CardContent>
        </Card>
    )
}

const MyEventMatchRow = ({match, current}: {match: MyEventMatchDto; current?: boolean}) => {
    const {t} = useTranslation()
    const subtitle = competitionSubtitle(match)

    return (
        <Stack
            direction="row"
            gap={1.5}
            alignItems="flex-start"
            sx={{
                py: 1,
                // Die markierte Zeile ist derselbe Lauf wie die Karte darüber. Ein farbiger
                // Balken links reicht dafür — ein zweites Mal "nächster Lauf" hinzuschreiben
                // würde die Zeile nur verbreitern.
                ...(current
                    ? {
                          borderLeft: 3,
                          borderColor: 'primary.main',
                          pl: 1,
                          bgcolor: 'action.hover',
                      }
                    : {borderLeft: 3, borderColor: 'transparent', pl: 1}),
            }}>
            <Typography
                sx={{
                    fontWeight: 700,
                    minWidth: '4.5em',
                    flexShrink: 0,
                    textDecoration: match.deregistered ? 'line-through' : undefined,
                }}
                color={match.startTime && !match.deregistered ? 'text.primary' : 'text.secondary'}>
                {match.startTime
                    ? formatClockTime(match.startTime)
                    : t('event.info.athleteBoard.unscheduled')}
            </Typography>
            <Box sx={{flex: 1, minWidth: 0}}>
                <Typography sx={{fontWeight: 600}}>{match.competitionName}</Typography>
                {subtitle && (
                    <Typography variant="body2" color="text.secondary">
                        {subtitle}
                    </Typography>
                )}
                {match.teamName && (
                    <Typography variant="body2" color="text.secondary">
                        {t('myEvent.team')}: {match.teamName}
                    </Typography>
                )}
                {/* Der Grund gehört in die Zeile und nicht nur an die große Karte: im
                    Tagesplan ist diese Zeile alles, was von dem Lauf zu sehen ist. */}
                {match.deregistered && match.deregisteredReason && (
                    <Typography variant="body2" color="text.secondary">
                        {match.deregisteredReason}
                    </Typography>
                )}
            </Box>
            {match.deregistered ? (
                <Chip size="small" color="warning" label={t('myEvent.deregistered')} />
            ) : (
                match.lane != null && (
                    <Chip
                        size="small"
                        variant="outlined"
                        label={`${t('myEvent.lane')} ${match.lane}`}
                    />
                )
            )}
        </Stack>
    )
}

export const MyEventMatchList = ({
    matches,
    serverTime,
    variant,
    highlightedMatchId,
    showDays,
}: MyEventMatchListProps) => {
    if (matches.length === 0) {
        return null
    }

    if (variant === 'next') {
        return <MyEventNextCard match={matches[0]} serverTime={serverTime} />
    }

    return (
        <Stack>
            {/* Nach Tagen geclustert: bei einer mehrtägigen Regatta trennt eine Datumszeile
                die Blöcke, sonst besteht die Liste aus genau einer Gruppe ohne Überschrift.
                Gruppen ohne Startzeit (am Ende) tragen keine — die Zeile sagt selbst
                „noch nicht terminiert". */}
            {/* Der Index gehört mit in den Schlüssel: derselbe Tag kann zweimal vorkommen,
                wenn ein überfälliger Lauf ans Ende gewandert ist. */}
            {groupByDay(matches).map((group, groupIndex) => (
                <Fragment key={`${group.day ?? 'ohne-tag'}-${groupIndex}`}>
                    {showDays && group.day && <DayHeading startTime={group.items[0].startTime!} />}
                    <Stack divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                        {group.items.map(match => (
                            <MyEventMatchRow
                                key={match.matchId}
                                match={match}
                                current={match.matchId === highlightedMatchId}
                            />
                        ))}
                    </Stack>
                </Fragment>
            ))}
        </Stack>
    )
}

export const MyEventResultList = ({results, eventId, showDays}: MyEventResultListProps) => {
    const {t} = useTranslation()
    // Genau ein aufgeklapptes Feld: wer das nächste öffnet, schließt das vorige. Das hält
    // die Liste auf dem Telefon kurz und spart die Abrufe gleichzeitig offener Felder.
    const [expandedMatchId, setExpandedMatchId] = useState<string | null>(null)

    if (results.length === 0) {
        return null
    }

    const renderResult = (result: MyEventResultDto) => {
        const subtitle = competitionSubtitle(result)
        const expanded = expandedMatchId === result.matchId
        return (
            <Box key={result.matchId}>
                <Stack
                    direction="row"
                    gap={1.5}
                    alignItems="center"
                    role="button"
                    aria-expanded={expanded}
                    onClick={() =>
                        setExpandedMatchId(current =>
                            current === result.matchId ? null : result.matchId,
                        )
                    }
                    sx={{py: 1, cursor: 'pointer'}}>
                    {/* Der Platz trägt die Zeile; ohne Wertung bleibt der Strich stehen,
                                damit die Zeilen untereinander nicht verspringen. */}
                    <Typography
                        sx={{
                            fontSize: '1.6rem',
                            fontWeight: 800,
                            lineHeight: 1,
                            minWidth: '1.8em',
                            textAlign: 'center',
                        }}
                        color={result.place != null ? 'text.primary' : 'text.secondary'}>
                        {result.place != null ? <PlaceOrdinal place={result.place} /> : '–'}
                    </Typography>
                    <Box sx={{flex: 1, minWidth: 0}}>
                        <Typography sx={{fontWeight: 600}}>{result.competitionName}</Typography>
                        {subtitle && (
                            <Typography variant="body2" color="text.secondary">
                                {subtitle}
                            </Typography>
                        )}
                        {result.startTime && (
                            <Typography variant="body2" color="text.secondary">
                                {formatClockTime(result.startTime)}
                            </Typography>
                        )}
                    </Box>
                    <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '50%'}}>
                        <Typography
                            sx={{fontWeight: 600, textAlign: 'right'}}
                            color={
                                result.failed || result.deregistered
                                    ? 'text.secondary'
                                    : 'text.primary'
                            }>
                            {result.deregistered
                                ? [
                                      t('event.info.athleteBoard.deregistered'),
                                      result.deregisteredReason,
                                  ]
                                      .filter(Boolean)
                                      .join(' · ')
                                : result.failed
                                  ? (result.failedReason ?? t('event.info.athleteBoard.failed'))
                                  : (result.timeString ?? '')}
                        </Typography>
                        {!result.deregistered && (
                            <AthleteBoardPenaltyNote
                                penaltySeconds={result.penaltySeconds}
                                penaltyNote={result.penaltyNote}
                            />
                        )}
                    </Stack>
                    {/* Der gedrehte Pfeil ist die einzige Einladung zum Antippen —
                                die Zeile selbst sieht aus wie zuvor. */}
                    <ExpandMoreIcon
                        fontSize="small"
                        sx={{
                            color: 'text.secondary',
                            flexShrink: 0,
                            transform: expanded ? 'rotate(180deg)' : 'none',
                            transition: 'transform 150ms',
                        }}
                    />
                </Stack>
                {/* unmountOnExit: erst das Aufklappen löst den einmaligen Abruf des
                            Feldes aus, und Zuklappen wirft ihn wieder weg. */}
                <Collapse in={expanded} unmountOnExit>
                    <MyEventResultField eventId={eventId} result={result} />
                </Collapse>
            </Box>
        )
    }

    return (
        <Stack>
            {/* Wie an der Laufliste: die Ergebnisse kommen vom Server neuestes zuerst, die
                Tagesgruppen folgen dieser Reihenfolge — der jüngste Tag steht also oben. */}
            {groupByDay(results).map((group, groupIndex) => (
                <Fragment key={`${group.day ?? 'ohne-tag'}-${groupIndex}`}>
                    {showDays && group.day && <DayHeading startTime={group.items[0].startTime!} />}
                    <Stack divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                        {group.items.map(renderResult)}
                    </Stack>
                </Fragment>
            ))}
        </Stack>
    )
}

export const MyEventUnscheduledList = ({registrations}: MyEventUnscheduledListProps) => {
    const {t} = useTranslation()

    if (registrations.length === 0) {
        return null
    }

    return (
        <Stack divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
            {registrations.map(registration => (
                <Stack
                    // Rolle mit im Schlüssel: wer im selben Wettkampf in zwei Rollen gemeldet
                    // ist und keinen Mannschaftsnamen trägt, ergäbe sonst zweimal denselben.
                    key={`${registration.competitionId}-${registration.teamName ?? ''}-${registration.role ?? ''}`}
                    direction="row"
                    gap={1.5}
                    alignItems="center"
                    sx={{py: 1}}>
                    <Chip
                        size="small"
                        variant="outlined"
                        label={registration.competitionIdentifier}
                        sx={{flexShrink: 0}}
                    />
                    <Box sx={{flex: 1, minWidth: 0}}>
                        <Typography sx={{fontWeight: 600}}>
                            {registration.competitionName}
                        </Typography>
                        {(registration.categoryName ||
                            registration.teamName ||
                            registration.role) && (
                            <Typography variant="body2" color="text.secondary">
                                {[
                                    registration.categoryName,
                                    registration.teamName
                                        ? `${t('myEvent.team')}: ${registration.teamName}`
                                        : null,
                                    registration.role,
                                ]
                                    .filter(Boolean)
                                    .join(' · ')}
                            </Typography>
                        )}
                    </Box>
                </Stack>
            ))}
        </Stack>
    )
}
