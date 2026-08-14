import {LatestMatchResultInfo, LiveMatchInfo, RunningMatchInfo} from '@api/types.gen.ts'
import {Box, Card, CardActionArea, CardContent, Chip, Stack, Typography} from '@mui/material'
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
     * Der zweite, leise Chip unter dem Zustand: „1 abgemeldet". Bewusst getrennt vom
     * [statusChip] - eine Abmeldung ist eine Aussage über die Besetzung, keine über den Zustand
     * des Laufs (siehe `matchStatusChip.deregisteredChip`).
     */
    secondaryChip?: MatchChip | null
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

/**
 * Die Karte eines Laufs auf der öffentlichen Ergebnisseite (Reiter „Ergebnisse" und „Live").
 *
 * Mobil-Umbau 13.08.2026: Der Wettkampfname stand vorher komplett in einem Chip — ein Chip
 * bricht nie um, auf 375 px Breite liefen „Coastal Frauen Doppelvierer mit Steuerfrau/mann
 * (Beach Sprint International)" deshalb rechts aus der Karte und der Rest verschwand. Jetzt ist
 * der Name normaler, umbrechender Text; nur die kurze Wertungskategorie bleibt ein Chip. Die
 * rechte Spalte (Zustand + Startzeit) darf unter den Namen rutschen statt ihn zusammenzudrücken,
 * und ein Laufname, der ohnehin nur die Runde wiederholt („Zeitfahren"/„Zeitfahren"), wird nicht
 * doppelt gezeigt.
 */
const ResultsMatchCard = <M extends ResultsMatchInfo>({
    match,
    selectMatch,
    competition,
    statusChip,
    secondaryChip,
    disabled = false,
    note,
    cancelled = false,
}: Props<M>) => {
    const {t} = useTranslation()

    const strikethrough = cancelled ? {textDecoration: 'line-through'} : undefined
    // "Zeitfahren" als Runde und "Zeitfahren" als Laufname: einmal reicht.
    const matchName = match.matchName !== match.roundName ? match.matchName : undefined

    const content = (
        <CardContent>
            <Stack spacing={0.5}>
                {competition && (
                    <Stack
                        direction={'row'}
                        spacing={1}
                        sx={{alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5}}>
                        <Typography
                            fontWeight={'bold'}
                            color={'primary'}
                            sx={{minWidth: 0, overflowWrap: 'anywhere'}}>
                            {competition.competitionName}
                        </Typography>
                        {competition.competitionCategory && (
                            <Chip
                                variant={'outlined'}
                                color={'primary'}
                                size={'small'}
                                label={competition.competitionCategory}
                            />
                        )}
                    </Stack>
                )}
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        flexWrap: 'wrap',
                        columnGap: 1,
                        rowGap: 0.5,
                    }}>
                    <Box sx={{minWidth: 0}}>
                        {match.roundName && (
                            <Typography sx={strikethrough}>{match.roundName}</Typography>
                        )}
                        {matchName && (
                            <Typography variant={'h6'} sx={strikethrough}>
                                {matchName}
                            </Typography>
                        )}
                    </Box>
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'flex-end',
                            gap: 0.5,
                            ml: 'auto',
                        }}>
                        <StatusChip chip={statusChip ?? null} />
                        <StatusChip chip={secondaryChip ?? null} />
                        {match.startTime && (
                            <Typography>
                                {format(new Date(match.startTime), t('format.datetime'))}
                            </Typography>
                        )}
                    </Box>
                </Box>
            </Stack>
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
