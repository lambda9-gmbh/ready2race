import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {GapDocumentTemplateDto, GapDocumentType} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {
    deleteGapDocumentTemplate,
    exportGapDocumentTemplate,
    getGapDocumentTemplates,
    importGapDocumentTemplate,
} from '@api/sdk.gen.ts'
import {FileDownload, Preview} from '@mui/icons-material'
import {useRef, useState} from 'react'
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
    Link,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import GapDocumentTemplatePreviewDialog from '@components/gapDocumentTemplate/GapDocumentTemplatePreviewDialog.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {getFilename} from '@utils/helpers.ts'
import {documentTemplateErrorKey} from '@components/certificate/certificateError.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getGapDocumentTemplates({
        signal,
        query: {...paginationParameters},
    })

const deleteRequest = (dto: GapDocumentTemplateDto) =>
    deleteGapDocumentTemplate({path: {gapDocumentTemplateId: dto.id}})

const GapDocumentTemplateTable = (props: BaseEntityTableProps<GapDocumentTemplateDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [previewId, setPreviewId] = useState<string | null>(null)
    const showPreview = previewId !== null
    const downloadRef = useRef<HTMLAnchorElement>(null)

    const handleClosePreview = () => {
        setPreviewId(null)
    }

    const handleExport = async (entity: GapDocumentTemplateDto) => {
        const {data, error, response} = await exportGapDocumentTemplate({
            path: {gapDocumentTemplateId: entity.id},
        })
        const anchor = downloadRef.current

        if (error || !data) {
            feedback.error(t('common.error.unexpected'))
        } else if (anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = getFilename(response) ?? `${entity.name.replace(/\.pdf$/i, '')}.r2rtpl.zip`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    // Die gewählte Datei wartet im Zustand, bis der Typ bestätigt ist: Das Manifest der Zip
    // behauptet nur, was sie ist - beim Import soll das sichtbar und überstimmbar sein
    // (Wunsch vom 10.08.2026). '' heißt "Typ aus der Datei übernehmen".
    const [importFile, setImportFile] = useState<File | null>(null)
    const [importType, setImportType] = useState<GapDocumentType | ''>('')

    // Der Import liefert nur die Id der neuen Vorlage zurück (kein vollständiges Dto) und die
    // Tabelle kennt keinen Weg, den Bearbeiten-Dialog für eine bloße Id zu öffnen (openDialog
    // erwartet ein vollständiges GapDocumentTemplateDto, es gibt keinen Einzel-Abruf per Id). Statt
    // dessen wird die Tabelle neu geladen, damit die importierte Vorlage in der Liste erscheint.
    const handleImport = async () => {
        if (!importFile) return
        const {data, error} = await importGapDocumentTemplate({
            body: {file: importFile},
            query: importType !== '' ? {documentType: importType} : undefined,
        })
        setImportFile(null)
        if (error || !data) {
            const key = error ? documentTemplateErrorKey(error) : undefined
            feedback.error(key ? t(key) : t('common.error.unexpected'))
            return
        }
        feedback.success(t('gap.document.template.importSucceeded'))
        props.reloadData()
    }

    const columns: GridColDef<GapDocumentTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('gap.document.template.name'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'type',
            headerName: t('gap.document.template.type'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, row) => t(`gap.document.template.types.${row.type}`),
        },
    ]

    const customEntityActions = (entity: GapDocumentTemplateDto): EntityAction[] => [
        <GridActionsCellItem
            icon={<Preview />}
            label={t('gap.document.template.preview.show')}
            onClick={() => setPreviewId(entity.id)}
            showInMenu
        />,
        <GridActionsCellItem
            icon={<FileDownload />}
            label={t('gap.document.template.export')}
            title={t('gap.document.template.exportHint')}
            onClick={() => handleExport(entity)}
            showInMenu
        />,
    ]

    return (
        <>
            <Link ref={downloadRef} display={'none'}></Link>
            <EntityTable
                {...props}
                parentResource={'EVENT'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                customEntityActions={customEntityActions}
                customTableActions={
                    <SelectFileButton
                        accept={'.zip'}
                        onSelected={file => {
                            setImportType('')
                            setImportFile(file)
                        }}>
                        {t('gap.document.template.import')}
                    </SelectFileButton>
                }
            />
            <Dialog open={importFile !== null} onClose={() => setImportFile(null)} fullWidth>
                <DialogTitle>{t('gap.document.template.import')}</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{mt: 1}}>
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {importFile?.name}
                        </Typography>
                        <FormControl fullWidth>
                            <InputLabel id={'import-type-label'}>
                                {t('gap.document.template.type')}
                            </InputLabel>
                            <Select
                                labelId={'import-type-label'}
                                label={t('gap.document.template.type')}
                                value={importType}
                                onChange={e =>
                                    setImportType(e.target.value as GapDocumentType | '')
                                }>
                                <MenuItem value={''}>
                                    {t('gap.document.template.importTypeFromFile')}
                                </MenuItem>
                                <MenuItem value={'AWARD_CERTIFICATE'}>
                                    {t('gap.document.template.types.AWARD_CERTIFICATE')}
                                </MenuItem>
                                <MenuItem value={'CERTIFICATE_OF_PARTICIPATION'}>
                                    {t('gap.document.template.types.CERTIFICATE_OF_PARTICIPATION')}
                                </MenuItem>
                            </Select>
                        </FormControl>
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {t('gap.document.template.importAssignHint')}
                        </Typography>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setImportFile(null)}>{t('common.cancel')}</Button>
                    <Button variant={'contained'} onClick={handleImport}>
                        {t('gap.document.template.import')}
                    </Button>
                </DialogActions>
            </Dialog>
            <GapDocumentTemplatePreviewDialog
                open={showPreview}
                onClose={handleClosePreview}
                gapDocumentTemplateId={previewId}
            />
        </>
    )
}

export default GapDocumentTemplateTable
