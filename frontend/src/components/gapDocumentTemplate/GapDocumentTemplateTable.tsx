import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {GapDocumentTemplateDto} from '@api/types.gen.ts'
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
import {Link} from '@mui/material'
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

    // Der Import liefert nur die Id der neuen Vorlage zurück (kein vollständiges Dto) und die
    // Tabelle kennt keinen Weg, den Bearbeiten-Dialog für eine bloße Id zu öffnen (openDialog
    // erwartet ein vollständiges GapDocumentTemplateDto, es gibt keinen Einzel-Abruf per Id). Statt
    // dessen wird die Tabelle neu geladen, damit die importierte Vorlage in der Liste erscheint.
    const handleImport = async (file: File) => {
        const {data, error} = await importGapDocumentTemplate({body: {file}})
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
                    <SelectFileButton accept={'.zip'} onSelected={handleImport}>
                        {t('gap.document.template.import')}
                    </SelectFileButton>
                }
            />
            <GapDocumentTemplatePreviewDialog
                open={showPreview}
                onClose={handleClosePreview}
                gapDocumentTemplateId={previewId}
            />
        </>
    )
}

export default GapDocumentTemplateTable
