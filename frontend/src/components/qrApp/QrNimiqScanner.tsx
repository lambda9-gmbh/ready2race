import {useEffect, useRef, useState} from "react";
import {Stack} from "@mui/material";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import {useFeedback} from "@utils/hooks.ts";
import {useTranslation} from "react-i18next";

// @ts-ignore
import QrScanner from "qr-scanner";

QrScanner.WORKER_PATH = "/qr-scanner-worker.min.js";

const QrNimiqScanner = (props: {
    callback: (qrCodeContent: string) => void
}) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [lastScannedCode, setLastScannedCode] = useState<string | null>(null);
    const [cameraId, setCameraId] = useState<string | undefined>(undefined);
    const [devices, setDevices] = useState<{ id: string, label: string }[]>([]);
    const videoRef = useRef<HTMLVideoElement>(null);
    const scannerRef = useRef<any>(null);

    useEffect(() => {
        QrScanner.listCameras(true).then((cams: { id: string, label: string }[]) => {
            setDevices(cams);
            const storedCameraId = localStorage.getItem('qr_camera_id');
            const validCamera = cams.find(cam => cam.id === storedCameraId);
            if (validCamera) {
                setCameraId(validCamera.id);
            } else {
                const backCam = cams.find((d) => d.label.toLowerCase().includes('back') || d.label.toLowerCase().includes('rear'));
                if (backCam) {
                    setCameraId(backCam.id);
                } else if (cams.length > 0) {
                    setCameraId(cams[0].id);
                }
            }
        });
        return () => {
            setCameraId(undefined)
        }
    }, []);

    useEffect(() => {
        if (cameraId) {
            localStorage.setItem('qr_camera_id', cameraId);
        }
    }, [cameraId]);

    useEffect(() => {
        if (!videoRef.current || !cameraId) return;
        if (videoRef.current.srcObject) {
            (videoRef.current.srcObject as MediaStream)
                .getTracks()
                .forEach(track => track.stop());
            videoRef.current.srcObject = null;
        }
        let stopped = false;
        navigator.mediaDevices.getUserMedia({video: {deviceId: {exact: cameraId}}})
            .then(stream => {
                if (stopped) {
                    stream.getTracks().forEach(track => track.stop());
                    return;
                }
                videoRef.current!.srcObject = stream;
                videoRef.current!.play();
                if (!scannerRef.current) {
                    scannerRef.current = new QrScanner(
                        videoRef.current!,
                        (result: { data: string }) => {
                            if (result && result.data && result.data !== lastScannedCode) {
                                setLastScannedCode(result.data);
                                props.callback(result.data);
                            }
                        },
                        {returnDetailedScanResult: true}
                    );
                    scannerRef.current.start();
                }
            })
            .catch(err => {
                feedback.error('Kamera konnte nicht geöffnet werden');
                console.log('getUserMedia error', err);
            });
        return () => {
            stopped = true;
            if (videoRef.current && videoRef.current.srcObject) {
                (videoRef.current.srcObject as MediaStream)
                    .getTracks()
                    .forEach(track => track.stop());
                videoRef.current.srcObject = null;
            }
            if (scannerRef.current) {
                scannerRef.current.stop();
                scannerRef.current.destroy();
                scannerRef.current = null;
            }
        };
    }, [cameraId, feedback, t, lastScannedCode, props])

    return (
        <Stack
            spacing={2}
            direction="column"
            alignItems="center"
            justifyContent="center"
            p={2}
        >
            <div style={{width: '100%'}}>
                <video ref={videoRef} style={{width: '100%'}} muted playsInline/>
            </div>
            {devices.length > 1 && (
                <FormControl fullWidth sx={{mt: 2}}>
                    <InputLabel id="camera-select-label" sx={{fontSize: '1.2rem'}}>
                        Kamera
                    </InputLabel>
                    <Select
                        labelId="camera-select-label"
                        value={cameraId || ''}
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
                        }}
                    >
                        {devices.map(device => (
                            <MenuItem
                                key={device.id}
                                value={device.id}
                                sx={{fontSize: '1.2rem', minHeight: 48}}
                            >
                                {device.label || `Kamera ${device.id}`}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            )}
        </Stack>
    );
};

export default QrNimiqScanner; 