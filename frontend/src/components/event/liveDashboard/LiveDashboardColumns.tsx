import {Alert, Box, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {LiveDashboardMatchDto} from '@api/types.gen.ts'
import {MatchResultStatus} from '@utils/matchResultStatus.ts'
import LiveDashboardMatchCard, {LiveDashboardPendingSlotCard} from './LiveDashboardMatchCard.tsx'
import {
    dashboardEntryDomId,
    LiveDashboardDetailSettings,
    LiveDashboardTimelineEntry,
} from './common.ts'

/**
 * Die Handlungen, die beide Spalten an ihre Karten durchreichen.
 * `onFinish`/`onSetActivated`/`onMarkStarted`/`onResumeAutoPull`/`onSkipSlot` sind nur gesetzt,
 * wenn die Nutzerin den Ablauf steuern darf — die Karten blenden ihre Knöpfe daran aus.
 */
export type LiveDashboardActions = {
    onTeamClick: (matchId: string, teamId: string) => void
    onFinish?: (matchId: string, openResults: MatchResultStatus | null) => Promise<void>
    /** Ruft den Lauf an den Start oder nimmt das zurück — der Ist-Start hängt nicht daran. */
    onSetActivated?: (matchId: string, activated: boolean) => Promise<void>
    /** „Läuft": stellt fest, dass das Rennen unterwegs ist. */
    onMarkStarted?: (matchId: string) => Promise<void>
    /** Gibt den beim Deaktivieren pausierten RaceClocker-Abruf wieder frei. */
    onResumeAutoPull?: (matchId: string, competitionId: string) => Promise<void>
    onSkipSlot?: (slotId: string, label: string, time: string) => void
    /** Ob die Veranstaltung den automatischen RaceClocker-Abruf eingeschaltet hat. */
    raceClockerAutoPull?: boolean
}

/**
 * Ein Eintrag samt Wrapper mit der DOM-Id seiner Spalte — der Zeitstrahl-Indikator springt über
 * diese Ids zur Karte (siehe [dashboardEntryDomId]).
 */
const TimelineEntryCard = ({
    entry,
    column,
    actions,
    shortLabels,
    detail,
}: {
    entry: LiveDashboardTimelineEntry
    column: 'live' | 'list'
    actions: LiveDashboardActions
    /** Rennen am Kürzel statt am ausgeschriebenen Namen - geteilt mit dem Zeitplan-Tab. */
    shortLabels: boolean
    /** Detailgrad der Karten (Notiz-Vorschau, Aufstellung) — siehe LiveDashboardDetailSettings. */
    detail: LiveDashboardDetailSettings
}) =>
    entry.kind === 'match' ? (
        <Box id={dashboardEntryDomId(entry.match.matchId, column)}>
            <LiveDashboardMatchCard
                match={entry.match}
                onTeamClick={actions.onTeamClick}
                onFinish={actions.onFinish}
                onSetActivated={actions.onSetActivated}
                onMarkStarted={actions.onMarkStarted}
                onResumeAutoPull={actions.onResumeAutoPull}
                raceClockerAutoPull={actions.raceClockerAutoPull}
                shortLabels={shortLabels}
                detail={detail}
            />
        </Box>
    ) : (
        <Box id={dashboardEntryDomId(entry.slot.slotId, column)}>
            <LiveDashboardPendingSlotCard
                slot={entry.slot}
                onSkip={actions.onSkipSlot}
                shortLabels={shortLabels}
            />
        </Box>
    )

type LiveColumnProps = {
    /** Läuft gerade bzw. wartet auf sein Beenden. */
    currentMatches: LiveDashboardMatchDto[]
    /** Nur relevant, solange nichts läuft: das chronologisch nächste Ding überhaupt. */
    nextEntry: LiveDashboardTimelineEntry | undefined
    /** Erst wenn Daten da sind, ist "es läuft nichts" eine Aussage und keine Ladephase. */
    loaded: boolean
    actions: LiveDashboardActions
    shortLabels: boolean
    detail: LiveDashboardDetailSettings
}

/** Was jetzt eine Handlung verlangt: die laufenden Läufe, ersatzweise "Als Nächstes". */
export const LiveColumn = ({
    currentMatches,
    nextEntry,
    loaded,
    actions,
    shortLabels,
    detail,
}: LiveColumnProps) => {
    const {t} = useTranslation()

    return (
        <>
            {currentMatches.length === 0 && loaded && (
                <Alert severity="info">{t('event.liveDashboard.noRunning')}</Alert>
            )}
            {currentMatches.map(match => (
                <TimelineEntryCard
                    key={match.matchId}
                    entry={{kind: 'match', match}}
                    column="live"
                    actions={actions}
                    shortLabels={shortLabels}
                    detail={detail}
                />
            ))}
            {currentMatches.length === 0 && nextEntry && (
                <>
                    <Typography variant="subtitle2" color="text.secondary">
                        {t('event.liveDashboard.nextUp')}
                    </Typography>
                    <TimelineEntryCard
                        entry={nextEntry}
                        column="live"
                        actions={actions}
                        shortLabels={shortLabels}
                        detail={detail}
                    />
                </>
            )}
        </>
    )
}

type MatchListColumnProps = {
    /** Geplante/laufende/beendete Läufe und wartende Slots gemeinsam nach Startzeit. */
    scheduledTimeline: LiveDashboardTimelineEntry[]
    unscheduledMatches: LiveDashboardMatchDto[]
    /** Es gibt weder Läufe noch wartende Slots — und die Daten sind da. */
    empty: boolean
    actions: LiveDashboardActions
    shortLabels: boolean
    detail: LiveDashboardDetailSettings
}

/** Die vollständige Liste zum Selbstbedienen: Zeitplan zuerst, unplanmäßige Läufe darunter. */
export const MatchListColumn = ({
    scheduledTimeline,
    unscheduledMatches,
    empty,
    actions,
    shortLabels,
    detail,
}: MatchListColumnProps) => {
    const {t} = useTranslation()

    return (
        <>
            {scheduledTimeline.map(entry => (
                <TimelineEntryCard
                    key={entry.kind === 'match' ? entry.match.matchId : entry.slot.slotId}
                    entry={entry}
                    column="list"
                    actions={actions}
                    shortLabels={shortLabels}
                    detail={detail}
                />
            ))}
            {unscheduledMatches.length > 0 && (
                <>
                    <Typography variant="subtitle2" color="text.secondary">
                        {t('event.liveDashboard.unscheduled')}
                    </Typography>
                    {unscheduledMatches.map(match => (
                        <TimelineEntryCard
                            key={match.matchId}
                            entry={{kind: 'match', match}}
                            column="list"
                            actions={actions}
                            shortLabels={shortLabels}
                            detail={detail}
                        />
                    ))}
                </>
            )}
            {empty && <Alert severity="info">{t('event.liveDashboard.noMatches')}</Alert>}
        </>
    )
}
