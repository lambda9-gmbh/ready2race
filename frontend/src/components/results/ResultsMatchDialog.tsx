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

    const sortedTeams = match
        ? 'executionOrder' in match
            ? match.teams.sort((a, b) => compareNullsHigh(a.startNumber, b.startNumber))
            : sortByPlaces(match.teams)
        : []

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
                            {sortedTeams.map(team => (
                                <Card key={team.teamId}>
                                    <CardContent>
                                        <Stack
                                            spacing={4}
                                            direction={'row'}
                                            sx={{
                                                justifyContent: 'space-between',
                                            }}>
                                            {'failed' in team ? (
                                                <Box>
                                                    <Typography
                                                        variant={team.place ? 'h5' : 'body1'}>
                                                        {team.place
                                                            ? `${team.place}.`
                                                            : team.failed
                                                              ? t(
                                                                    'event.competition.execution.results.failed',
                                                                ) +
                                                                (team.failedReason
                                                                    ? ` (${team.failedReason})`
                                                                    : '')
                                                              : team.deregistered
                                                                ? t(
                                                                      'event.competition.registration.deregister.deregistered',
                                                                  ) +
                                                                  (team.deregisteredReason
                                                                      ? ` (${team.deregisteredReason})`
                                                                      : '')
                                                                : ''}
                                                    </Typography>
                                                    {'failed' in team &&
                                                        !team.failed &&
                                                        team.timeString && (
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
                                                </Box>
                                            ) : (
                                                <Box></Box>
                                            )}
                                            <Box>
                                                <Typography textAlign={'right'}>
                                                    {team.actualClubName ?? team.clubName}
                                                </Typography>
                                                <Typography
                                                    color={'textSecondary'}
                                                    variant={'body2'}
                                                    textAlign={'right'}>
                                                    {`${t('club.registeredBy')} ` +
                                                        team.clubName +
                                                        ` | ${team.teamName}`}
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
                                                        : (a.namedRole ?? '') > (b.namedRole ?? '')
                                                          ? 1
                                                          : -1,
                                                )
                                                .map(participant => (
                                                    <Grid2 size={6} key={participant.participantId}>
                                                        <ListItemText
                                                            primary={
                                                                participant.firstName +
                                                                ' ' +
                                                                participant.lastName
                                                            }
                                                            secondary={
                                                                <>
                                                                    <Typography
                                                                        variant="body2"
                                                                        color="text.secondary">
                                                                        {participant.namedRole}
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
