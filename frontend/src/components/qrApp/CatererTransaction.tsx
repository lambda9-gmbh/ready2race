import { Box, Stack, Alert, TextField, Button } from "@mui/material";
import { useTranslation } from "react-i18next";
import { PriceAdjuster } from "@components/qrApp/PriceAdjuster.tsx";
import { useState } from "react";

interface CatererTransactionProps {
    onConfirm: (price: string) => void;
    submitting: boolean;
}

export const CatererTransaction = ({ onConfirm, submitting }: CatererTransactionProps) => {
    const { t } = useTranslation();
    const [price, setPrice] = useState<string>('');

    const handleConfirm = () => {
        onConfirm(price);
    };

    return (
        <Box sx={{ width: '100%', maxWidth: 600 }}>
            <Stack spacing={2}>
                <Alert severity="success">
                    {t('caterer.canReceiveFood')}
                </Alert>
                
                <TextField
                    label={t('caterer.price')}
                    value={price}
                    onChange={(e) => setPrice(e.target.value)}
                    type="number"
                    slotProps={{ htmlInput: { step: "0.01", min: "0" } }}
                    helperText={t('caterer.priceHelper')}
                    fullWidth
                />
                
                <PriceAdjuster price={price} onPriceChange={setPrice} />
                
                <Button
                    variant="contained"
                    color="primary"
                    onClick={handleConfirm}
                    disabled={submitting}
                    fullWidth
                >
                    {t('caterer.confirm')}
                </Button>
            </Stack>
        </Box>
    );
};