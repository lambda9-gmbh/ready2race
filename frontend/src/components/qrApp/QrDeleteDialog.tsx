import {
    DialogTitle, 
    DialogContent, 
    DialogActions, 
    Button, 
    Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import BaseDialog from "@components/BaseDialog.tsx";

interface QrDeleteDialogProps {
    open: boolean;
    onClose: () => void;
    onDelete: () => void;
    loading: boolean;
    type: 'participant' | 'appuser';
}

export const QrDeleteDialog = ({ 
    open, 
    onClose, 
    onDelete, 
    loading,
    type 
}: QrDeleteDialogProps) => {
    const { t } = useTranslation();
    
    const titleKey = type === 'participant' 
        ? 'qrParticipant.removeAssignmentTitle' 
        : 'qrAppuser.removeAssignmentTitle';
    
    const confirmKey = type === 'participant'
        ? 'qrParticipant.removeAssignmentConfirm'
        : 'qrAppuser.removeAssignmentConfirm';

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>{t(titleKey)}</DialogTitle>
            <DialogContent>
                <Typography>{t(confirmKey)}</Typography>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={loading}>
                    {t('common.cancel')}
                </Button>
                <Button 
                    onClick={onDelete} 
                    color="error" 
                    variant="contained" 
                    disabled={loading}
                >
                    {t('common.delete')}
                </Button>
            </DialogActions>
        </BaseDialog>
    );
};