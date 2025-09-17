import { memo } from 'react'
import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
    Checkbox,
    FormControlLabel,
    Typography,
} from '@mui/material'
import Grid2 from '@mui/material/Grid2'
import BaseDialog from '@components/BaseDialog.tsx'
import { useTranslation } from 'react-i18next'
import { ImportForm, DATA_TYPE_OPTIONS } from './common'
import { WebDAVExportType } from '@api/types.gen.ts'

interface ImportDialogProps {
    open: boolean
    onClose: () => void
    formData: ImportForm
    setFormData: React.Dispatch<React.SetStateAction<ImportForm>>
    webDavExportTypeNames: Map<WebDAVExportType, string>
    onSubmit: () => Promise<void>
}

const ImportDialog = memo(({
    open,
    onClose,
    formData,
    setFormData,
    webDavExportTypeNames,
    onSubmit,
}: ImportDialogProps) => {
    const { t } = useTranslation()

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'sm'}>
            <DialogTitle>{t('webDAV.import.import')}</DialogTitle>
            <DialogContent>
                <TextField
                    fullWidth
                    value={formData.folderName}
                    onChange={e => setFormData(prev => ({...prev, folderName: e.target.value}))}
                    label={t('webDAV.import.folderName')}
                    required
                    margin="normal"
                />
                <Typography variant="subtitle1" sx={{mt: 2, mb: 1}}>
                    {t('webDAV.import.selectData')}
                </Typography>
                <Grid2 container spacing={1}>
                    {DATA_TYPE_OPTIONS.map((importType, index) => (
                        <Grid2 key={importType} size={{ xs: 12, sm: 6 }}>
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={formData.selectedData[index]?.checked || false}
                                        onChange={e => {
                                            const newSelectedData = [...formData.selectedData]
                                            newSelectedData[index] = {
                                                ...newSelectedData[index],
                                                checked: e.target.checked,
                                            }
                                            setFormData(prev => ({
                                                ...prev,
                                                selectedData: newSelectedData,
                                            }))
                                        }}
                                    />
                                }
                                label={webDavExportTypeNames.get(importType) ?? '-'}
                            />
                        </Grid2>
                    ))}
                </Grid2>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>
                    {t('common.cancel')}
                </Button>
                <Button variant="contained" onClick={onSubmit}>
                    {t('webDAV.import.confirm')}
                </Button>
            </DialogActions>
        </BaseDialog>
    )
})

ImportDialog.displayName = 'ImportDialog'

export default ImportDialog