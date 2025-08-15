import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getRunningMatches} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {Alert, Stack} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import ResultsMatchCard from '@components/results/ResultsMatchCard.tsx'
import {useState} from 'react'
import {RunningMatchInfo} from '@api/types.gen.ts'
import ResultsMatchDialog from '@components/results/ResultsMatchDialog.tsx'

type Props = {
    eventId: string
}

const ResultsLiveMatches = ({eventId}: Props) => {
    const matchesLimit = 100 // todo

    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data, pending} = useFetch(
        signal =>
            getRunningMatches({
                signal,
                path: {eventId},
                query: {limit: matchesLimit},
            }),
        {
            onResponse: response => {
                if (response.error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('results.liveMatches.liveMatches'),
                        }),
                    )
                }
            },
            deps: [eventId, matchesLimit],
        },
    )

    const [dialogOpen, setDialogOpen] = useState(false)
    const [matchSelected, setMatchSelected] = useState<RunningMatchInfo | null>(null)
    const onClickMatch = (match: RunningMatchInfo) => {
        setDialogOpen(true)
        setMatchSelected(match)
    }
    const closeDialog = () => {
        setDialogOpen(false)
        setMatchSelected(null)
    }

    return (
        <>
            <Stack spacing={2} sx={{alignItems: 'center', p: 2}}>
                {pending ? (
                    <Throbber />
                ) : data?.length === 0 ? (
                    <Alert severity={'info'}>{t('results.liveMatches.noMatches')}</Alert>
                ) : (
                    data
                        ?.sort((a, b) =>
                            (a.startTime ?? a.eventDayDate ?? '') >
                            (b.startTime ?? b.eventDayDate ?? '')
                                ? -1
                                : 1,
                        )
                        .map(match => (
                            <ResultsMatchCard
                                match={match}
                                selectMatch={onClickMatch}
                                key={match.matchId}
                            />
                        ))
                )}
            </Stack>
            <ResultsMatchDialog
                match={matchSelected}
                dialogOpen={dialogOpen}
                closeDialog={closeDialog}
            />
        </>
    )
}
export default ResultsLiveMatches