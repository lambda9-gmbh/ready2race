import EntityTable from '@components/EntityTable.tsx'
import {BaseEntityTableProps} from '@utils/types.ts'
import {EventDocumentTypeDto} from '@api/types.gen.ts'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteDocumentType, getDocumentTypes} from '@api/sdk.gen.ts'
import {useTranslation} from "react-i18next";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getDocumentTypes({
        signal,
        query: {...paginationParameters},
    })

const deleteRequest = (entity: EventDocumentTypeDto) =>
    deleteDocumentType({
        path: {
            eventDocumentTypeId: entity.id,
        },
    })

const DocumentTypeTable = (props: BaseEntityTableProps<EventDocumentTypeDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<EventDocumentTypeDto>[] = [
        {
            field: 'name',
            headerName: t('event.document.type.name'),
            minWidth: 200,
            flex: 1,
        },
    ]

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            deleteRequest={deleteRequest}
        />
    )
}

export default DocumentTypeTable
