import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    TextField,
    Typography
} from "@mui/material";
import {useEffect, useState} from "react";
import {qrEventRoute, router} from "@routes";
import {createCateringTransaction, deleteQrCode} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import {
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal
} from '@authorization/privileges';
import {PriceAdjuster} from "@components/qrApp/PriceAdjuster.tsx";

const QrAppuserPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = router.navigate
    
    // Caterer specific state
    const [price, setPrice] = useState<string>('');
    const [submitting, setSubmitting] = useState(false);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    useEffect(() => {
        if (!appFunction) {
            navigate({to: "/app/function"})
            return;
        }
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId, navigate])
    

    const handleDelete = async () => {
        setLoading(true);
        setError(null);
        try {
            await deleteQrCode({path: {qrCodeId: qr.qrCodeId!}});
            setDialogOpen(false);
            setSuccessMessage(t('qrAppuser.deleteSuccess'));
            setTimeout(() => {
                qr.reset(eventId);
            }, 2000);
        } catch (e) {
            setError((e as Error)?.message || t('qrAppuser.deleteError'));
        } finally {
            setLoading(false);
        }
    };
    
    const handleCateringTransaction = async () => {
        if (!qr.qrCodeId) return;
        
        setSubmitting(true);
        setError(null);
        try {
            await createCateringTransaction({
                body: {
                    appUserId: qr.qrCodeId,
                    eventId: eventId,
                    price: price || null
                }
            });
            // Show success and go back after delay
            setSuccessMessage(t('caterer.transactionSuccess'));
            setTimeout(() => {
                qr.reset(eventId);
            }, 2000);
        } catch (e) {
            setError((e as Error)?.message || t('caterer.transactionError'));
        } finally {
            setSubmitting(false);
        }
    };

    const allowed = [
        updateAppQrManagementGlobal,
        updateAppCompetitionCheckGlobal,
        updateAppEventRequirementGlobal,
        updateAppCatererGlobal
    ].some(priv => appFunction === priv.resource);
    const canRemove = appFunction === updateAppQrManagementGlobal.resource;
    const isCaterer = appFunction === updateAppCatererGlobal.resource;

    return (
        <Stack 
            spacing={2} 
            alignItems="center" 
            justifyContent="center"
            sx={{ width: '100%', maxWidth: 600 }}
        >
            <Typography variant="h4" textAlign="center" gutterBottom>
                {t('qrAppuser.title')}
            </Typography>
            {successMessage && (
                <Alert severity="success" sx={{ width: '100%' }}>
                    {successMessage}
                </Alert>
            )}
            {!allowed && (
                <Alert severity="warning">Du hast für diesen QR-Code-Typ keine Berechtigung.</Alert>
            )}
            {allowed && !isCaterer && <Alert severity={"error"} variant={"filled"}>{t('qrAppuser.underConstruction')}</Alert>}
            
            {/* Caterer specific UI */}
            {isCaterer && qr.qrCodeId && (
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
                            onClick={handleCateringTransaction}
                            disabled={submitting}
                            fullWidth
                        >
                            {t('caterer.confirm')}
                        </Button>
                    </Stack>
                </Box>
            )}
            
            {error && <Alert severity="error">{error}</Alert>}
            
            <Button variant={'outlined'} onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>
            {canRemove && (
                <Button
                    color="error"
                    variant="contained"
                    fullWidth
                    onClick={() => setDialogOpen(true)}
                >
                    {t('qrAppuser.removeAssignment')}
                </Button>
            )}
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>{t('qrAppuser.removeAssignmentTitle')}</DialogTitle>
                <DialogContent>
                    <Typography>{t('qrAppuser.removeAssignmentConfirm')}</Typography>
                    {error && <Alert severity="error" sx={{mt: 2}}>{error}</Alert>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogOpen(false)} disabled={loading}>{t('common.cancel')}</Button>
                    <Button onClick={handleDelete} color="error" variant="contained" disabled={loading}>
                        {t('common.delete')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrAppuserPage