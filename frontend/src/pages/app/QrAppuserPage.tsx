import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Paper,
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
import {Person, Business} from '@mui/icons-material';

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
            qr.reset(eventId);
        } catch (e) {
            setError((e as Error)?.message || t('qrAppuser.deleteError'));
        } finally {
            setLoading(false);
        }
    };
    
    const handleCateringTransaction = async () => {
        if (!qr.response?.id) return;
        
        setSubmitting(true);
        setError(null);
        try {
            await createCateringTransaction({
                body: {
                    appUserId: qr.response?.id!!,
                    eventId: eventId,
                    price: price === '' ? '0' : price
                },
                throwOnError: true
            });
            qr.reset(eventId);
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
            
            {/* QR Code Assignment Info Box */}
            {qr.response && allowed && !isCaterer && (
                <Paper elevation={2} sx={{ p: 2, width: '100%', bgcolor: 'background.default' }}>
                    <Stack spacing={1.5}>
                        <Typography variant="h6" color="primary">
                            {t('qrAppuser.assignmentInfo')}
                        </Typography>
                        
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Person color="action" />
                            <Typography>
                                <strong>{t('common.name')}:</strong> {qr.response.firstname} {qr.response.lastname}
                            </Typography>
                        </Stack>
                        
                        {qr.response.type === 'User' && 'clubName' in qr.response && qr.response.clubName && (
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Business color="action" />
                                <Typography>
                                    <strong>{t('club.club')}:</strong> {qr.response.clubName}
                                </Typography>
                            </Stack>
                        )}
                    </Stack>
                </Paper>
            )}
            
            {!allowed && (
                <Alert severity="warning">Du hast für diesen QR-Code-Typ keine Berechtigung.</Alert>
            )}

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
            <Button variant={'outlined'} onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>

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