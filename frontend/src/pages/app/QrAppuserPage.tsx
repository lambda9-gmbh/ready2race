import {
    Alert,
    Button,
    Stack,
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
import {QrAssignmentInfo} from '@components/qrApp/QrAssignmentInfo';
import {QrDeleteDialog} from '@components/qrApp/QrDeleteDialog';
import {CatererTransaction} from '@components/qrApp/CatererTransaction';

const QrAppuserPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const navigate = router.navigate

    useEffect(() => {
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
    
    const handleCateringTransaction = async (price: string) => {
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
                <QrAssignmentInfo response={qr.response} />
            )}
            
            {!allowed && (
                <Alert severity="warning">{t('qrParticipant.noRight')}</Alert>
            )}

            {/* Caterer specific UI */}
            {isCaterer && qr.qrCodeId && (
                <CatererTransaction 
                    onConfirm={handleCateringTransaction}
                    submitting={submitting}
                />
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

            <QrDeleteDialog
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                onDelete={handleDelete}
                loading={loading}
                error={error}
                type="appuser"
            />
        </Stack>
    )
}

export default QrAppuserPage