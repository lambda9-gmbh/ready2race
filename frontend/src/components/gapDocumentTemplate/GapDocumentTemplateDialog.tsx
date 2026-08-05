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
    getGapDocumentTemplateTypes,
    updateGapDocumentTemplate,
} from '@api/sdk.gen.ts'
import {Button, Grid2, MenuItem, Select, Stack, TextField, Typography} from '@mui/material'
import SelectFileButton from '@components/SelectFileButton.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {v4 as uuidv4} from 'uuid'
import PdfPlaceholderEditor from '@components/gapDocumentTemplate/PdfPlaceholderEditor.tsx'
import PlaceholderSidebar from '@components/gapDocumentTemplate/PlaceholderSidebar.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'

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
    fontSize?: number
    bold: boolean
    italic: boolean
    staticText?: string
}

type Form = {
    type: GapDocumentType
    fontName?: string
    placeholders: PlaceholderData[]
    files: {
        file: File
    }[]
    fontFile?: File
    removeFont: boolean
}

const defaultValues: Form = {
    type: 'CERTIFICATE_OF_PARTICIPATION',
    fontName: undefined,
    placeholders: [],
    files: [],
    fontFile: undefined,
    removeFont: false,
}

const mapFormToRequest = (formData: Form): GapDocumentTemplateRequest => ({
    type: formData.type,
    fontName: formData.fontName,
    placeholders: formData.placeholders.map(p => ({
        name: p.name,
        type: p.type,
        page: p.page,
        relLeft: p.relLeft,
        relTop: p.relTop,
        relWidth: p.relWidth,
        relHeight: p.relHeight,
        textAlign: p.textAlign,
        fontSize: p.fontSize,
        bold: p.bold,
        italic: p.italic,
        staticText: p.staticText,
    })),
})

const mapEntityToForm = (dto: GapDocumentTemplateDto): Form => ({
    type: dto.type,
    fontName: dto.fontName,
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
        fontSize: p.fontSize,
        bold: p.bold,
        italic: p.italic,
        staticText: p.staticText,
    })),
    files: [],
    fontFile: undefined,
    removeFont: false,
})

const addAction = (formData: Form) =>
    addGapDocumentTemplate({
        body: {
            request: mapFormToRequest(formData),
            files: formData.files.map(file => file.file),
            font: formData.fontFile,
        },
    })

const editAction = (formData: Form, entity: GapDocumentTemplateDto) =>
    updateGapDocumentTemplate({
        path: {gapDocumentTemplateId: entity.id},
        body: {
            request: mapFormToRequest(formData),
            font: formData.fontFile ?? (formData.removeFont ? new Blob([]) : undefined),
        },
    })

const DOCUMENT_TYPES: GapDocumentType[] = ['CERTIFICATE_OF_PARTICIPATION', 'AWARD_CERTIFICATE']

