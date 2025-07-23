import { useTranslation } from 'react-i18next'
import EntityTable, { ExtendedGridColDef } from '@components/EntityTable.tsx'
import { BaseEntityTableProps, PageResponse } from '@utils/types.ts'
import { CatererTransactionViewDto, type GetEventCatererTransactionsError } from '@api/types.gen.ts'
import { format } from 'date-fns'
import { GridPaginationModel, GridSortModel } from '@mui/x-data-grid'
import { PaginationParameters } from '@utils/ApiUtils.ts'
import { RequestResult } from '@hey-api/client-fetch'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'createdAt', sort: 'desc'}]

type Props = BaseEntityTableProps<CatererTransactionViewDto> & {
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<CatererTransactionViewDto>, GetEventCatererTransactionsError, false>
}

const CatererTransactionTable = (props: Props) => {
  const { t } = useTranslation()

  const columns: ExtendedGridColDef<CatererTransactionViewDto>[] = [
    {
      field: 'catererFirstname',
      headerName: t('catererTransaction.catererName'),
      minWidth: 200,
      flex: 1,
      renderCell: (params) => `${params.row.catererFirstname} ${params.row.catererLastname}`
    },
    {
      field: 'userFirstname',
      headerName: t('catererTransaction.userName'),
      minWidth: 200,
      flex: 1,
      renderCell: (params) => `${params.row.userFirstname} ${params.row.userLastname}`
    },
    {
      field: 'price',
      headerName: t('catererTransaction.price'),
      minWidth: 150,
      valueFormatter: v => v + ' €'
    },
    {
      field: 'createdAt',
      headerName: t('catererTransaction.createdAt'),
      minWidth: 200,
      valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null)
    }
  ]

  return (
    <EntityTable
      {...props}
      resource="INVOICE"
      initialPagination={initialPagination}
      pageSizeOptions={pageSizeOptions}
      initialSort={initialSort}
      columns={columns}
    />
  )
}

export default CatererTransactionTable