import {useCallback, useEffect, useState} from "react";
import {IDetectedBarcode, Scanner} from "@yudiel/react-qr-scanner";
import {Stack, Typography} from "@mui/material";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import {useFetch} from "@utils/hooks.ts";
import {checkQrCode} from '@api/sdk.gen.ts'
import {CheckQrCodeResponse} from "@api/types.gen.ts";

const uuidRegex = /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;

const QrScanner = () => {
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
    }, []);

    const handleScan = useCallback((codes: IDetectedBarcode[]) => {
        if (Array.isArray(codes) && codes.length > 0) {
            const code = codes[0].rawValue;
            const match = code.match(uuidRegex);
            if (match) {
                let qrCodeId = match[1];
                setUuid(qrCodeId);
                const data: CheckQrCodeResponse = useFetch(signal => checkQrCode(signal, qrCodeId),
                    {
                        onResponse: ({error}) => {
                            if (error) {
                                feedback.error(
                                    t('common.load.error.single', {
                                        entity: t('task.task'),
                                    }),
                                )
                                console.log(error)
                            }
                        },
                    })
            }
        }
    }, []);

    return (
        <Stack spacing={2} direction={"column"} alignItems={"center"} justifyContent={"center"}>
            <Typography variant={"h2"} textAlign={"center"}>QR-Code scannen</Typography>
            <Scanner
                onScan={handleScan}
                constraints={cameraId ? {deviceId: {exact: cameraId}} : undefined}
                styles={{container: {width: '100%'}}}
            />
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
        </Stack>
    );
};

export default QrScanner;