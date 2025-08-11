import {CompetitionMatchDto, CompetitionRoundDto, StartListFileType} from '@api/types.gen.ts'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Card,
    Divider,
    FormControlLabel,
    Link,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    useTheme,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Substitutions from '@components/event/competition/excecution/Substitutions.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {Fragment, SyntheticEvent, useRef, useState} from 'react'
import {
    deleteCurrentCompetitionExecutionRound,
    downloadStartList,
    updateMatchRunningState,
    uploadResultFile,
} from '@api/sdk.gen.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {competitionRoute, eventRoute} from '@routes'
import SelectionMenu from '@components/SelectionMenu.tsx'
import {format} from 'date-fns'
import StartListConfigPicker from '@components/event/competition/excecution/StartListConfigPicker.tsx'
import Checkbox from '@mui/material/Checkbox'
import MatchResultUploadDialog from '@components/event/competition/excecution/MatchResultUploadDialog.tsx'
import {getFilename} from '@utils/helpers.ts'

type Props = {
    round: CompetitionRoundDto
    roundIndex: number
    filteredMatches: CompetitionMatchDto[]
    reloadRoundDto: () => void
    setSubmitting: (value: boolean) => void
    submitting: boolean
    openResultsDialog: (matchIndex: number) => void
    openEditMatchDialog: (roundIndex: number, matchIndex: number) => void
    accordionsExpanded: boolean[] | undefined
    handleAccordionExpandedChange: (accordionIndex: number, isExpanded: boolean) => void
    smallScreenLayout: boolean
}

const MATCH_RESULT_OPTIONS = ['form', 'XLS'] as const
type MatchResultOption = (typeof MATCH_RESULT_OPTIONS)[number]

