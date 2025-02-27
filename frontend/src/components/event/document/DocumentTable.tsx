import EntityTable from '@components/EntityTable.tsx'
import {BaseEntityTableProps, EntityTableAction} from '@utils/types.ts'
import {EventDocumentDto} from '@api/types.gen.ts'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {addDocuments, deleteDocument, downloadDocument, getDocuments} from '@api/sdk.gen.ts'
import {eventIndexRoute} from '@routes'
import SelectFileButton from '@components/SelectFileButton.tsx'
import {Download} from '@mui/icons-material'
import {useRef} from 'react'
import {Link} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const DocumentTable = (props: BaseEntityTableProps<EventDocumentDto>) => {
    const downloadRef = useRef<HTMLAnchorElement>(null)
    const {eventId} = eventIndexRoute.useParams()

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

    const uploadFiles = async (files: FileList) => {
        // todo: error handling
        await addDocuments({
            path: {
                eventId,
            },
            body: {
                files: Array.from(files),
            },
        })
        props.reloadData()
    }

    const columns: GridColDef<EventDocumentDto>[] = [
        {
            field: 'name',
            headerName: '[todo] Bezeichnung',
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'documentType',
            headerName: '[todo] Typ',
            minWidth: 200,
            flex: 1,
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
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const customActions = (entity: EventDocumentDto): EntityTableAction[] => [
        <GridActionsCellItem
            icon={<Download />}
            label={'[todo] download'}
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
                customAdd={
                    <SelectFileButton
                        variant={'outlined'}
                        multiple
                        onSelected={uploadFiles}
                        accept={'application/pdf'}>
                        todo Upload
                    </SelectFileButton>
                }
                customActions={customActions}
            />
        </>
    )
}

export default DocumentTable
