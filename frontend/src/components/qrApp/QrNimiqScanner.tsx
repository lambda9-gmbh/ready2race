import {useEffect, useRef, useState} from 'react'
import {Button, Stack, Typography} from '@mui/material'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import Select from '@mui/material/Select'
import MenuItem from '@mui/material/MenuItem'
import {useFeedback} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'

import QrScanner from 'qr-scanner'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {startScannerWithRetry} from './startScannerWithRetry.ts'

const QrNimiqScanner = (props: {callback: (qrCodeContent: string) => void}) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [cameraId, setCameraId] = useState<string | undefined>(undefined)
    // Die tatsächlich aktive Kamera, wenn das Betriebssystem gewählt hat (facingMode) —
    // nur für die Anzeige im Select, löst keinen Neustart des Streams aus.
    const [activeCameraId, setActiveCameraId] = useState<string | undefined>(undefined)
    // Erst wenn geklärt ist, ob eine gespeicherte Kamerawahl noch gültig ist, darf der
    // Stream starten — sonst liefe kurz die Systemwahl und würde gleich wieder ersetzt.
    const [storedChecked, setStoredChecked] = useState(false)
    const [devices, setDevices] = useState<{id: string; label: string}[]>([])
    // Sind auch die automatischen Wiederholungen gescheitert, zeigt die Komponente
    // eine Meldung statt eines stillen schwarzen Bildes; der Zähler stößt über die
    // Effekt-Abhängigkeiten einen manuellen Neustart an.
    const [cameraFailed, setCameraFailed] = useState(false)
    const [restartCount, setRestartCount] = useState(0)
    const videoRef = useRef<HTMLVideoElement>(null)
    const scannerRef = useRef<QrScanner | null>(null)
    const {qrLastScanned} = useAppSession()

    // Callback und Feedback bekommen bei jedem Render neue Identitäten (der Aufrufer
    // übergibt eine Inline-Funktion, useFeedback baut sein Objekt jedes Mal neu).
    // Als Effekt-Abhängigkeiten rissen sie die Kamera deshalb bei jedem Render ab
    // und starteten sie neu — genau das ließ die Vorschau auf der Regatta ständig
    // sterben. Über Refs bleiben sie aktuell, ohne den Stream anzufassen.
    const callbackRef = useRef(props.callback)
    callbackRef.current = props.callback
    const feedbackRef = useRef(feedback)
    feedbackRef.current = feedback
    const lastScannedCodeRef = useRef<string | null>(null)

    useEffect(() => {
        QrScanner.listCameras(true)
            .then((cams: {id: string; label: string}[]) => {
                setDevices(cams)
                const storedCameraId = localStorage.getItem('qr_camera_id')
                const validCamera = cams.find(cam => cam.id === storedCameraId)
                if (validCamera) {
                    setCameraId(validCamera.id)
                }
                // Ohne gespeicherte Auswahl wird nicht über Gerätelabels geraten: auf
                // deutschsprachigen iPhones heißen die Kameras „Rückkamera“/„Frontkamera“,
                // „back“/„rear“ matcht dort nie und der Fallback landete auf der Frontkamera.
                // Stattdessen überlässt der Scanner die Wahl per facingMode dem
                // Betriebssystem — das kennt seine logische Rückkamera sprachunabhängig.
            })
            .finally(() => {
                // Auch wenn die Kameraliste nicht zu bekommen war (z. B. Berechtigung
                // gerade verweigert): den Start trotzdem versuchen, damit der Nutzer
                // eine Fehlermeldung mit Wiederholen-Knopf sieht statt gar nichts.
                setStoredChecked(true)
            })
    }, [])

    useEffect(() => {
        if (cameraId) {
            localStorage.setItem('qr_camera_id', cameraId)
        }
    }, [cameraId])

    useEffect(() => {
        const video = videoRef.current
        if (!video || !storedChecked) return
        let cancelled = false

        // Der Scanner verwaltet seinen Kamerastream vollständig selbst — inklusive
        // Stoppen bei App im Hintergrund und Neustart beim Zurückkommen
        // (visibilitychange-Listener der Bibliothek). Ein zusätzlicher eigener
        // getUserMedia-Aufruf daneben öffnete die Kamera doppelt und ließ beim
        // Aufräumen leicht einen Stream offen, der die nächste Öffnung blockierte
        // (NotReadableError auf Android).
        const scanner = new QrScanner(
            video,
            (result: QrScanner.ScanResult) => {
                if (result && result.data && result.data !== lastScannedCodeRef.current) {
                    if (qrLastScanned.current + 2000 < Date.now()) {
                        lastScannedCodeRef.current = result.data
                        qrLastScanned.current = Date.now()
                        callbackRef.current(result.data)
                    }
                }
            },
            {
                returnDetailedScanResult: true,
                // Eine gespeicherte bzw. manuell gewählte Kamera gewinnt. Ohne Auswahl
                // wählt das Betriebssystem per facingMode die logische Rückkamera —
                // funktioniert auf iOS und Android unabhängig von der Gerätesprache.
                // Existiert die gespeicherte Kamera nicht mehr, fällt die Bibliothek
                // selbst auf eine freie Kamera zurück.
                preferredCamera: cameraId ?? 'environment',
            },
        )
        scannerRef.current = scanner

        const runPromise = startScannerWithRetry(scanner, () => cancelled).then(outcome => {
            if (cancelled) return
            if (outcome === 'started') {
                setCameraFailed(false)
                // Das Select soll die tatsächlich aktive Kamera anzeigen, auch wenn
                // das Betriebssystem sie gewählt hat.
                const stream = video.srcObject instanceof MediaStream ? video.srcObject : null
                setActiveCameraId(stream?.getVideoTracks()[0]?.getSettings().deviceId)
            } else if (outcome === 'failed') {
                setCameraFailed(true)
                feedbackRef.current.error(t('qrScanner.cameraError'))
            }
        })

        return () => {
            // start() ist asynchron (getUserMedia): destroy() erst, wenn kein Start
            // mehr in der Luft hängt — sonst startete die Kamera „ins Leere“ oder
            // ihr Stream bliebe offen und blockierte den nächsten Start. stop()
            // vorab ist synchron unbedenklich und gibt die Kamera schnellstmöglich
            // frei; die Bibliothek verwirft dadurch auch einen Stream, den ein noch
            // laufender Start erst danach erhält.
            cancelled = true
            scannerRef.current = null
            scanner.stop()
            void runPromise.finally(() => scanner.destroy())
        }
        // t ist absichtlich keine Abhängigkeit: ein Sprachwechsel soll die laufende
        // Kamera nicht neu starten.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [cameraId, storedChecked, restartCount, qrLastScanned])

    return (
        <Stack spacing={2} direction="column" alignItems="center" justifyContent="center" p={2}>
            <div style={{width: '100%'}}>
                <video ref={videoRef} style={{width: '100%'}} muted playsInline />
            </div>
            {cameraFailed && (
                <Stack spacing={1} alignItems="center" sx={{width: '100%'}}>
                    <Typography textAlign="center">{t('qrScanner.cameraError')}</Typography>
                    <Typography variant="body2" color="text.secondary" textAlign="center">
                        {t('qrScanner.cameraErrorHint')}
                    </Typography>
                    <Button
                        variant="contained"
                        fullWidth
                        onClick={() => {
                            setCameraFailed(false)
                            setRestartCount(count => count + 1)
                        }}>
                        {t('qrScanner.cameraRetry')}
                    </Button>
                </Stack>
            )}
            {devices.length > 1 && (
                <FormControl fullWidth sx={{mt: 2}}>
                    <InputLabel id="camera-select-label" sx={{fontSize: '1.2rem'}}>
                        Kamera
                    </InputLabel>
                    <Select
                        labelId="camera-select-label"
                        value={cameraId ?? activeCameraId ?? ''}
                        label="Kamera"
                        onChange={e => setCameraId(e.target.value)}
                        fullWidth
                        sx={{
                            fontSize: '1.2rem',
                            minHeight: 60,
                        }}
                        MenuProps={{
                            PaperProps: {
                                sx: {
                                    fontSize: '1.2rem',
                                    minWidth: 200,
                                },
                            },
                        }}>
                        {devices.map(device => (
                            <MenuItem
                                key={device.id}
                                value={device.id}
                                sx={{fontSize: '1.2rem', minHeight: 48}}>
                                {device.label || `Kamera ${device.id}`}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            )}
        </Stack>
    )
}

export default QrNimiqScanner