const GapDocumentTemplateDialog = (props: BaseEntityDialogProps<GapDocumentTemplateDto>) => {
    const formContext = useForm<Form>()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [fileError, setFileError] = useState<string | null>(null)
    const [pdfFile, setPdfFile] = useState<File | Blob | null>(null)
    const [selectedPlaceholder, setSelectedPlaceholder] = useState<string | null>(null)
    const [currentPage, setCurrentPage] = useState<number>(1)
    const [hasExistingFont, setHasExistingFont] = useState<boolean>(false)

    const {data: documentTypes} = useFetch(signal => getGapDocumentTemplateTypes({signal}))

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
    const fontName = formContext.watch('fontName')
    const fontFile = formContext.watch('fontFile')
    const removeFont = formContext.watch('removeFont')

    const allowedPlaceholderTypes =
        documentTypes?.find(dt => dt.type === documentType)?.allowedPlaceholders ?? []

    const onOpen = useCallback(async () => {
        if (props.entity) {
            formContext.reset(mapEntityToForm(props.entity))
            setHasExistingFont(props.entity.hasFont)
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
            setHasExistingFont(false)
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

    const handleTypeChange = (newType: GapDocumentType) => {
        if (newType === documentType) {
            return
        }
        const newAllowedTypes =
            documentTypes?.find(dt => dt.type === newType)?.allowedPlaceholders ?? []
        const keptPlaceholders = placeholders.filter(p => newAllowedTypes.includes(p.type))
        const removedCount = placeholders.length - keptPlaceholders.length

        formContext.setValue('type', newType)
        formContext.setValue('placeholders', keptPlaceholders)
        if (selectedPlaceholder && !keptPlaceholders.some(p => p.id === selectedPlaceholder)) {
            setSelectedPlaceholder(null)
        }
        if (removedCount > 0) {
            feedback.warning(
                t('gap.document.template.typePlaceholdersRemoved', {count: removedCount}),
            )
        }
    }

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
            bold: false,
            italic: false,
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
                <Stack spacing={0.5}>
                    <FormInputLabel label={t('gap.document.template.type')} required horizontal>
                        <Select
                            sx={{flex: 1}}
                            value={documentType}
                            disabled={!!props.entity}
                            onChange={e => {
                                handleTypeChange(e.target.value as GapDocumentType)
                            }}>
                            {DOCUMENT_TYPES.map(type => (
                                <MenuItem key={type} value={type}>
                                    {t(`gap.document.template.types.${type}`)}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormInputLabel>
                    {props.entity && (
                        <Typography variant="caption" color="text.secondary">
                            {t('gap.document.template.typeLockedHelp')}
                        </Typography>
                    )}
                </Stack>

                {/* Font Name */}
                <FormInputLabel label={t('gap.document.template.font.name')} horizontal>
                    <TextField
                        sx={{flex: 1}}
                        size="small"
                        value={fontName ?? ''}
                        onChange={e =>
                            formContext.setValue('fontName', e.target.value || undefined)
                        }
                        helperText={t('gap.document.template.font.nameHelp')}
                    />
                </FormInputLabel>

                {/* Font File Upload */}
                <Stack spacing={1}>
                    <Typography variant="body2">
                        {t('gap.document.template.font.upload')}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        {t('gap.document.template.font.uploadHelp')}
                    </Typography>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <SelectFileButton
                            variant={'outlined'}
                            onSelected={file => {
                                formContext.setValue('fontFile', file)
                                formContext.setValue('removeFont', false)
                            }}
                            accept={'.ttf,.otf,font/ttf,font/otf'}>
                            {fontFile || hasExistingFont
                                ? t('gap.document.template.font.change')
                                : t('gap.document.template.font.choose')}
                        </SelectFileButton>
                        {fontFile && (
                            <>
                                <Typography variant="body2">{fontFile.name}</Typography>
                                <Button
                                    size="small"
                                    onClick={() => formContext.setValue('fontFile', undefined)}>
                                    {t('common.cancel')}
                                </Button>
                            </>
                        )}
                    </Stack>
                    {!fontFile && hasExistingFont && !removeFont && (
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="body2" color="text.secondary">
                                {t('gap.document.template.font.current')}
                            </Typography>
                            <Button
                                size="small"
                                color="error"
                                onClick={() => formContext.setValue('removeFont', true)}>
                                {t('gap.document.template.font.remove')}
                            </Button>
                        </Stack>
                    )}
                    {!fontFile && hasExistingFont && removeFont && (
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="body2" color="error">
                                {t('gap.document.template.font.willBeRemoved')}
                            </Typography>
                            <Button
                                size="small"
                                onClick={() => formContext.setValue('removeFont', false)}>
                                {t('gap.document.template.font.undoRemove')}
                            </Button>
                        </Stack>
                    )}
                    {!fontFile && !hasExistingFont && (
                        <Typography variant="body2" color="text.secondary">
                            {t('gap.document.template.font.none')}
                        </Typography>
                    )}
                </Stack>

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
                                    documentType={documentType}
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
                                    allowedTypes={allowedPlaceholderTypes}
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
