import {useTranslation} from 'react-i18next'
import {Alert, Stack, Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import ResultsMatchCard from '@components/results/ResultsMatchCard.tsx'
import {useState} from 'react'
import {LiveMatchInfo} from '@api/types.gen.ts'
import {getLiveMatches} from '@api/sdk.gen.ts'
import ResultsMatchDialog from '@components/results/ResultsMatchDialog.tsx'
import {matchStatusChip} from '@components/event/match/matchStatusChip.ts'
import {useNow} from '@components/event/match/useNow.ts'
import {usePolledFetch} from '@utils/usePolledFetch.ts'
import {format} from 'date-fns'

type Props = {
    eventId: string
}

const MATCHES_LIMIT = 100
/**
 * Dieselbe Größenordnung wie die Athleten-Anzeige. Der Endpoint sitzt hinter dem
 * `publicInfo`-Rate-Limit, und ein Zustandswechsel darf am Ufer ruhig eine Viertelminute brauchen.
 */
const REFRESH_MS = 15_000

/**
 * Der Tab „Live" der öffentlichen Ergebnisanzeige: was gerade läuft UND was als nächstes dran ist.
 *
 * Bis zum 09.08.2026 zeigte er ausschließlich aktivierte Läufe, ohne Zustand und ohne
 * Nachladen — ein Lauf, der gleich dran war, stand nirgends, „In Vorbereitung" und „Läuft" sahen
 * gleich aus, und wer den Wechsel sehen wollte, musste die Seite neu laden.
 *
 * Der Zustand kommt fertig vom Server (`match.status`) und wird hier ausschließlich durch
 * `matchStatusChip` in einen Chip übersetzt — dieselbe Entscheidung, dieselben Wörter und dieselben
 * Farben wie auf der Durchführungsseite, im Zeitplan und im Schiedsrichter-Dashboard.
 */
const ResultsLiveMatches = ({eventId}: Props) => {
    const {t} = useTranslation()
    // Eigene Uhr für die verstrichenen Minuten auf dem Chip: so zählt „Läuft · 4 min" zwischen
    // zwei Abrufen weiter, statt eine Viertelminute lang stillzustehen.
    const now = useNow()

    const {data, lastUpdated, initialLoad, failed} = usePolledFetch<LiveMatchInfo[]>(
        async signal => {
            const {data} = await getLiveMatches({
                signal,
                path: {eventId},
                query: {limit: MATCHES_LIMIT},
            })
            return data ?? null
        },
        REFRESH_MS,
        [eventId],
    )

    const [dialogOpen, setDialogOpen] = useState(false)
    const [matchSelected, setMatchSelected] = useState<LiveMatchInfo | null>(null)
    const onClickMatch = (match: LiveMatchInfo) => {
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
                {initialLoad ? (
                    <Throbber />
                ) : data === null ? (
                    // Vor dem ersten Erfolg gescheitert: „konnte nicht geladen werden" ist etwas
                    // anderes als „kein Lauf angesetzt", und der Unterschied ist der ganze Grund
                    // für `initialLoad`.
                    <Alert severity={'warning'} sx={{width: 1}}>
                        {t('results.liveMatches.loadFailed')}
                    </Alert>
                ) : (
                    <>
                        {/* Der letzte gute Stand bleibt stehen, wenn ein Abruf scheitert - eine
                            leere Seite nach einem Funkloch wäre der schlechteste Ausgang. Die
                            Zeile sagt, wie alt das Gezeigte ist. */}
                        {failed && lastUpdated && (
                            <Typography variant={'body2'} color={'text.secondary'}>
                                {t('results.liveMatches.stale', {
                                    time: format(lastUpdated, t('format.time')),
                                })}
                            </Typography>
                        )}
                        {data.length === 0 ? (
                            <Alert severity={'info'} sx={{width: 1}}>
                                {t('results.liveMatches.noMatches')}
                            </Alert>
                        ) : (
                            data.map(match => (
                                <ResultsMatchCard
                                    match={match}
                                    selectMatch={onClickMatch}
                                    key={match.matchId}
                                    statusChip={matchStatusChip(
                                        match.status,
                                        match.startTime,
                                        now,
                                    )}
                                    // Abgesagt, wartende Runde oder Programmpunkt: hinter der
                                    // Karte steht keine Aufstellung, die ein Dialog zeigen könnte.
                                    disabled={
                                        match.cancelled === true ||
                                        match.pendingRound === true ||
                                        match.name != null
                                    }
                                    // Wartende Runde: statt einer (nicht vorhandenen)
                                    // Mannschaftsliste steht hier der Hinweis darauf.
                                    note={
                                        match.pendingRound === true
                                            ? t('results.liveMatches.pendingRound')
                                            : undefined
                                    }
                                    competition={{
                                        competitionName: match.name ?? match.competitionName,
                                        competitionCategory: match.categoryName ?? undefined,
                                    }}
                                />
                            ))
                        )}
                    </>
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
