import {createNextCompetitionRound, getCompetitionExecutionProgress} from '@api/sdk.gen.ts'
import {Box, Typography} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'

const CompetitionExecution = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const [reloadData, setReloadData] = useState(false)

    const {data: progressDto} = useFetch(
        signal =>
            getCompetitionExecutionProgress({
                signal,
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error('[todo] Error when fetching progress')
                }
            },
            deps: [eventId, competitionId, reloadData],
        },
    )

    const handleCreateNextRound = async () => {
        setSubmitting(true)
        const {error} = await createNextCompetitionRound({
            path: {
                eventId: eventId,
                competitionId: competitionId,
            },
        })
        setSubmitting(false)
        if (error) {
            feedback.error(`[todo] Error: ${error.errorCode}`)
        } else {
            feedback.success('[todo] New round created')
            setReloadData(!reloadData)
        }
    }

    console.log(progressDto)

    return (
        <Box>
            <LoadingButton
                label={'Create next round'}
                pending={submitting}
                disabled={
                    progressDto?.canCreateNewRound === false || progressDto?.lastRoundFinished
                        ? true
                        : undefined
                }
                variant={'contained'}
                onClick={handleCreateNextRound}
            />
            <Box>
                {progressDto?.rounds.map(round => (
                    <Box sx={{p: 2, border: 1}}>
                        <Typography variant={'h3'}>{round.name}</Typography>
                        {round.required && (
                            <Typography>{t('event.competition.setup.round.required')}</Typography>
                        )}
                        {round.matches
                            .sort((a, b) => a.executionOrder - b.executionOrder)
                            .map(match => (
                                <Box sx={{p: 2, mb: 2, border: 1}}>
                                    {match.name && <Typography>{match.name}</Typography>}
                                    <Typography>Weighting: {match.weighting}</Typography>
                                    {match.startTime && (
                                        <Typography>Start time: {match.startTime}</Typography>
                                    )}
                                    {match.startTimeOffset && (
                                        <Typography>
                                            Start time offset: {match.startTimeOffset}
                                        </Typography>
                                    )}
                                    <Typography>Teams:</Typography>
                                    <Box sx={{display: 'flex', gap: 2}}>
                                        {match.teams
                                            .sort((a, b) => a.startNumber - b.startNumber)
                                            .map(team => (
                                                <Box sx={{p: 2, border: 1}}>
                                                    <Typography>
                                                        {team.clubName +
                                                            (team.name && ` ${team.name}`)}
                                                    </Typography>
                                                    <Typography>
                                                        Start number: {team.startNumber}
                                                    </Typography>
                                                    <Typography>Place: {team.place}</Typography>
                                                </Box>
                                            ))}
                                    </Box>
                                </Box>
                            ))}
                    </Box>
                ))}
            </Box>
        </Box>
    )
}
export default CompetitionExecution
