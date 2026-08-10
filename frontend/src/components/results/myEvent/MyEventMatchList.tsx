import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {
    MyEventMatchDto,
    MyEventRegistrationDto,
    MyEventResultDto,
    MyEventTeamMemberDto,
} from '@api/types.gen.ts'
import AthleteBoardPenaltyNote from '@components/event/info/athleteBoard/AthleteBoardPenaltyNote.tsx'
import {formatClockTime, formatRemaining} from '@components/event/info/athleteBoard/common.ts'
import {useServerClock} from '@components/event/info/athleteBoard/useServerClock.ts'

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
}

type MyEventResultListProps = {results: MyEventResultDto[]}

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
}: MyEventMatchListProps) => {
    if (matches.length === 0) {
        return null
    }

    if (variant === 'next') {
        return <MyEventNextCard match={matches[0]} serverTime={serverTime} />
    }

    return (
        <Stack divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
            {matches.map(match => (
                <MyEventMatchRow
                    key={match.matchId}
                    match={match}
                    current={match.matchId === highlightedMatchId}
                />
            ))}
        </Stack>
    )
}

export const MyEventResultList = ({results}: MyEventResultListProps) => {
    const {t} = useTranslation()

    if (results.length === 0) {
        return null
    }

    return (
        <Stack divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
            {results.map(result => {
                const subtitle = competitionSubtitle(result)
                return (
                    <Stack
                        key={result.matchId}
                        direction="row"
                        gap={1.5}
                        alignItems="center"
                        sx={{py: 1}}>
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
                            {result.place ?? '–'}
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
                    </Stack>
                )
            })}
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
