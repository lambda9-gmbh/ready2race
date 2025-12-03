import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {ParticipantTrackingDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getParticipantTrackings} from '@api/sdk.gen.ts'
import {useMemo} from 'react'
import {format} from 'date-fns'
import EntityTable from '@components/EntityTable.tsx'
import {Chip} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'scannedAt', sort: 'desc'}]

const ParticipantTrackingLogTable = (props: BaseEntityTableProps<ParticipantTrackingDto>) => {
    const {t} = useTranslation()
    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantTrackings({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<ParticipantTrackingDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                flex: 1,
                minWidth: 100,
            },
            {
                field: 'firstName',
                headerName: t('entity.firstname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'lastName',
                headerName: t('entity.lastname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'externalClubName',
                headerName: t('club.participant.externalClub'),
                minWidth: 150,
                flex: 1,
            },
            {
                field: 'scanType',
                headerName: t('club.participant.tracking.status'),
                minWidth: 150,
                flex: 1,
                renderCell: ({row}) => (
                    <Chip
                        label={
                            row.scanType === 'ENTRY'
                                ? t('club.participant.tracking.in')
                                : t('club.participant.tracking.out')
                        }
                        color={row.scanType === 'ENTRY' ? 'success' : 'default'}
                        size="small"
                    />
                ),
            },
            {
                field: 'scannedAt',
                headerName: t('club.participant.tracking.lastScan.at'),
                minWidth: 100,
                maxWidth: 170,
                flex: 1,
                valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
            },
            {
                field: 'lastScanBy',
                headerName: t('club.participant.tracking.lastScan.by'),
                minWidth: 170,
                flex: 1,
                sortable: false,
                renderCell: ({row}) =>
                    row.lastScanBy ? row.lastScanBy.firstname + ' ' + row.lastScanBy.lastname : '-',
            },
        ],
        [],
    )

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('event.registration.registration')}
            mobileBreakpoint={'lg'}
        />
    )
}
export default ParticipantTrackingLogTable
