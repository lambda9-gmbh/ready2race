import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    GapDocumentPlaceholderType,
    GapDocumentTemplateDto,
    GapDocumentTemplateRequest,
    GapDocumentType,
    TextAlign,
} from '@api/types.gen.ts'
import {useFieldArray, useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useCallback, useEffect, useState} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {
    addGapDocumentTemplate,
    downloadGapDocumentTemplateOriginal,
    updateGapDocumentTemplate,
} from '@api/sdk.gen.ts'
import {Grid2, MenuItem, Select, Stack, Typography} from '@mui/material'
import SelectFileButton from '@components/SelectFileButton.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {v4 as uuidv4} from 'uuid'
import PdfPlaceholderEditor from '@components/gapDocumentTemplate/PdfPlaceholderEditor.tsx'
import PlaceholderSidebar from '@components/gapDocumentTemplate/PlaceholderSidebar.tsx'
import {useFeedback} from '@utils/hooks.ts'

type PlaceholderData = {
    id: string
    name?: string
    type: GapDocumentPlaceholderType
    page: number
    relLeft: number
    relTop: number
    relWidth: number
    relHeight: number
    textAlign: TextAlign
}

type Form = {
    type: GapDocumentType
    placeholders: PlaceholderData[]
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    type: 'CERTIFICATE_OF_PARTICIPATION',
    placeholders: [],
    files: [],
}

const mapFormToRequest = (formData: Form): GapDocumentTemplateRequest => ({
    type: formData.type,
    placeholders: formData.placeholders.map(p => ({
        name: p.name,
        type: p.type,
        page: p.page,
        relLeft: p.relLeft,
        relTop: p.relTop,
        relWidth: p.relWidth,
        relHeight: p.relHeight,
        textAlign: p.textAlign,
    })),
})

const mapEntityToForm = (dto: GapDocumentTemplateDto): Form => ({
    type: dto.type,
    placeholders: dto.placeholders.map(p => ({
        id: p.id,
        name: p.name,
        type: p.type,
        page: p.page,
        relLeft: p.relLeft,
        relTop: p.relTop,
        relWidth: p.relWidth,
        relHeight: p.relHeight,
        textAlign: p.textAlign,
    })),
    files: [],
})

const addAction = (formData: Form) =>
    addGapDocumentTemplate({
        body: {
            request: mapFormToRequest(formData),
            files: formData.files.map(file => file.file),
        },
    })

const editAction = (formData: Form, entity: GapDocumentTemplateDto) =>
    updateGapDocumentTemplate({
        path: {gapDocumentTemplateId: entity.id},
        body: mapFormToRequest(formData),
    })

const GapDocumentTemplateDialog = (props: BaseEntityDialogProps<GapDocumentTemplateDto>) => {
    const formContext = useForm<Form>()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [fileError, setFileError] = useState<string | null>(null)
    const [pdfFile, setPdfFile] = useState<File | Blob | null>(null)
    const [selectedPlaceholder, setSelectedPlaceholder] = useState<string | null>(null)
    const [currentPage, setCurrentPage] = useState<number>(1)

    const {fields, append, update} = useFieldArray({
        control: formContext.control,
        name: 'files',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length !== 1 && !props.entity) {
                    setFileError(t('gap.document.template.file.missing'))
                    return 'empty'
                } else {
                    setFileError(null)
                    return undefined
                }
            },
        },
    })

    const placeholders = formContext.watch('placeholders') || []
    const documentType = formContext.watch('type')

    const onOpen = useCallback(async () => {
        if (props.entity) {
            formContext.reset(mapEntityToForm(props.entity))
            // Load the PDF for editing
            const {data, error} = await downloadGapDocumentTemplateOriginal({
                path: {gapDocumentTemplateId: props.entity.id},
            })
            if (error) {
                feedback.error(t('gap.document.template.loadPdf.error'))
            } else if (data) {
                setPdfFile(data)
            }
        } else {
            formContext.reset(defaultValues)
            setPdfFile(null)
        }
        setFileError(null)
        setSelectedPlaceholder(null)
        setCurrentPage(1)
    }, [props.entity])

    useEffect(() => {
        if (fields[0]?.file) {
            setPdfFile(fields[0].file)
        }
    }, [fields])

    const handleAddPlaceholder = (type: GapDocumentPlaceholderType, page: number) => {
        const newPlaceholder: PlaceholderData = {
            id: uuidv4(),
            type,
            page,
            relLeft: 0.1,
            relTop: 0.1,
            relWidth: 0.3,
            relHeight: 0.1,
            textAlign: 'LEFT',
        }
        formContext.setValue('placeholders', [...placeholders, newPlaceholder])
        setSelectedPlaceholder(newPlaceholder.id)
    }

    const handlePlaceholdersChange = (updatedPlaceholders: PlaceholderData[]) => {
        formContext.setValue('placeholders', updatedPlaceholders)
    }

    const filename = fields[0]?.file?.name

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}
            fullScreen>
            <Stack spacing={3}>
                {/* Document Type Selection */}
                <FormInputLabel label={t('gap.document.template.type')} required horizontal>
                    <Select
                        sx={{flex: 1}}
                        value={documentType}
                        onChange={e => {
                            formContext.setValue('type', e.target.value as GapDocumentType)
                        }}>
                        <MenuItem value={'CERTIFICATE_OF_PARTICIPATION'}>
                            {t('gap.document.template.types.CERTIFICATE_OF_PARTICIPATION')}
                        </MenuItem>
                    </Select>
                </FormInputLabel>

                {/* File Upload (only for new templates) */}
                {!props.entity && (
                    <Stack spacing={1}>
                        <Typography variant="body2">{filename}</Typography>
                        <SelectFileButton
                            variant={'outlined'}
                            onSelected={file => {
                                if (fields.length < 1) {
                                    append({file})
                                } else {
                                    update(0, {file})
                                }
                                // Reset placeholders when changing file
                                formContext.setValue('placeholders', [])
                                setSelectedPlaceholder(null)
                            }}
                            accept={'application/pdf'}>
                            {filename
                                ? t('gap.document.template.file.change')
                                : t('gap.document.template.file.choose')}
                        </SelectFileButton>
                        {fileError && <Typography color={'error'}>{fileError}</Typography>}
                    </Stack>
                )}

                {/* PDF Editor with Sidebar */}
                {pdfFile && (
                    <>
                        <Typography variant="h6">
                            {t('gap.document.template.placeholders.title')}
                        </Typography>
                        <Grid2 container spacing={2}>
                            <Grid2 size={{xs: 12, md: 8}}>
                                <PdfPlaceholderEditor
                                    pdfFile={pdfFile}
                                    placeholders={placeholders}
                                    onPlaceholdersChange={handlePlaceholdersChange}
                                    onAddPlaceholder={handleAddPlaceholder}
                                    selectedPlaceholder={selectedPlaceholder}
                                    onSelectPlaceholder={setSelectedPlaceholder}
                                />
                            </Grid2>
                            <Grid2 size={{xs: 12, md: 4}}>
                                <PlaceholderSidebar
                                    selectedPlaceholder={selectedPlaceholder}
                                    placeholders={placeholders}
                                    onPlaceholdersChange={handlePlaceholdersChange}
                                    onAddPlaceholder={handleAddPlaceholder}
                                    currentPage={currentPage}
                                />
                            </Grid2>
                        </Grid2>
                    </>
                )}
            </Stack>
        </EntityDialog>
    )
}

export default GapDocumentTemplateDialog
