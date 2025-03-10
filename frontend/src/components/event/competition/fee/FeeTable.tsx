import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteFee, getFees} from '@api/sdk.gen.ts'
import {DeleteNamedParticipantError, FeeDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {useFeedback} from '@utils/hooks.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getFees({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: FeeDto) => {
    return deleteFee({path: {feeId: dto.id}})
}

const FeeTable = (props: BaseEntityTableProps<FeeDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<FeeDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.fee.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.competition.fee.description'),
            flex: 2,
            sortable: false,
        },
    ]

    const onDeleteError = (error: DeleteNamedParticipantError) => {
        if (error.status.value === 409) {
            feedback.error(t('event.competition.error.referenced', {entity: props.entityName}))
        } else {
            feedback.error(t('entity.delete.error', {entity: props.entityName}))
        }
    }

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('event.competition.fee.fee')}
            deleteRequest={deleteRequest}
            onDeleteError={onDeleteError}
        />
    )
}

export default FeeTable
