import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {GridActionsCellItem, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps, EntityTableAction, PageResponse} from '@utils/types.ts'
import {type GetRegistrationInvoicesError, InvoiceDto, Resource} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {RequestResult} from '@hey-api/client-fetch'
import { Download } from '@mui/icons-material'
import {downloadInvoice} from '@api/sdk.gen.ts'
import {useRef} from 'react'
import {Link} from '@mui/material'
import {useFeedback} from '@utils/hooks.ts'
import {format} from "date-fns";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'invoiceNumber', sort: 'asc'}]

type Props = BaseEntityTableProps<InvoiceDto> & {
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<InvoiceDto>, GetRegistrationInvoicesError, false>
} & (
    | {
    resource: Resource
    parentResource?: never
}
    | {
    resource?: never
    parentResource: Resource
}
    )

const InvoiceTable = (props: Props) => {

    const {t} = useTranslation()
    const feedback = useFeedback()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const columns: ExtendedGridColDef<InvoiceDto>[] = [
        {
            field: 'invoiceNumber',
            headerName: t('invoice.invoiceNumber'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'totalAmount',
            headerName: t('invoice.amount'),
            minWidth: 150,
            flex: 0,
            sortable: false,
            valueFormatter: v => v + ' €'
        },
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            minWidth: 150,
            flex: 0,
            valueGetter: (v: string) => v ? format(new Date(v), t('format.datetime')) : null,
        }
    ]

    const handleDownload = async (invoiceId: string) => {
        const {data, error, response} = await downloadInvoice({path:{invoiceId}})
        const anchor = downloadRef.current

        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/attachment; filename="?(.+)"?/)?.[1]

        if (error || !filename) {
            feedback.error(t('invoice.downloadError'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = filename
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const customEntityActions = (entity: InvoiceDto): EntityTableAction[] => [
        <GridActionsCellItem
            icon={<Download />}
            label={t('invoice.download')}
            onClick={() => handleDownload(entity.id)}
            showInMenu
        />
    ]

    return (
        <>
            <Link ref={downloadRef} display={'none'}></Link>
            <EntityTable
                {...props}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                customEntityActions={customEntityActions}
            />
        </>
    )
}

export default InvoiceTable