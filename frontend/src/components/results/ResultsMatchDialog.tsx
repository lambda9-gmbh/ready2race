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
import {sortByPlaces, sortDiff} from '@utils/helpers.ts'

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
            ? match.teams.sort((a, b) => sortDiff(a.startNumber, b.startNumber) ?? 0)
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
                            <Box>
                                <Chip
                                    label={match.categoryName}
                                    variant={'outlined'}
                                    color={'primary'}
                                    size={'small'}
                                />
                            </Box>
                        </Stack>
                    </DialogTitle>
                    <DialogContent>
                        <Stack spacing={2}>
                            {(match.startTime || match.eventDayDate) && (
                                <Stack direction={'row'} spacing={1}>
                                    <ScheduleOutlinedIcon color={'primary'} />
                                    <Typography>
                                        {match.startTime
                                            ? format(
                                                  new Date(match.startTime),
                                                  t('format.datetime'),
                                              )
                                            : format(
                                                  new Date(match.eventDayDate!),
                                                  t('format.date'),
                                              ) +
                                              (match.eventDayName
                                                  ? ` (${match.eventDayName})`
                                                  : '')}
                                    </Typography>
                                </Stack>
                            )}
                            {sortedTeams.map(team => (
                                <Card key={team.teamId}>
                                    <CardContent>
                                        <Stack
                                            direction={'row'}
                                            sx={{
                                                alignItems: 'center',
                                                justifyContent: 'space-between',
                                            }}>
                                            {'place' in team && (
                                                <Typography variant={'h5'}>
                                                    {/*todo with changes*/}
                                                    {team.place}.
                                                </Typography>
                                            )}
                                            <Typography>
                                                {team.clubName +
                                                    (team.teamName ? ` ${team.teamName}` : '')}
                                            </Typography>
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
                                                            secondary={participant.namedRole}
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
