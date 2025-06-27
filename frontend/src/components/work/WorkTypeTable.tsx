import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteWorkType, getWorkTypes} from '@api/sdk.gen.ts'
import {DeleteNamedParticipantError, WorkTypeDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {Box} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getWorkTypes({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: WorkTypeDto) => {
    return deleteWorkType({path: {workTypeId: dto.id}})
}

const WorkTypeTable = (props: BaseEntityTableProps<WorkTypeDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<WorkTypeDto>[] = [
        {
            field: 'color',
            headerName: t('entity.color'),
            sortable: false,
            renderCell: ({value}) => (
                <Box
                    width={48}
                    height={24}
                    sx={{background: value}}
                    borderRadius={8}
                    border={'1px solid #bfbfbf'}
                />
            ),
        },
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            flex: 2,
            sortable: false,
        },
        {
            field: 'minUser',
            headerName: t('work.shift.minUser'),
            sortable: false,
        },
        {
            field: 'maxUser',
            headerName: t('work.shift.maxUser'),
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
            entityName={t('work.type.type')}
            deleteRequest={deleteRequest}
            onDeleteError={onDeleteError}
        />
    )
}

export default WorkTypeTable
