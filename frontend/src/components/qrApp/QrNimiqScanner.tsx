import {useEffect, useRef, useState} from "react";
import {Button, Stack} from "@mui/material";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import {useFeedback, useFetch} from "@utils/hooks.ts";
import {checkQrCode} from '@api/sdk.gen.ts'
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";

// @ts-ignore
import QrScanner from "qr-scanner";

QrScanner.WORKER_PATH = "/qr-scanner-worker.min.js";

const uuidRegex = /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;

const QrNimiqScanner = (props: {
    callback: (qrCodeId: string, response: CheckQrCodeResponse | null) => void
}) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [uuid, setUuid] = useState<string | null>(null);
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
                            if (result && result.data) {
                                const match = result.data.match(uuidRegex);
                                if (match) {
                                    setUuid(match[1]);
                                }
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
    }, [cameraId, feedback, t]);

    useFetch(signal => checkQrCode({signal, path: {qrCodeId: uuid!!}}),
        {
            onResponse: ({error, data}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('task.task'),
                        }),
                    )
                    console.log(error)
                } else {
                    console.log("call callback")
                    if (Object.keys(data).length == 0) {
                        props.callback(uuid!!, null)
                    } else {
                        props.callback(uuid!!, data)
                    }
                }
            },
            deps: [uuid],
            preCondition: () => uuid !== null
        })

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
            <Button
                onClick={() => setUuid('b294a2e0-039d-4ede-bd84-c61a47dd9c04')}
                fullWidth
                sx={{
                    minHeight: 60,
                    fontSize: '1.2rem',
                    borderRadius: 2,
                    mt: 2,
                    py: 2,
                }}
                variant="contained"
                color="primary"
            >
                Dev (Test)
            </Button>
        </Stack>
    );
};

export default QrNimiqScanner; 