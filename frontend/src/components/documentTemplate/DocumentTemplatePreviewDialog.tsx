import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select,
    Stack,
} from '@mui/material'
import BaseDialog from '@components/BaseDialog.tsx'
import {Trans, useTranslation} from 'react-i18next'
import {DocumentType} from '@api/types.gen.ts'
import {useState} from 'react'
import {downloadDocumentTemplateSample} from '@api/sdk.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type Props = {
    open: boolean
    onClose: () => void
    documentTemplateId: string | null
}

const DocumentTemplatePreviewDialog = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [selectedType, setSelectedType] = useState<DocumentType | ''>('')
    const [previewUrl, setPreviewUrl] = useState<string | null>(null)

    const handleClose = () => {
        props.onClose()
        setSelectedType('')
        setPreviewUrl(null)
    }

    const handleChange = async (type: DocumentType) => {
        setSelectedType(type)

        const {data, error} = await downloadDocumentTemplateSample({
            path: {documentTemplateId: props.documentTemplateId!},
            query: {documentType: type},
        })

        if (error) {
            feedback.error(t('document.template.preview.error'))
        } else if (data !== undefined) {
            setPreviewUrl(URL.createObjectURL(data))
        }
    }

    return (
        <BaseDialog open={props.open} onClose={handleClose} fullScreen>
            <DialogTitle>
                <Trans i18nKey={'document.template.preview.title'} />
            </DialogTitle>
            <DialogContent>
                <Stack spacing={4} sx={{height: 1}}>
                    <FormInputLabel label={t('document.template.preview.type')} required horizontal>
                        <Select
                            sx={{flex: 1}}
                            value={selectedType}
                            onChange={e => {
                                const value = e.target.value as DocumentType
                                handleChange(value)
                            }}>
                            <MenuItem value={'INVOICE'}>
                                <Trans i18nKey={'document.template.type.INVOICE'} />
                            </MenuItem>
                            <MenuItem value={'REGISTRATION_REPORT'}>
                                <Trans i18nKey={'document.template.type.REGISTRATION_REPORT'} />
                            </MenuItem>
                            <MenuItem value={'START_LIST'}>
                                <Trans i18nKey={'document.template.type.START_LIST'} />
                            </MenuItem>
                        </Select>
                    </FormInputLabel>
                    {previewUrl && (
                        <Box
                            sx={{
                                position: 'relative',
                                flex: 1,
                            }}>
                            <Box
                                component={'iframe'}
                                src={previewUrl}
                                sx={{
                                    position: 'absolute',
                                    top: 0,
                                    left: 0,
                                    width: 1,
                                    height: 1,
                                }}
                            />
                        </Box>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>{t('common.close')}</Button>
            </DialogActions>
        </BaseDialog>
    )
}

export default DocumentTemplatePreviewDialog
