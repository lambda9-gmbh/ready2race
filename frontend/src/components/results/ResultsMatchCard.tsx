import {LatestMatchResultInfo, LiveMatchInfo, RunningMatchInfo} from '@api/types.gen.ts'
import {Box, Card, CardActionArea, CardContent, Chip, Typography} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import {MatchChip} from '@components/event/match/matchStatusChip.ts'
import StatusChip from '@components/event/match/StatusChip.tsx'

export type ResultsMatchInfo = LatestMatchResultInfo | RunningMatchInfo | LiveMatchInfo

type Props<M extends ResultsMatchInfo> = {
    match: M
    selectMatch: (match: M) => void
    competition?: {
        competitionName: string
        competitionCategory?: string
    }
    /** Der Zustand des Laufs, wo die Ansicht ihn kennt. Null oder fehlend heißt: kein Chip. */
    statusChip?: MatchChip | null
    /**
     * Ein Lauf, hinter dem kein Dialog steht: abgesagt, wartende Runde, Programmpunkt. Die Karte
     * bleibt sichtbar, verliert aber ihre Klickfläche — ein Dialog hätte dort nichts zu zeigen.
     */
    disabled?: boolean
    /**
     * Eine zurückgenommene Zusatzzeile unter dem Karteninhalt, z.B. der Hinweis auf eine noch
     * nicht erzeugte Runde. Fehlt sie, ändert sich an der Karte nichts.
     */
    note?: string
    /**
     * Der Lauf ist abgesagt: Runde und Laufname bekommen zusätzlich zur Abblendung der ganzen
     * Karte (siehe [disabled]) eine Durchstreichung, so wie `AthleteBoardMatchCard` es für
     * denselben Fall tut. Eigene, schmalere Eigenschaft statt Wiederverwendung von [disabled]:
     * eine wartende Runde oder ein Programmpunkt sind ebenfalls `disabled`, aber nicht abgesagt
     * und sollen deshalb nicht durchgestrichen werden.
     */
    cancelled?: boolean
}

const ResultsMatchCard = <M extends ResultsMatchInfo>({
    match,
    selectMatch,
    competition,
    statusChip,
    disabled = false,
    note,
    cancelled = false,
}: Props<M>) => {
    const {t} = useTranslation()

    const content = (
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
                    {match.roundName && (
                        <Typography
                            sx={cancelled ? {textDecoration: 'line-through'} : undefined}>
                            {match.roundName}
                        </Typography>
                    )}
                    <Box>
                        {match.matchName && (
                            <Typography
                                variant={'h6'}
                                sx={cancelled ? {textDecoration: 'line-through'} : undefined}>
                                {match.matchName}
                            </Typography>
                        )}
                    </Box>
                </Box>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-end',
                        gap: 0.5,
                    }}>
                    <StatusChip chip={statusChip ?? null} />
                    {match.startTime && (
                        <Typography>
                            {format(new Date(match.startTime), t('format.datetime'))}
                        </Typography>
                    )}
                </Box>
            </Box>
            {note && (
                <Typography variant={'body2'} color={'text.secondary'} fontStyle={'italic'}>
                    {note}
                </Typography>
            )}
        </CardContent>
    )

    return (
        <Card sx={{flex: 1, width: 1, ...(disabled && {opacity: 0.6})}} key={match.matchId}>
            {disabled ? (
                content
            ) : (
                <CardActionArea onClick={() => selectMatch(match)}>{content}</CardActionArea>
            )}
        </Card>
    )
}

export default ResultsMatchCard
