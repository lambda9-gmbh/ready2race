import { Button, Stack, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import QrNimiqScanner from "@components/qrApp/QrNimiqScanner.tsx";

interface SystemUserScannerProps {
    onScan: (qrCodeContent: string) => void;
    onBack: () => void;
}

const SystemUserScanner: React.FC<SystemUserScannerProps> = ({ onScan, onBack }) => {
    const { t } = useTranslation();

    return (
        <Stack spacing={2} sx={{ width: '100%' }}>
            <Typography variant="h6" align="center">
                {t('qrAssign.scanUserQr')}
            </Typography>
            <QrNimiqScanner callback={onScan} />
            <Button variant="outlined" onClick={onBack} fullWidth>
                {t('common.back')}
            </Button>
        </Stack>
    );
};

export default SystemUserScanner;