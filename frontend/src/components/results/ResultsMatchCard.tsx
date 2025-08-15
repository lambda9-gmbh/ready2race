import {LatestMatchResultInfo, RunningMatchInfo} from '@api/types.gen.ts'
import {
    Box,
    Card,
    CardActionArea,
    CardContent,
    Typography,
} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'

export type ResultsMatchInfo = LatestMatchResultInfo | RunningMatchInfo

type Props<M extends ResultsMatchInfo> = {
    match: M
    selectMatch: (match: M) => void
}

const ResultsMatchCard = <M extends ResultsMatchInfo>({match, selectMatch}: Props<M>) => {
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
                            <Typography>{match.roundName}</Typography>
                            <Box>
                                {match.matchName && (
                                    <Typography variant={'h6'}>{match.matchName}</Typography>
                                )}
                            </Box>
                        </Box>
                        {(match.startTime || match.eventDayDate) && (
                            <Typography>
                                {match.startTime
                                    ? format(new Date(match.startTime), t('format.datetime'))
                                    : format(new Date(match.eventDayDate!), t('format.date'))}
                            </Typography>
                        )}
                    </Box>
                </CardContent>
            </CardActionArea>
        </Card>
    )
}

export default ResultsMatchCard
