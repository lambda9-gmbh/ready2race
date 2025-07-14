import {useCallback, useEffect, useState} from "react";
import {IDetectedBarcode, Scanner} from "@yudiel/react-qr-scanner";
import {Button, Stack, Typography} from "@mui/material";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import {useFeedback, useFetch} from "@utils/hooks.ts";
import {checkQrCode} from '@api/sdk.gen.ts'
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import Skeleton from "@mui/material/Skeleton";

const uuidRegex = /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;

const QrScanner = (props: {
    callback: (qrCodeId: string, response: CheckQrCodeResponse | null) => void
}) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [uuid, setUuid] = useState<string | null>(null);
    const [cameraId, setCameraId] = useState<string | undefined>(undefined);
    const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);

    useEffect(() => {
        navigator.mediaDevices.enumerateDevices().then((allDevices) => {
            const videoDevices = allDevices.filter((d) => d.kind === 'videoinput');
            setDevices(videoDevices);
            const backCam = videoDevices.find(
                (d) => d.label.toLowerCase().includes('back') || d.label.toLowerCase().includes('rear')
            );
            if (backCam) {
                setCameraId(backCam.deviceId);
            } else if (videoDevices.length > 0) {
                setCameraId(videoDevices[0].deviceId);
            }
        });

        return () => {
            setCameraId(undefined)
        }
    }, []);

    const handleScan = useCallback((codes: IDetectedBarcode[]) => {
        console.log("found xy codes", codes.toString())
        if (Array.isArray(codes) && codes.length > 0) {
            const code = codes[0].rawValue;
            const match = code.match(uuidRegex);
            if (match) {
                let qrCodeId = match[1];
                setUuid(qrCodeId);
            }
        }
    }, []);

    const handleError = useCallback(((error: unknown) => {
        console.log(error)
    }), [])

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
        <Stack spacing={2} direction={"column"} alignItems={"center"} justifyContent={"center"}>
            <Typography variant={"h2"} textAlign={"center"}>QR-Code scannen</Typography>
            {cameraId === undefined ? (
                <Skeleton variant="rectangular" width={320} height={240} sx={{ borderRadius: 2, maxWidth: '100%' }} />
            ) : (
                <Scanner
                    onError={handleError}
                    onScan={handleScan}
                    constraints={cameraId ? {deviceId: {exact: cameraId}} : undefined}
                    styles={{container: {width: '100%'}}}
                    paused={ cameraId === undefined }
                />
            )}
            {devices.length > 1 && (
                <FormControl fullWidth sx={{mt: 2}}>
                    <InputLabel id="camera-select-label">Kamera</InputLabel>
                    <Select
                        labelId="camera-select-label"
                        value={cameraId || ''}
                        label="Kamera"
                        onChange={e => setCameraId(e.target.value)}
                    >
                        {devices.map(device => (
                            <MenuItem key={device.deviceId} value={device.deviceId}>
                                {device.label || `Kamera ${device.deviceId}`}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            )}

            <Button onClick={() => setUuid("b294a2e0-039d-4ede-bd84-c61a47dd9c04")}>Dev (Test)</Button>
        </Stack>
    );
};

export default QrScanner;