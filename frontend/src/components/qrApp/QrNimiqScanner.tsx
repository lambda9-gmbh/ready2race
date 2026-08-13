import {useEffect, useRef, useState} from 'react'
import {Stack} from '@mui/material'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import Select from '@mui/material/Select'
import MenuItem from '@mui/material/MenuItem'
import {useFeedback} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'

// @ts-ignore
import QrScanner from 'qr-scanner'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'

QrScanner.WORKER_PATH = '/qr-scanner-worker.min.js'

const QrNimiqScanner = (props: {callback: (qrCodeContent: string) => void}) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [lastScannedCode, setLastScannedCode] = useState<string | null>(null)
    const [cameraId, setCameraId] = useState<string | undefined>(undefined)
    // Die tatsächlich aktive Kamera, wenn das Betriebssystem gewählt hat (facingMode) —
    // nur für die Anzeige im Select, löst keinen Neustart des Streams aus.
    const [activeCameraId, setActiveCameraId] = useState<string | undefined>(undefined)
    // Erst wenn geklärt ist, ob eine gespeicherte Kamerawahl noch gültig ist, darf der
    // Stream starten — sonst liefe kurz die Systemwahl und würde gleich wieder ersetzt.
    const [storedChecked, setStoredChecked] = useState(false)
    const [devices, setDevices] = useState<{id: string; label: string}[]>([])
    const videoRef = useRef<HTMLVideoElement>(null)
    const scannerRef = useRef<QrScanner | null>(null)
    const {qrLastScanned} = useAppSession()

    useEffect(() => {
        QrScanner.listCameras(true).then((cams: {id: string; label: string}[]) => {
            setDevices(cams)
            const storedCameraId = localStorage.getItem('qr_camera_id')
            const validCamera = cams.find(cam => cam.id === storedCameraId)
            if (validCamera) {
                setCameraId(validCamera.id)
            }
            // Ohne gespeicherte Auswahl wird nicht über Gerätelabels geraten: auf
            // deutschsprachigen iPhones heißen die Kameras „Rückkamera“/„Frontkamera“,
            // „back“/„rear“ matcht dort nie und der Fallback landete auf der Frontkamera.
            // Stattdessen überlässt der Stream-Effekt die Wahl per facingMode dem
            // Betriebssystem — das kennt seine logische Rückkamera sprachunabhängig.
            setStoredChecked(true)
        })
        return () => {
            setCameraId(undefined)
        }
    }, [])

    useEffect(() => {
        if (cameraId) {
            localStorage.setItem('qr_camera_id', cameraId)
        }
    }, [cameraId])

    useEffect(() => {
        if (!videoRef.current || !storedChecked) return
        if (videoRef.current.srcObject) {
            ;(videoRef.current.srcObject as MediaStream).getTracks().forEach(track => track.stop())
            videoRef.current.srcObject = null
        }
        let stopped = false
        navigator.mediaDevices
            .getUserMedia(
                cameraId
                    ? // Eine gespeicherte bzw. manuell gewählte Kamera gewinnt weiterhin.
                      {video: {deviceId: {exact: cameraId}}}
                    : // Keine Auswahl: das Betriebssystem wählt die logische Rückkamera —
                      // funktioniert auf iOS und Android unabhängig von der Gerätesprache.
                      {video: {facingMode: {ideal: 'environment'}}},
            )
            .then(stream => {
                if (stopped) {
                    stream.getTracks().forEach(track => track.stop())
                    return
                }
                videoRef.current!.srcObject = stream
                videoRef.current!.play()
                // Das Select soll die tatsächlich aktive Kamera anzeigen, auch wenn das
                // Betriebssystem sie gewählt hat.
                setActiveCameraId(stream.getVideoTracks()[0]?.getSettings().deviceId)
                if (!scannerRef.current) {
                    scannerRef.current = new QrScanner(
                        videoRef.current!,
                        (result: {data: string}) => {
                            if (result && result.data && result.data !== lastScannedCode) {
                                if (qrLastScanned.current + 2000 < Date.now()) {
                                    setLastScannedCode(result.data)
                                    qrLastScanned.current = Date.now()
                                    props.callback(result.data)
                                }
                            }
                        },
                        {returnDetailedScanResult: true},
                    )
                    scannerRef.current.start()
                }
            })
            .catch(_ => {
                feedback.error('Kamera konnte nicht geöffnet werden')
            })
        return () => {
            stopped = true
            if (videoRef.current && videoRef.current.srcObject) {
                ;(videoRef.current.srcObject as MediaStream)
                    .getTracks()
                    .forEach(track => track.stop())
                videoRef.current.srcObject = null
            }
            if (scannerRef.current) {
                scannerRef.current.stop()
                scannerRef.current.destroy()
                scannerRef.current = null
            }
        }
    }, [cameraId, storedChecked, feedback, t, lastScannedCode, props])

    return (
        <Stack spacing={2} direction="column" alignItems="center" justifyContent="center" p={2}>
            <div style={{width: '100%'}}>
                <video ref={videoRef} style={{width: '100%'}} muted playsInline />
            </div>
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
