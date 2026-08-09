import {useCallback, useEffect, useState} from 'react'
import {Alert, Box, Button, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {getMyEvent} from '@api/sdk.gen.ts'
import {MyEventDto} from '@api/types.gen.ts'
import Throbber from '@components/Throbber.tsx'
import {
    codesForEvent,
    forgetMyEventCode,
    MyEventCode,
    rememberMyEventCode,
} from '@utils/myEventStorage.ts'
import {blockOrder, MyEventBlock} from './myEventOrder.ts'
import {MyEventMatchList, MyEventResultList, MyEventUnscheduledList} from './MyEventMatchList.tsx'
import {MyEventPersonSwitcher} from './MyEventPersonSwitcher.tsx'
import {MyEventRequirements} from './MyEventRequirements.tsx'
import {usePolledEndpoint} from './usePolledEndpoint.ts'

const FALLBACK_INTERVAL_SECONDS = 15
// Wie beim Athleten-Board: erst ein tatsächlich fehlgeschlagener Abruf macht aus einem
// alternden Stand eine Warnung.
const STALE_AFTER_MISSED_INTERVALS = 3

const BlockHeading = ({title}: {title: string}) => (
    <Typography
        sx={{fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.04em', mb: 0.5}}
        variant="body2"
        color="text.secondary">
        {title}
    </Typography>
)

type MyEventContentProps = {
    eventId: string
    qrCode: string
    onDisplayName: (qrCode: string, displayName: string) => void
    onForget: (qrCode: string) => void
}

/**
 * Der Abruf hängt an einer eigenen Komponente, damit der Takt bei einem Personenwechsel
 * vollständig neu beginnt (der Aufrufer setzt `key`) und damit der Haken gar nicht erst
 * läuft, solange kein Code hinterlegt ist.
 */
const MyEventContent = ({eventId, qrCode, onDisplayName, onForget}: MyEventContentProps) => {
    const {t} = useTranslation()

    const {data, lastUpdated, notFound, initialLoad, loadFailed} = usePolledEndpoint<MyEventDto>(
        signal => getMyEvent({signal, path: {eventId, qrCode}}),
        d => (d.refreshIntervalSeconds > 0 ? d.refreshIntervalSeconds : FALLBACK_INTERVAL_SECONDS),
        [eventId, qrCode],
    )

    const displayName = data?.displayName
    useEffect(() => {
        // Der Name kommt erst mit der Antwort. Zurückgeschrieben steht im Umschalter beim
        // nächsten Öffnen "Ilka Heller" statt eines Codefragments.
        if (displayName) {
            onDisplayName(qrCode, displayName)
        }
    }, [displayName, qrCode, onDisplayName])

    if (notFound) {
        return (
            <Alert
                severity="warning"
                action={
                    <Button color="inherit" size="small" onClick={() => onForget(qrCode)}>
                        {t('myEvent.remove')}
                    </Button>
                }>
                {t('myEvent.notFound')}
            </Alert>
        )
    }

    if (!data) {
        // Vor dem ersten guten Stand ist "nichts gewusst" von "geladen, aber leer" zu
        // unterscheiden — sonst behauptet die Seite bei totem Backend, es stehe kein Lauf an.
        if (loadFailed && !initialLoad) {
            return <Alert severity="error">{t('event.info.athleteBoard.loadError')}</Alert>
        }
        return <Throbber />
    }

    const staleThresholdMs =
        (data.refreshIntervalSeconds || FALLBACK_INTERVAL_SECONDS) *
        STALE_AFTER_MISSED_INTERVALS *
        1000
    const stale =
        loadFailed && lastUpdated !== null && Date.now() - lastUpdated.getTime() > staleThresholdMs

    const scheduled = [...data.running, ...data.upcoming]
    // Läuft der eigene Lauf bereits, ist er das, was oben stehen muss — sonst der nächste.
    const highlighted = data.running.length > 0 ? data.running : data.upcoming
    const highlightTitle = data.running.length > 0 ? t('myEvent.running') : t('myEvent.next')

    const renderBlock = (block: MyEventBlock) => {
        switch (block) {
            case 'requirementBanner':
                return <MyEventRequirements requirements={data.requirements} variant="banner" />
            case 'next':
                return highlighted.length > 0 ? (
                    <Box>
                        <BlockHeading title={highlightTitle} />
                        <MyEventMatchList
                            matches={highlighted}
                            serverTime={data.serverTime}
                            variant="next"
                        />
                    </Box>
                ) : null
            case 'matches':
                return (
                    <Box>
                        <BlockHeading title={t('myEvent.matches')} />
                        {scheduled.length > 0 ? (
                            <MyEventMatchList
                                matches={scheduled}
                                serverTime={data.serverTime}
                                variant="list"
                            />
                        ) : (
                            <Typography color="text.secondary">{t('myEvent.noMatches')}</Typography>
                        )}
                    </Box>
                )
            case 'results':
                return data.results.length > 0 ? (
                    <Box>
                        <BlockHeading title={t('myEvent.results')} />
                        <MyEventResultList results={data.results} />
                    </Box>
                ) : null
            case 'unscheduled':
                return data.unscheduled.length > 0 ? (
                    <Box>
                        <BlockHeading title={t('myEvent.unscheduled')} />
                        <MyEventUnscheduledList registrations={data.unscheduled} />
                    </Box>
                ) : null
            case 'requirements':
                return data.requirements.length > 0 ? (
                    <Box>
                        <BlockHeading title={t('myEvent.requirements')} />
                        <MyEventRequirements requirements={data.requirements} variant="list" />
                    </Box>
                ) : null
        }
    }

    return (
        <Stack gap={2}>
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="baseline"
                gap={1}
                flexWrap="wrap">
                <Box sx={{minWidth: 0}}>
                    <Typography variant="h6" sx={{fontWeight: 700}}>
                        {data.displayName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {[data.clubName, data.eventName].filter(Boolean).join(' | ')}
                    </Typography>
                </Box>
                {lastUpdated && (
                    <Typography variant="caption" color={stale ? 'warning.main' : 'text.secondary'}>
                        {t('event.info.athleteBoard.asOf', {
                            time: new Date(data.serverTime).toLocaleTimeString(undefined, {
                                hour: '2-digit',
                                minute: '2-digit',
                            }),
                        })}
                        {stale ? ` — ${t('event.info.athleteBoard.stale')}` : ''}
                    </Typography>
                )}
            </Stack>

            {/* Leere Blöcke fallen ganz weg, damit der Abstand zwischen den sichtbaren
                Blöcken gleich bleibt. */}
            {blockOrder(data).map(block => {
                const content = renderBlock(block)
                return content ? <Box key={block}>{content}</Box> : null
            })}
        </Stack>
    )
}

/**
 * Das persönliche Dashboard „Mein Event": über den QR-Code am Band ohne Anmeldung geöffnet.
 * Der Code steht nur im Gerätespeicher, nie in der Adresszeile.
 */
export const MyEventPanel = ({eventId}: {eventId: string}) => {
    const {t} = useTranslation()

    const [codes, setCodes] = useState<MyEventCode[]>(() => codesForEvent(eventId))
    const [activeQrCode, setActiveQrCode] = useState<string | null>(
        () => codesForEvent(eventId)[0]?.qrCode ?? null,
    )

    useEffect(() => {
        const forEvent = codesForEvent(eventId)
        setCodes(forEvent)
        setActiveQrCode(forEvent[0]?.qrCode ?? null)
    }, [eventId])

    const handleDisplayName = useCallback(
        (qrCode: string, displayName: string) => {
            const known = codesForEvent(eventId).find(c => c.qrCode === qrCode)
            if (known?.displayName === displayName) {
                // Ohne diesen Abbruch schriebe jeder Abruf denselben Namen erneut in den
                // Speicher und stieße eine neue Darstellung an.
                return
            }
            rememberMyEventCode({qrCode, eventId, displayName})
            setCodes(codesForEvent(eventId))
        },
        [eventId],
    )

    const handleRemove = useCallback(
        (qrCode: string) => {
            forgetMyEventCode(qrCode)
            const rest = codesForEvent(eventId)
            setCodes(rest)
            setActiveQrCode(current => (current === qrCode ? (rest[0]?.qrCode ?? null) : current))
        },
        [eventId],
    )

    if (activeQrCode === null) {
        // Der Reiter erscheint auch ohne hinterlegten Code — sonst erfährt niemand von der
        // Funktion, und die Bänder erklären sich nicht von selbst.
        return <Alert severity="info">{t('myEvent.hint')}</Alert>
    }

    return (
        <Box sx={{p: 1}}>
            <MyEventPersonSwitcher
                codes={codes}
                activeQrCode={activeQrCode}
                onSelect={setActiveQrCode}
                onRemove={handleRemove}
            />
            <MyEventContent
                key={activeQrCode}
                eventId={eventId}
                qrCode={activeQrCode}
                onDisplayName={handleDisplayName}
                onForget={handleRemove}
            />
        </Box>
    )
}