const CompetitionExecutionRound = ({
    round,
    roundIndex,
    filteredMatches,
    submitting,
    smallScreenLayout,
    ...props
}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const {confirmAction} = useConfirmation()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [startListMatch, setStartListMatch] = useState<string | null>(null)
    const showStartListConfigDialog = startListMatch !== null
    const closeStartListConfigDialog = () => setStartListMatch(null)

    const [resultImportMatch, setResultImportMatch] = useState<string | null>(null)
    const showMatchResultImportConfigDialog = resultImportMatch !== null
    const closeMatchResultImportConfigDialog = () => setResultImportMatch(null)

    const deleteCurrentRound = async () => {
        confirmAction(
            async () => {
                props.setSubmitting(true)
                const {error} = await deleteCurrentCompetitionExecutionRound({
                    path: {
                        eventId: eventId,
                        competitionId: competitionId,
                    },
                })
                props.setSubmitting(false)
                if (error) {
                    feedback.error(t('event.competition.execution.deleteRound.error'))
                } else {
                    feedback.success(t('event.competition.execution.deleteRound.success'))
                }
                props.reloadRoundDto()
            },
            {
                content: t('event.competition.execution.deleteRound.confirmation.content'),
                okText: t('common.delete'),
            },
        )
    }

    const handleAccordionExpandedChange =
        (accordionIndex: number) => (_: SyntheticEvent, isExpanded: boolean) => {
            props.handleAccordionExpandedChange(accordionIndex, isExpanded)
        }

    const handleDownloadStartList = async (
        competitionMatchId: string,
        fileType: StartListFileType,
        config?: string,
    ) => {
        const {data, error, response} = await downloadStartList({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            query: {
                fileType,
                config,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data !== undefined && anchor) {
            // need Blob constructor for text/csv
            anchor.href = URL.createObjectURL(new Blob([data])) // TODO: @Memory: revokeObjectURL() when done
            anchor.download =
                getFilename(response) ?? `startList-${competitionMatchId}.${fileType.toLowerCase()}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handleUploadMatchResults = async (
        competitionMatchId: string,
        file: File,
        config: string,
    ) => {
        const {error} = await uploadResultFile({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            body: {
                request: {config},
                files: [file],
            },
        })

        if (error) {
            if (error.status.value === 400) {
                if (error.errorCode === 'FILE_ERROR') {
                    feedback.error(t('event.competition.execution.results.error.FILE_ERROR'))
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
            } else if (error.status.value === 422) {
                const details = 'details' in error && error.details
                switch (error.errorCode) {
                    case 'SPREADSHEET_NO_HEADERS':
                        feedback.error(t('event.competition.execution.results.error.NO_HEADERS'))
                        break
                    case 'SPREADSHEET_COLUMN_UNKNOWN':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.COLUMN_UNKNOWN',
                                details as {expected: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_CELL_BLANK':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.CELL_BLANK',
                                details as {row: number; column: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_WRONG_CELL_TYPE':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.WRONG_CELL_TYPE',
                                details as {
                                    row: number
                                    column: string
                                    actual: string
                                    expected: string
                                },
                            ),
                        )
                        break
                    case 'WRONG_TEAM_COUNT':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.WRONG_TEAM_COUNT',
                                details as {actual: number; expected: number},
                            ),
                        )
                        break
                    case 'DUPLICATE_PLACES':
                        feedback.error(
                            t('event.competition.execution.results.error.DUPLICATE_PLACES'),
                        )
                        break
                    case 'DUPLICATE_START_NUMBERS':
                        feedback.error(
                            t('event.competition.execution.results.error.DUPLICATE_START_NUMBERS'),
                        )
                        break
                    case 'PLACES_UNCONTINUOUS':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.PLACES_UNCONTINUOUS',
                                details as {actual: number; expected: number},
                            ),
                        )
                        break
                    default:
                        feedback.error(t('common.error.unexpected'))
                        break
                }
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            feedback.success(t('event.competition.execution.results.submit.success'))
        }

        props.reloadRoundDto()
    }

    const handleToggleRunningState = async (match: CompetitionMatchDto) => {
        // Check if match has no places set
        const hasPlacesSet = match.teams.some(
            team => team.place !== null && team.place !== undefined,
        )
        if (hasPlacesSet) {
            feedback.error(t('event.competition.execution.running.error.hasPlaces'))
            return
        }

        props.setSubmitting(true)
        const {error} = await updateMatchRunningState({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionMatchId: match.id,
            },
            body: {
                currentlyRunning: !match.currentlyRunning,
            },
        })
        props.setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.execution.running.error.update'))
        } else {
            feedback.success(t('event.competition.execution.running.success'))
            props.reloadRoundDto()
        }
    }

    return (
        <Fragment>
            <Link ref={downloadRef} display={'none'}></Link>
            <Stack
                spacing={2}
                sx={{
                    borderLeft: 1,
                    borderColor: theme.palette.primary.main,
                    pl: 4,
                    py: 2,
                }}>
                <Typography variant={'h2'}>{round.name}</Typography>
                {round.required && (
                    <Typography>{t('event.competition.setup.round.required')}</Typography>
                )}
                <Box>
                    {round.matches.filter(match => match.teams.length === 1).length > 0 && (
                        <Accordion
                            expanded={props.accordionsExpanded?.[0] ?? false}
                            onChange={handleAccordionExpandedChange(0)}>
                            <AccordionSummary
                                expandIcon={<ExpandMoreIcon />}
                                aria-expanded={true}
                                aria-controls={`round-${roundIndex}-${round.name}-panel-teams-with-bye-content`}
                                id={`round-${roundIndex}-${round.name}-panel-teams-with-bye-header`}>
                                <Typography component="span">
                                    {t('event.competition.execution.teamsWithBye')} (
                                    {round.matches.filter(match => match.teams.length === 1).length}
                                    )
                                </Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                                <TableContainer>
                                    <Table>
                                        <TableHead>
                                            <TableRow>
                                                <TableCell width="20%">
                                                    {t(
                                                        'event.competition.setup.match.outcome.outcome',
                                                    )}
                                                </TableCell>
                                                <TableCell width="80%">
                                                    {t('event.competition.execution.match.team')}
                                                </TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {round.matches
                                                .filter(match => match.teams.length === 1)
                                                .sort((a, b) => a.weighting - b.weighting)
                                                .map(match => (
                                                    <TableRow key={match.id}>
                                                        <TableCell width="20%">
                                                            {match.weighting}
                                                        </TableCell>
                                                        <TableCell width="80%">
                                                            {match.teams[0].clubName +
                                                                (match.teams[0].name
                                                                    ? ` ${match.teams[0].name}`
                                                                    : '')}
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </AccordionDetails>
                        </Accordion>
                    )}
                    <Accordion
                        expanded={props.accordionsExpanded?.[1] ?? false}
                        onChange={handleAccordionExpandedChange(1)}>
                        <AccordionSummary
                            expandIcon={<ExpandMoreIcon />}
                            aria-controls={`round-${roundIndex}-${round.name}-panel-substitutions-content`}
                            id={`round-${roundIndex}-${round.name}-panel-substitutions-header`}>
                            <Typography component="span">
                                {t('event.competition.execution.substitution.substitutions')}
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Substitutions
                                reloadRoundDto={() => props.reloadRoundDto()}
                                roundDto={round}
                                roundIndex={roundIndex}
                            />
                        </AccordionDetails>
                    </Accordion>
                </Box>
                <Box sx={{py: 2}}>
                    <Divider variant={'middle'} />
                </Box>
                <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 4}}>
                    {filteredMatches.map((match, matchIndex) => (
                        <Card
                            key={match.id}
                            sx={{
                                p: 2,
                                flex: 1,
                                [theme.breakpoints.up('md')]: {
                                    minWidth: 400,
                                },
                                ...(match.currentlyRunning && {
                                    borderColor: 'primary.main',
                                    borderWidth: 2,
                                    borderStyle: 'solid',
                                }),
                            }}>
                            <Stack
                                direction={'row'}
                                sx={{
                                    justifyContent: 'space-between',
                                    [theme.breakpoints.down('md')]: {
                                        flexDirection: 'column',
                                    },
                                }}>
                                <Stack spacing={1}>
                                    {match.name && (
                                        <Typography variant={'h3'}>{match.name}</Typography>
                                    )}
                                    <Typography>
                                        {t('event.competition.execution.match.startTime') + ': '}
                                        {match.startTime
                                            ? format(
                                                  new Date(match.startTime),
                                                  t('format.datetime'),
                                              )
                                            : '-'}
                                    </Typography>
                                    {match.startTimeOffset && (
                                        <Typography>
                                            {t(
                                                'event.competition.execution.match.startTimeOffset',
                                            ) + ': '}
                                            {match.startTimeOffset} {t('common.form.seconds')}
                                        </Typography>
                                    )}
                                    {/* Only show toggle if match has no places set */}
                                    {!match.teams.some(
                                        team => team.place !== null && team.place !== undefined,
                                    ) && (
                                        <FormControlLabel
                                            control={
                                                <Checkbox
                                                    checked={match.currentlyRunning}
                                                    onChange={() => handleToggleRunningState(match)}
                                                    disabled={submitting}
                                                />
                                            }
                                            label={t(
                                                'event.competition.execution.match.currentlyRunning',
                                            )}
                                        />
                                    )}
                                </Stack>
                                <Stack direction={'column'} spacing={1}>
                                    {roundIndex === 0 && (
                                        <SelectionMenu
                                            anchor={{
                                                button: {
                                                    vertical: 'bottom',
                                                    horizontal: 'right',
                                                },
                                                menu: {
                                                    vertical: 'top',
                                                    horizontal: 'right',
                                                },
                                            }}
                                            buttonContent={t(
                                                'event.competition.execution.results.enter',
                                            )}
                                            keyLabel={'competition-execution-results-enter'}
                                            onSelectItem={async (value: string) => {
                                                const v = value as MatchResultOption
                                                switch (v) {
                                                    case 'form':
                                                        props.openResultsDialog(matchIndex)
                                                        break
                                                    case 'XLS':
                                                        setResultImportMatch(match.id)
                                                        break
                                                }
                                            }}
                                            items={MATCH_RESULT_OPTIONS.map(
                                                o =>
                                                    ({
                                                        id: o,
                                                        label: t(
                                                            `event.competition.execution.results.type.${o}`,
                                                        ),
                                                    }) satisfies {
                                                        id: MatchResultOption
                                                        label: string
                                                    },
                                            )}
                                        />
                                    )}
                                    <LoadingButton
                                        onClick={() =>
                                            props.openEditMatchDialog(roundIndex, matchIndex)
                                        }
                                        variant={'outlined'}
                                        pending={submitting}>
                                        {t('event.competition.execution.matchData.edit')}
                                    </LoadingButton>

                                    <SelectionMenu
                                        anchor={{
                                            button: {
                                                vertical: 'bottom',
                                                horizontal: 'right',
                                            },
                                            menu: {
                                                vertical: 'top',
                                                horizontal: 'right',
                                            },
                                        }}
                                        buttonContent={t(
                                            'event.competition.execution.startList.download',
                                        )}
                                        keyLabel={'competition-execution-startlist-download'}
                                        onSelectItem={async (fileType: string) => {
                                            const ft = fileType as StartListFileType
                                            switch (ft) {
                                                case 'PDF':
                                                    await handleDownloadStartList(match.id, 'PDF')
                                                    break
                                                case 'CSV':
                                                    setStartListMatch(match.id)
                                                    break
                                            }
                                        }}
                                        items={
                                            [
                                                {
                                                    id: 'PDF',
                                                    label: t(
                                                        'event.competition.execution.startList.type.PDF',
                                                    ),
                                                },
                                                {
                                                    id: 'CSV',
                                                    label: t(
                                                        'event.competition.execution.startList.type.CSV',
                                                    ),
                                                },
                                            ] satisfies {id: StartListFileType; label: string}[]
                                        }
                                    />
                                </Stack>
                            </Stack>
                            <Divider sx={{my: 2}} />
                            <TableContainer>
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell width="15%">
                                                {smallScreenLayout
                                                    ? t(
                                                          'event.competition.execution.match.startNumber.short',
                                                      )
                                                    : t(
                                                          'event.competition.execution.match.startNumber.startNumber',
                                                      )}
                                            </TableCell>
                                            <TableCell width="55%">
                                                {t('event.competition.execution.match.team')}
                                            </TableCell>
                                            <TableCell width="30%">
                                                {t('event.competition.execution.match.place')}
                                            </TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {match.teams
                                            .sort((a, b) => a.startNumber - b.startNumber)
                                            .map(team => (
                                                <TableRow key={team.registrationId}>
                                                    <TableCell width="15%">
                                                        {team.startNumber}
                                                    </TableCell>
                                                    <TableCell width="55%">
                                                        {team.clubName +
                                                            (team.name ? ` ${team.name}` : '')}
                                                    </TableCell>
                                                    <TableCell width="30%">
                                                        {!team.deregistered
                                                            ? team.place
                                                            : t(
                                                                  'event.competition.registration.deregister.deregistered',
                                                              ) +
                                                              (team.deregistrationReason
                                                                  ? ` (${team.deregistrationReason})`
                                                                  : '')}
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Card>
                    ))}
                </Box>
                {roundIndex === 0 && (
                    <LoadingButton
                        pending={submitting}
                        onClick={deleteCurrentRound}
                        variant={'outlined'}>
                        {t('event.competition.execution.deleteRound.delete')}
                    </LoadingButton>
                )}
            </Stack>
            <StartListConfigPicker
                open={showStartListConfigDialog}
                onClose={closeStartListConfigDialog}
                onSuccess={async config => handleDownloadStartList(startListMatch!, 'CSV', config)}
            />
            <MatchResultUploadDialog
                open={showMatchResultImportConfigDialog}
                onClose={closeMatchResultImportConfigDialog}
                onSuccess={async (config, file) =>
                    handleUploadMatchResults(resultImportMatch!, file, config)
                }
            />
        </Fragment>
    )
}
export default CompetitionExecutionRound
