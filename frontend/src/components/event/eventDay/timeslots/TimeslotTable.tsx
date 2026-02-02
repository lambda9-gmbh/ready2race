import {GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {deleteTimeslot, getTimeslots} from '@api/sdk.gen.ts'
import {TimeslotDto} from '@api/types.gen.ts'
import {eventDayRoute, eventRoute} from '@routes'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {useState} from 'react'
import {Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton} from '@mui/material'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 100,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'startTime', sort: 'asc'}]

const TimeslotTable = (props: BaseEntityTableProps<TimeslotDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const [selectedDescription, setSelectedDescription] = useState<string | null>(null)

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getTimeslots({
            signal,
            path: {
                eventDayId: eventDayId,
                eventId: eventId,
            },
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: TimeslotDto) => {
        return deleteTimeslot({
            path: {timeslotId: dto.id, eventDayId: eventDayId, eventId: eventId},
        })
    }

    const columns: ExtendedGridColDef<TimeslotDto>[] = [
        {
            field: 'name',
            headerName: t('event.eventDay.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'startTime',
            headerName: t('event.eventDay.startTime'),
            minWidth: 110,
            sortable: true,
            renderCell: ({value}) => value.slice(0, 5),
        },
        {
            field: 'endTime',
            headerName: t('event.eventDay.endTime'),
            minWidth: 110,
            sortable: false,
            renderCell: ({value}) => value.slice(0, 5),
        },
        {
            field: 'description',
            headerName: t('event.eventDay.description'),
            flex: 2,
            sortable: false,
            renderCell: ({value}) =>
                value && (
                    <>
                        <IconButton onClick={() => setSelectedDescription(value)}>
                            <VisibilityOutlinedIcon />
                        </IconButton>
                    </>
                ),
        },
    ]

    return (
        <>
            <EntityTable
                {...props}
                parentResource={'EVENT'}
                publicRead={true}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
            />
            <Dialog open={!!selectedDescription} onClose={() => setSelectedDescription(null)}>
                <DialogTitle>{t('event.eventDay.description')}</DialogTitle>
                <DialogContent>{selectedDescription}</DialogContent>
                <DialogActions>
                    <Button onClick={() => setSelectedDescription(null)}>Close</Button>
                </DialogActions>
            </Dialog>
        </>
    )
}

export default TimeslotTable
