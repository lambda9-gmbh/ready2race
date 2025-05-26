import EntityTable from '@components/EntityTable.tsx'
import {BaseEntityTableProps} from '@utils/types.ts'
import {TaskDto} from '@api/types.gen.ts'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteTask, getTasks} from '@api/sdk.gen.ts'
import {eventIndexRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import {TaskStateIcon} from '@components/event/task/TaskStateIcon.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'state', sort: 'asc'}]

const TaskTable = (props: BaseEntityTableProps<TaskDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
        getTasks({
            signal,
            path: {
                eventId,
            },
            query: {...paginationParameters},
        })

    const deleteRequest = (entity: TaskDto) =>
        deleteTask({
            path: {
                eventId,
                taskId: entity.id,
            },
        })

    const columns: GridColDef<TaskDto>[] = [
        {
            field: 'state',
            headerName: t('task.state'),
            minWidth: 150,
            renderCell: ({row}) => <TaskStateIcon state={row.state} showLabel={true} />,
        },
        {
            field: 'name',
            headerName: t('task.name'),
            minWidth: 200,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            flex: 1,
        },
        {
            field: 'dueDate',
            headerName: t('task.dueDate'),
            valueGetter: v => (v ? new Date(v) : null),
            type: 'dateTime',
            minWidth: 200,
        },
        {
            field: 'responsibleUsers',
            headerName: t('task.responsibleUsers'),
            sortable: false,
            minWidth: 200,
            renderCell: ({row}) =>
                row.responsibleUsers.map(u => `${u.firstname} ${u.lastname}`).join(', '),
        },
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
            />
        </>
    )
}

export default TaskTable
