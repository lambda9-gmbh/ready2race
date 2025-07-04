import {Button, DialogActions, DialogContent, DialogTitle, Link, MenuItem, Select} from '@mui/material'
import BaseDialog from '@components/BaseDialog.tsx';
import {useTranslation} from "react-i18next";
import {DocumentType} from "@api/types.gen.ts";
import {useRef, useState} from "react";
import {downloadDocumentTemplateSample} from "@api/sdk.gen.ts";
import {useFeedback} from "@utils/hooks.ts";

type Props = {
    open: boolean
    onClose: () => void
    documentTemplateId: string | null
}

const DocumentTemplatePreviewDialog = (props: Props) => {

    const {t} = useTranslation()
    const feedback = useFeedback()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [selectedType, setSelectedType] = useState<DocumentType | ''>('')

    const handleClose = () => {
        props.onClose()
        setSelectedType('')
    }


    // TODO: implement preview instead of loading in new tab
    const handleChange = async (type: DocumentType) => {
        setSelectedType(type)
        // ...

        const {data, error, response} = await downloadDocumentTemplateSample({path: {documentTemplateId: props.documentTemplateId!}, query: {documentType: type}})

        const anchor = downloadRef.current
        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/attachment; filename="?(.+)"?/)?.[1]

        if (error) {
            feedback.error(t('document.template.preview.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = filename ?? 'sample.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    return (
        <BaseDialog
            open={props.open}
            onClose={handleClose}
            maxWidth={false}
        >
            <DialogTitle>
                [todo] - Template Preview
            </DialogTitle>
            <DialogContent>
                <Link ref={downloadRef} display={'none'} target={'_blank'}></Link>
                <Select
                    value={selectedType}
                    onChange={e => {
                        const value = e.target.value as DocumentType
                        handleChange(value)
                    }}
                >
                    <MenuItem value={'INVOICE'}>
                        Invoice
                    </MenuItem>
                    <MenuItem value={'REGISTRATION_REPORT'}>
                        Registration report
                    </MenuItem>
                </Select>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>
                    {t('common.close')}
                </Button>
            </DialogActions>
        </BaseDialog>
    )
}

export default DocumentTemplatePreviewDialog