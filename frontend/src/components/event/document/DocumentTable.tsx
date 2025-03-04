import EntityTable from '@components/EntityTable.tsx'
import {BaseEntityTableProps, EntityTableAction} from '@utils/types.ts'
import {EventDocumentDto} from '@api/types.gen.ts'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteDocument, downloadDocument, getDocuments} from '@api/sdk.gen.ts'
import {eventIndexRoute} from '@routes'
import {Download} from '@mui/icons-material'
import {useRef} from 'react'
import {Link} from '@mui/material'
import {useTranslation} from "react-i18next";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const DocumentTable = (props: BaseEntityTableProps<EventDocumentDto>) => {
    const downloadRef = useRef<HTMLAnchorElement>(null)
    const {eventId} = eventIndexRoute.useParams()
    const {t} = useTranslation()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
        getDocuments({
            signal,
            path: {
                eventId,
            },
            query: {...paginationParameters},
        })

    const deleteRequest = (entity: EventDocumentDto) =>
        deleteDocument({
            path: {
                eventId,
                eventDocumentId: entity.id,
            },
        })

    const columns: GridColDef<EventDocumentDto>[] = [
        {
            field: 'name',
            headerName: t('event.document.name'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'documentType',
            headerName: t('event.document.type.type'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, row) => row.documentType?.name,
        },
    ]

    const handleDownloadDocument = async (entity: EventDocumentDto) => {
        // todo: error handling
        const {data} = await downloadDocument({
            path: {
                eventId,
                eventDocumentId: entity.id,
            },
        })
        const anchor = downloadRef.current
        if (data != undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = entity.name
            //anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const customActions = (entity: EventDocumentDto): EntityTableAction[] => [
        <GridActionsCellItem
            icon={<Download />}
            label={t('event.document.download.download')}
            onClick={() => handleDownloadDocument(entity)}
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
                customActions={customActions}
            />
        </>
    )
}

export default DocumentTable
