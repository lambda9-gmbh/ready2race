import {PaginationParameters} from '@utils/ApiUtils.ts'
import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {BaseEntityTableProps} from '@utils/types.ts'
import {eventIndexRoute} from '@routes'
import {CompetitionDto} from '@api/types.gen.ts'
import {deleteCompetition, getCompetitions} from '@api/sdk.gen.ts'
import {Chip} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'identifier', sort: 'asc'}]

const CompetitionTable = (props: BaseEntityTableProps<CompetitionDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitions({
            signal,
            path: {eventId: eventId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: CompetitionDto) => {
        return deleteCompetition({path: {eventId: dto.event, competitionId: dto.id}})
    }

    const columns: GridColDef<CompetitionDto>[] = [
        {
            field: 'identifier',
            headerName: t('event.competition.identifier'),
            minWidth: 120,
            flex: 0,
            valueGetter: (_, e) => e.properties.identifier,
        },
        {
            field: 'shortName',
            headerName: t('event.competition.shortName'),
            minWidth: 120,
            flex: 0,
            valueGetter: (_, e) => e.properties.shortName,
        },
        {
            field: 'name',
            headerName: t('event.competition.name'),
            minWidth: 150,
            flex: 1,
            valueGetter: (_, e) => e.properties.name,
        },
        {
            field: 'competitionCategory',
            headerName: t('event.competition.category.category'),
            minWidth: 150,
            flex: 0,
            valueGetter: (_, row) => row.properties.competitionCategory?.name ?? '',
        },
        {
            field: 'registrationCount',
            headerName: t('event.competition.registrationCount'),
            type: 'number',
            renderCell: ({formattedValue}) => <Chip label={formattedValue} />,
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
            linkColumn={entity => ({
                to: '/event/$eventId/competition/$competitionId',
                params: {eventId: entity.event, competitionId: entity.id},
            })}
            entityName={t('event.competition.competition')}
            deleteRequest={deleteRequest}
        />
    )
}

export default CompetitionTable
