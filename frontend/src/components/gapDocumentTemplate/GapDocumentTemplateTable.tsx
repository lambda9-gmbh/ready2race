import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {GapDocumentTemplateDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteGapDocumentTemplate, getGapDocumentTemplates} from '@api/sdk.gen.ts'
import {Preview} from '@mui/icons-material'
import {useState} from 'react'
import GapDocumentTemplatePreviewDialog from '@components/gapDocumentTemplate/GapDocumentTemplatePreviewDialog.tsx'

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
    const [previewId, setPreviewId] = useState<string | null>(null)
    const showPreview = previewId !== null

    const handleClosePreview = () => {
        setPreviewId(null)
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
    ]

    return (
        <>
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
