import {LatestMatchResultInfo, RunningMatchInfo} from '@api/types.gen.ts'
import {Box, Card, CardActionArea, CardContent, Chip, Typography} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'

export type ResultsMatchInfo = LatestMatchResultInfo | RunningMatchInfo

type Props<M extends ResultsMatchInfo> = {
    match: M
    selectMatch: (match: M) => void
    competition?: {
        competitionName: string
        competitionCategory?: string
    }
}

const ResultsMatchCard = <M extends ResultsMatchInfo>({
    match,
    selectMatch,
    competition,
}: Props<M>) => {
    const {t} = useTranslation()

    const onClickMatch = (match: M) => {
        selectMatch(match)
    }

    return (
        <Card sx={{flex: 1, width: 1}} key={match.matchId}>
            <CardActionArea onClick={() => onClickMatch(match)}>
                <CardContent>
                    <Box
                        sx={{
                            display: 'flex',
                            justifyContent: 'space-between',
                        }}>
                        <Box>
                            {competition && (
                                <Chip
                                    variant={'outlined'}
                                    color={'primary'}
                                    sx={{mb: 1}}
                                    label={
                                        <Typography fontWeight={'bold'} variant={'body2'}>
                                            {competition.competitionName +
                                                (competition.competitionCategory
                                                    ? ` (${competition.competitionCategory})`
                                                    : '')}
                                        </Typography>
                                    }
                                />
                            )}
                            <Typography>{match.roundName}</Typography>
                            <Box>
                                {match.matchName && (
                                    <Typography variant={'h6'}>{match.matchName}</Typography>
                                )}
                            </Box>
                        </Box>
                        <Box textAlign={'right'}>
                            {(match.startTime || match.eventDayDate) && (
                                <Typography>
                                    {match.startTime
                                        ? format(new Date(match.startTime), t('format.datetime'))
                                        : format(new Date(match.eventDayDate!), t('format.date'))}
                                </Typography>
                            )}
                        </Box>
                    </Box>
                </CardContent>
            </CardActionArea>
        </Card>
    )
}

export default ResultsMatchCard
