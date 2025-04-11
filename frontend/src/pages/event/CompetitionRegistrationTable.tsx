import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {CompetitionRegistrationTeamDto, getCompetitionRegistrations} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {Grid2, Stack, Typography} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const CompetitionRegistrationTable = (
    props: BaseEntityTableProps<CompetitionRegistrationTeamDto>,
) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrations({
            signal,
            path: {eventId, competitionId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<CompetitionRegistrationTeamDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                minWidth: 200,
            },
            {
                field: 'name',
                headerName: t('entity.name'),
                valueGetter: value => value ?? '-',
            },
            {
                field: 'namedParticipants',
                headerName: t('club.participant.title'),
                flex: 1,
                renderCell: ({row}) => (
                    <Grid2 container spacing={1}>
                        {row.namedParticipants.map(np => (
                            <>
                                {row.namedParticipants.length > 1 && (
                                    <Grid2 direction={'row'} spacing={2} size={2}>
                                        <Typography variant={'subtitle2'}>
                                            {np.namedParticipantName}:
                                        </Typography>
                                    </Grid2>
                                )}
                                <Grid2 spacing={1} size={10}>
                                    {np.participants.map(p => (
                                        <Typography variant={'body2'}>
                                            {p.firstname} {p.lastname}{' '}
                                            {p.externalClubName && `(${p.externalClubName})`}
                                        </Typography>
                                    ))}
                                </Grid2>
                            </>
                        ))}
                    </Grid2>
                ),
            },
            {
                field: 'optionalFees',
                headerName: t('event.registration.optionalFee'),
                renderCell: ({row}) => (
                    <Stack>
                        {row.optionalFees.length >= 1
                            ? row.optionalFees.map(f => f.feeName).join(', ')
                            : '-'}
                    </Stack>
                ),
            },
            {
                field: 'updatedAt',
                headerName: t('entity.updatedAt'),
                minWidth: 200,
                type: 'dateTime',
                valueGetter: v => new Date(v),
            },
        ],
        [],
    )

    return (
        <Fragment>
            <EntityTable
                {...props}
                options={{entityCreate: false, entityUpdate: false}}
                withSearch={false}
                gridProps={{getRowId: row => row.id}}
                parentResource={'EVENT'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('club.participant.title')}
            />
        </Fragment>
    )
}

export default CompetitionRegistrationTable
