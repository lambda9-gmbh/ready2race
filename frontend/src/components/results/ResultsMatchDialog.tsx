import {
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Grid2,
    ListItemText,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined'
import {format} from 'date-fns'
import BaseDialog from '@components/BaseDialog.tsx'
import {useTranslation} from 'react-i18next'
import {ResultsMatchInfo} from '@components/results/ResultsMatchCard.tsx'

import {sortByPlaces, compareNullsHigh} from '@utils/helpers.ts'
import {groupByRatingCategory, hasRatingCategories} from '@utils/ratingCategorySections.ts'
import {failedLabel} from '@utils/matchResultStatus.ts'
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'

type Props<M extends ResultsMatchInfo> = {
    match: M | null
    dialogOpen: boolean
    closeDialog: () => void
}

const ResultsMatchDialog = <M extends ResultsMatchInfo>({
    match,
    dialogOpen,
    closeDialog,
}: Props<M>) => {
    const {t} = useTranslation()
    const theme = useTheme()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    // Der Dialog bedient zwei Formen: das Ergebnis eines gefahrenen Laufs und den laufenden Lauf.
    // Ohne die gemeinsame Annotation bliebe hier eine Vereinigung zweier Listen stehen, die sich
    // nicht am Stück gruppieren lässt.
    const sortedTeams: ResultsMatchInfo['teams'][number][] = match
        ? 'executionOrder' in match
            ? match.teams.sort((a, b) => compareNullsHigh(a.startNumber, b.startNumber))
            : sortByPlaces(match.teams)
        : []

    // Ergebnisse werden je Wertungskategorie getrennt gezeigt und je Abschnitt ab 1 gezählt. Ein
    // Lauf ohne Kategorien liefert genau einen namenlosen Abschnitt - dann bleibt die Anzeige die
    // gewohnte gemeinsame Liste, ohne Überschrift.
    const sections = groupByRatingCategory(sortedTeams, team =>
        'ratingCategory' in team ? team.ratingCategory : null,
    )
    const showSectionHeadings = hasRatingCategories(sections)

    return (
        <BaseDialog
            open={dialogOpen}
            onClose={closeDialog}
            fullScreen={smallScreenLayout}
            maxWidth={!smallScreenLayout ? 'md' : undefined}>
            {match && (
                <>
                    <DialogTitle>
                        <Stack>
                            <Typography variant={match.matchName ? 'body2' : 'h6'}>
                                {match.competitionName} - {match.roundName}
                            </Typography>
                            {match.matchName ?? ''}
                            {match.categoryName && (
                                <Box>
                                    <Chip
                                        label={match.categoryName}
                                        variant={'outlined'}
                                        color={'primary'}
                                        size={'small'}
                                    />
                                </Box>
                            )}
                        </Stack>
                    </DialogTitle>
                    <DialogContent>
                        <Stack spacing={2}>
                            {match.startTime && (
                                <Stack direction={'row'} spacing={1}>
                                    <ScheduleOutlinedIcon color={'primary'} />
                                    <Typography>
                                        {format(new Date(match.startTime), t('format.datetime'))}
                                    </Typography>
                                </Stack>
                            )}
                            {sections.map(section => (
                                <Stack spacing={2} key={section.category?.id ?? 'none'}>
                                    {showSectionHeadings && (
                                        <Typography variant={'subtitle1'} fontWeight={'bold'}>
                                            {section.category?.name ??
                                                t('event.ratingCategory.withoutCategory')}
                                        </Typography>
                                    )}
                                    {section.entries.map(team => (
                                        <Card key={team.teamId}>
                                            <CardContent>
                                                <Stack
                                                    spacing={4}
                                                    direction={'row'}
                                                    sx={{
                                                        justifyContent: 'space-between',
                                                    }}>
                                                    {/* Unterscheidet die Ergebnis-Mannschaft von der
                                                eines laufenden Laufs. Bewusst an `deregistered`:
                                                Zeit, Zeitstrafe und `failed` trägt inzwischen
                                                auch die laufende Mannschaft (Teilergebnisse). */}
                                                    {'deregistered' in team ? (
                                                        <Box>
                                                            {/* Gezeigt wird der Platz innerhalb der
                                                        Wertungskategorie; team.place bleibt der
                                                        Platz im Lauf und ist nur seine Grundlage. */}
                                                            <Typography
                                                                variant={
                                                                    team.categoryPlace
                                                                        ? 'h5'
                                                                        : 'body1'
                                                                }>
                                                                {team.categoryPlace
                                                                    ? `${team.categoryPlace}.`
                                                                    : team.failed
                                                                      ? failedLabel(
                                                                            team.failedReason,
                                                                            t(
                                                                                'event.competition.execution.results.failed',
                                                                            ),
                                                                        )
                                                                      : team.deregistered
                                                                        ? t(
                                                                              'event.competition.registration.deregister.deregistered',
                                                                          ) +
                                                                          (team.deregisteredReason
                                                                              ? ` (${team.deregisteredReason})`
                                                                              : '')
                                                                        : ''}
                                                            </Typography>
                                                            {!team.failed && team.timeString && (
                                                                <Box
                                                                    display="flex"
                                                                    gap={1}
                                                                    alignItems={'center'}>
                                                                    <TimerOutlinedIcon
                                                                        color={'action'}
                                                                        fontSize={'inherit'}
                                                                    />
                                                                    <Typography
                                                                        color={'textSecondary'}
                                                                        variant={'body2'}>
                                                                        {team.timeString}
                                                                    </Typography>
                                                                </Box>
                                                            )}
                                                            {team.penaltySeconds != null && (
                                                                <Typography
                                                                    color={'warning.main'}
                                                                    variant={'body2'}>
                                                                    {t(
                                                                        'event.competition.execution.results.penalty',
                                                                        {
                                                                            seconds:
                                                                                team.penaltySeconds,
                                                                        },
                                                                    )}
                                                                    {team.penaltyNote
                                                                        ? ` · ${team.penaltyNote}`
                                                                        : ''}
                                                                </Typography>
                                                            )}
                                                        </Box>
                                                    ) : (
                                                        <Box></Box>
                                                    )}
                                                    <Box>
                                                        <Typography textAlign={'right'}>
                                                            {team.clubsFull ?? team.clubName}
                                                        </Typography>
                                                        <Typography
                                                            color={'textSecondary'}
                                                            variant={'body2'}
                                                            textAlign={'right'}>
                                                            {[
                                                                t('club.registeredBy') +
                                                                    ' ' +
                                                                    team.clubName,
                                                                team.teamName,
                                                            ]
                                                                .filter(Boolean)
                                                                .join(' | ')}
                                                        </Typography>
                                                    </Box>
                                                </Stack>
                                                <Divider sx={{my: 1}} />
                                                <Grid2 container>
                                                    {team.participants
                                                        .sort((a, b) =>
                                                            a.namedRole === b.namedRole
                                                                ? a.firstName === b.firstName
                                                                    ? a.lastName > b.lastName
                                                                        ? 1
                                                                        : -1
                                                                    : a.firstName > b.firstName
                                                                      ? 1
                                                                      : -1
                                                                : (a.namedRole ?? '') >
                                                                    (b.namedRole ?? '')
                                                                  ? 1
                                                                  : -1,
                                                        )
                                                        .map(participant => (
                                                            <Grid2
                                                                size={6}
                                                                key={participant.participantId}>
                                                                <ListItemText
                                                                    primary={
                                                                        participant.firstName +
                                                                        ' ' +
                                                                        participant.lastName +
                                                                        // Jahrgang hinter dem Namen
                                                                        // (Wunsch von Lea, 10.08.2026)
                                                                        (participant.year != null
                                                                            ? ` (${participant.year})`
                                                                            : '')
                                                                    }
                                                                    secondary={
                                                                        <>
                                                                            <Typography
                                                                                variant="body2"
                                                                                color="text.secondary">
                                                                                {
                                                                                    participant.namedRole
                                                                                }
                                                                            </Typography>
                                                                            <Typography
                                                                                variant="body2"
                                                                                color="text.secondary">
                                                                                {participant.externalClubName ??
                                                                                    team.clubName}
                                                                            </Typography>
                                                                        </>
                                                                    }
                                                                />
                                                            </Grid2>
                                                        ))}
                                                </Grid2>
                                            </CardContent>
                                        </Card>
                                    ))}
                                </Stack>
                            ))}
                        </Stack>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeDialog}>{t('common.close')}</Button>
                    </DialogActions>
                </>
            )}
        </BaseDialog>
    )
}

export default ResultsMatchDialog
