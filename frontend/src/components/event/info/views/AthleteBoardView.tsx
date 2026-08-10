import {Box, CircularProgress, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAthleteBoardData} from '../athleteBoard/useAthleteBoardData'
import {useServerClock} from '../athleteBoard/useServerClock'
import AthleteBoardColumnCard from '../athleteBoard/AthleteBoardColumnCard'
import AthleteBoardMatchCard from '../athleteBoard/AthleteBoardMatchCard'
import AthleteBoardResultCard from '../athleteBoard/AthleteBoardResultCard'
import {BoardCardKind, boardScale, selectBoardCards} from '../athleteBoard/boardLayout'
import {scaled} from '../athleteBoard/common'

interface AthleteBoardViewProps {
    eventId: string
    /**
     * Auf der Info-Seite liegen die Knöpfe für Konfiguration und Vollbild als Overlay über
     * der rechten oberen Ecke und verdecken sonst die Uhr. Dann beginnt der Kopf weiter
     * unten. Auf der eigenen Seite (fest montierter Bildschirm) gibt es keine Knöpfe.
     */
    controlsOverlayed?: boolean
}

const STALE_AFTER_MISSED_INTERVALS = 2

const AthleteBoardView = ({eventId, controlsOverlayed = false}: AthleteBoardViewProps) => {
    const {t} = useTranslation()
    const {data, lastUpdated, notFound, initialLoad, loadFailed} = useAthleteBoardData(eventId)
    const now = useServerClock(data?.serverTime)

    if (notFound) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', p: 3}}>
                <Typography variant="h5" color="text.secondary">
                    {t('event.info.athleteBoard.eventNotFound')}
                </Typography>
            </Box>
        )
    }

    if (initialLoad && !data) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center'}}>
                <CircularProgress />
            </Box>
        )
    }

    // Es wurde noch nie erfolgreich geladen und der letzte Versuch ist fehlgeschlagen
    // (Backend tot, HTTP-Fehler, kein Netz). Das darf nicht wie "keine Läufe" aussehen —
    // ein montierter Bildschirm würde sonst bei totem Backend behaupten, es sei kein Lauf
    // in der Arena.
    if (!data && loadFailed) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', p: 3}}>
                <Typography variant="h5" color="error">
                    {t('event.info.athleteBoard.loadError')}
                </Typography>
            </Box>
        )
    }

    // "Stand" wird bewusst aus der Serverzeit der letzten erfolgreichen Antwort abgeleitet
    // (nicht aus der Geräteuhr) — sonst widersprechen sich Uhrzeit links (Serveruhr) und
    // "Stand" rechts genau auf dem Bildschirm, für den die Serverzeit-Verankerung gedacht ist.
    const asOfTime = data ? new Date(data.serverTime) : null

    const staleThresholdMs =
        (data?.refreshIntervalSeconds ?? 15) * STALE_AFTER_MISSED_INTERVALS * 1000
    // Beide Bedingungen, nicht nur die Uhr: Im Hintergrund pausiert das Polling bewusst,
    // dabei altert lastUpdated ohne dass die Verbindung gestört wäre. Erst ein tatsächlich
    // fehlgeschlagener Abruf (loadFailed) macht aus dem Altern eine Warnung.
    const stale =
        loadFailed &&
        lastUpdated !== null &&
        Date.now() - lastUpdated.getTime() > staleThresholdMs

    const layout = selectBoardCards(data)
    const scale = boardScale(layout)

    const titleFor = (kind: BoardCardKind) =>
        kind === 'running'
            ? t('event.info.athleteBoard.running')
            : kind === 'upcoming'
              ? t('event.info.athleteBoard.upcoming')
              : t('event.info.athleteBoard.results')

    const emptyTextFor = (kind: BoardCardKind) =>
        kind === 'running'
            ? t('event.info.athleteBoard.noRunning')
            : kind === 'upcoming'
              ? t('event.info.athleteBoard.noUpcoming')
              : t('event.info.athleteBoard.noResults')

    return (
        <Box
            sx={{
                // Ab lg gilt das Scroll-Verbot: die Bühne passt sich der Höhe an, statt
                // überzulaufen. Darunter bleibt die gestapelte, scrollende Darstellung von
                // früher — dafür braucht die Höhe hier ihr natürliches Maß statt 100 %.
                height: {xs: 'auto', lg: '100%'},
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: {xs: 'auto auto', lg: 'auto minmax(0, 1fr)'},
                rowGap: 'clamp(0.4rem, 0.9vh, 1rem)',
                p: 'clamp(0.5rem, 1vw, 1.5rem)',
                overflow: {xs: 'auto', lg: 'hidden'},
                // Der Dichte-Faktor löst nur das Höhenproblem der festen Bühne ab lg; darunter
                // scrollt die Seite ohnehin, dort bliebe er ein reiner Verkleinerungsfaktor auf
                // Text, der schon am Minimum von scaled() klemmt. Neutral halten (1), statt die
                // gestapelte mobile Ansicht mitschrumpfen zu lassen.
                '--ab-scale': {xs: 1, lg: scale},
                // Höhe der Overlay-Knöpfe (top: 16 + Knopfhöhe) plus Luft
                ...(controlsOverlayed && {pt: '4rem'}),
            }}>
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="baseline"
                gap={2}>
                <Typography
                    sx={{fontSize: 'clamp(1rem, 1.8vw, 3rem)', fontWeight: 800}}
                    noWrap>
                    {data?.eventName ?? ''}
                </Typography>
                <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                    <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 2rem)', fontWeight: 800}}>
                        {now.toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})}
                    </Typography>
                    {asOfTime && (
                        <Typography
                            sx={{fontSize: 'clamp(0.65rem, 1vw, 0.85rem)'}}
                            color={stale ? 'warning.main' : 'text.secondary'}>
                            {t('event.info.athleteBoard.asOf', {
                                time: asOfTime.toLocaleTimeString(undefined, {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                }),
                            })}
                            {stale ? ` — ${t('event.info.athleteBoard.stale')}` : ''}
                        </Typography>
                    )}
                    {/* Ein gekappter Lauf verschwindet nicht stumm: von einem Anzeigefehler wäre
                        das nicht zu unterscheiden. Eigene, stagenskalierte Zeile statt Anhängsel
                        an die "Stand"-Zeile — deren feste, kleine Schrift wäre vom Steg aus nicht
                        zu lesen. */}
                    {layout.hiddenRunning > 0 && (
                        <Typography
                            sx={{fontWeight: 600, fontSize: scaled('0.8rem', '1.1vw', '1.8rem')}}
                            color="warning.main">
                            {t('event.info.athleteBoard.moreRunning', {count: layout.hiddenRunning})}
                        </Typography>
                    )}
                </Stack>
            </Stack>

            {/* Gleich breite Spalten, keine dominante: bei zwei Läufen in der Arena stehen sie
                gleichwertig nebeneinander. Unterhalb lg stapeln sie wie bisher. */}
            <Box
                sx={{
                    minHeight: 0,
                    display: 'grid',
                    gap: scaled('0.4rem', '0.7vw', '1rem'),
                    gridAutoFlow: {xs: 'row', lg: 'column'},
                    gridAutoColumns: {lg: 'minmax(0, 1fr)'},
                }}>
                {layout.cards.map(card => (
                    <AthleteBoardColumnCard
                        key={card.key}
                        title={titleFor(card.kind)}
                        emptyText={emptyTextFor(card.kind)}>
                        {card.match ? (
                            <AthleteBoardMatchCard
                                match={card.match}
                                now={now}
                                variant={card.kind === 'running' ? 'running' : 'upcoming'}
                                showCountdown={data?.showCountdown ?? true}
                            />
                        ) : card.result ? (
                            <AthleteBoardResultCard result={card.result} />
                        ) : undefined}
                    </AthleteBoardColumnCard>
                ))}
            </Box>
        </Box>
    )
}

export default AthleteBoardView
