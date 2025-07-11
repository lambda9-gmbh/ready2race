import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {DocumentTemplateDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteDocumentTemplate, getDocumentTemplates} from '@api/sdk.gen.ts'
import {Preview} from "@mui/icons-material";
import {useState} from "react";
import DocumentTemplatePreviewDialog from "@components/documentTemplate/DocumentTemplatePreviewDialog.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | { value: number; label: string })[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getDocumentTemplates({
        signal,
        query: {...paginationParameters},
    })

const deleteRequest = (dto: DocumentTemplateDto) =>
    deleteDocumentTemplate({path: {documentTemplateId: dto.id}})

const DocumentTemplateTable = (props: BaseEntityTableProps<DocumentTemplateDto>) => {
    const {t} = useTranslation()
    const [previewId, setPreviewId] = useState<string | null>(null)
    const showPreview = previewId !== null

    const handleClosePreview = () => {
        setPreviewId(null)
    }

    const columns: GridColDef<DocumentTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('document.template.name'),
            minWidth: 200,
            flex: 1,
        },
    ]


    const customEntityActions = (entity: DocumentTemplateDto): EntityAction[] => [
        <GridActionsCellItem
            icon={<Preview/>}
            label={t('document.template.preview.show')}
            onClick={() => setPreviewId(entity.id)}
            showInMenu
        />
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
            <DocumentTemplatePreviewDialog open={showPreview} onClose={handleClosePreview}
                                           documentTemplateId={previewId}/>
        </>
    )
}

export default DocumentTemplateTable
