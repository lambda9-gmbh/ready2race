import {BaseEntityDialogProps} from '@utils/types.ts'
import {DocumentTemplateDto, DocumentTemplateRequest} from '@api/types.gen.ts'
import {useFieldArray, useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useCallback, useState} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {addDocumentTemplate, updateDocumentTemplate} from '@api/sdk.gen.ts'
import {InputAdornment, Stack, Typography} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'

type Form = {
    pagePaddingTop: string
    pagePaddingLeft: string
    pagePaddingRight: string
    pagePaddingBottom: string
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    pagePaddingTop: '',
    pagePaddingLeft: '',
    pagePaddingRight: '',
    pagePaddingBottom: '',
    files: [],
}

const mapFormToRequest = (formData: Form): DocumentTemplateRequest => ({
    pagePaddingTop: formData.pagePaddingTop ? Number(formData.pagePaddingTop) : undefined,
    pagePaddingLeft: formData.pagePaddingLeft ? Number(formData.pagePaddingLeft) : undefined,
    pagePaddingRight: formData.pagePaddingRight ? Number(formData.pagePaddingRight) : undefined,
    pagePaddingBottom: formData.pagePaddingBottom ? Number(formData.pagePaddingBottom) : undefined,
})

const mapEntityToForm = (dto: DocumentTemplateDto): Form => ({
    pagePaddingTop: dto.pagePaddingTop?.toString() ?? '',
    pagePaddingLeft: dto.pagePaddingLeft?.toString() ?? '',
    pagePaddingRight: dto.pagePaddingRight?.toString() ?? '',
    pagePaddingBottom: dto.pagePaddingBottom?.toString() ?? '',
    files: [],
})

const addAction = (formData: Form) =>
    addDocumentTemplate({
        body: {
            request: mapFormToRequest(formData),
            files: formData.files.map(file => file.file),
        },
    })

const editAction = (formData: Form, entity: DocumentTemplateDto) =>
    updateDocumentTemplate({
        path: {documentTemplateId: entity.id},
        body: mapFormToRequest(formData),
    })

const DocumentTemplateDialog = (props: BaseEntityDialogProps<DocumentTemplateDto>) => {
    const formContext = useForm<Form>()
    const {t} = useTranslation()

    const [fileError, setFileError] = useState<string | null>(null)

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapEntityToForm(props.entity) : defaultValues)
        setFileError(null)
    }, [props.entity])


    const {fields, append, update} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length !== 1 && !props.entity) {
                    setFileError(t('document.template.file.missing'))
                    return 'empty'
                } else {
                    setFileError(null)
                    return undefined
                }
            }
        }
    })

    const filename = fields[0]?.file?.name

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={2}>
                {!props.entity && (
                    <>
                        <Typography>{filename}</Typography>
                        <SelectFileButton
                            variant={'text'}
                            onSelected={file => {
                                if (fields.length < 1) {
                                    append({file})
                                } else {
                                    update(0, {file})
                                }
                            }}
                            accept={'application/pdf'}>
                            {filename
                                ? t('document.template.file.change')
                                : t('document.template.file.choose')}
                        </SelectFileButton>
                        {fileError && <Typography color={'error'}>{fileError}</Typography>}
                    </>
                )}
                <FormInputNumber
                    name={'pagePaddingTop'}
                    label={t('document.template.pagePadding.top')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>,
                        },
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingLeft'}
                    label={t('document.template.pagePadding.left')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>,
                        },
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingRight'}
                    label={t('document.template.pagePadding.right')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>,
                        },
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingBottom'}
                    label={t('document.template.pagePadding.bottom')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>,
                        },
                    }}
                />
            </Stack>
        </EntityDialog>
    )
}

export default DocumentTemplateDialog
