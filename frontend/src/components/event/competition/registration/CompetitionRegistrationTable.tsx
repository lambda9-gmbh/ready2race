import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {
    CompetitionRegistrationTeamDto,
    deleteCompetitionRegistration,
    getCompetitionRegistrations,
} from '../../../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {
    Box,
    Chip,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {Warning} from '@mui/icons-material'
import QrCodeIcon from '@mui/icons-material/QrCode'
import {format} from 'date-fns'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'

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

    const deleteRequest = (dto: CompetitionRegistrationTeamDto) =>
        deleteCompetitionRegistration({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: dto.id,
            },
        })

    const columns: GridColDef<CompetitionRegistrationTeamDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club') + ' / ' + t('entity.name'),
                minWidth: 250,
                renderCell: params => {
                    const teamName = params.row.name ? ` - ${params.row.name}` : ''
                    return `${params.row.clubName}${teamName}`
                },
            },
            {
                field: 'namedParticipants',
                headerName: t('club.participant.title'),
                flex: 2,
                minWidth: 300,
                sortable: false,
                renderCell: ({row}) => {
                    return (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{width: '40%'}}>{t('entity.name')}</TableCell>
                                    <TableCell sx={{width: '40%'}}>
                                        {t('event.competition.namedParticipant.namedParticipant')}
                                    </TableCell>
                                    <TableCell sx={{width: '20%'}}>{t('qrCode.qrCode')}</TableCell>
                                    <TableCell sx={{width: '20%'}}>
                                        {t('club.participant.tracking.status')}
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {row.namedParticipants.map(np =>
                                    np.participants.map(participant => (
                                        <TableRow key={participant.id}>
                                            <TableCell
                                                sx={{
                                                    width: '40%',
                                                }}>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                            <TableCell sx={{width: '40%'}}>
                                                {np.namedParticipantName}
                                            </TableCell>
                                            <TableCell sx={{width: '20%'}}>
                                                {participant.qrCodeId ? (
                                                    <HtmlTooltip
                                                        title={
                                                            <Box sx={{p: 1}}>
                                                                <Typography
                                                                    fontWeight={'bold'}
                                                                    gutterBottom>
                                                                    {t('qrCode.value')}:
                                                                </Typography>
                                                                <Typography>
                                                                    {participant.qrCodeId}
                                                                </Typography>
                                                            </Box>
                                                        }>
                                                        <QrCodeIcon />
                                                    </HtmlTooltip>
                                                ) : row.namedParticipants
                                                      .flatMap(np => np.participants)
                                                      .some(p => p.qrCodeId !== undefined) ? (
                                                    <></>
                                                ) : (
                                                    <HtmlTooltip
                                                        title={
                                                            <Typography>
                                                                {t('qrCode.noQrCodeAssigned')}
                                                            </Typography>
                                                        }>
                                                        <Warning color={'warning'} />
                                                    </HtmlTooltip>
                                                )}
                                            </TableCell>
                                            <TableCell sx={{width: '20%'}}>
                                                {participant.currentStatus !== undefined && (
                                                    <HtmlTooltip
                                                        title={
                                                            <>
                                                                {participant.lastScanAt && (
                                                                    <>
                                                                        <Typography variant={'h6'}>
                                                                            {t(
                                                                                'club.participant.tracking.lastScan.at',
                                                                            )}
                                                                        </Typography>
                                                                        <Typography>
                                                                            {format(
                                                                                new Date(
                                                                                    participant.lastScanAt,
                                                                                ),
                                                                                t(
                                                                                    'format.datetime',
                                                                                ),
                                                                            )}
                                                                        </Typography>
                                                                    </>
                                                                )}
                                                                {participant.lastScanBy && (
                                                                    <Typography>
                                                                        {t('common.by')}:{' '}
                                                                        {
                                                                            participant.lastScanBy
                                                                                .firstname
                                                                        }{' '}
                                                                        {
                                                                            participant.lastScanBy
                                                                                .lastname
                                                                        }
                                                                    </Typography>
                                                                )}
                                                            </>
                                                        }>
                                                        <Chip
                                                            label={
                                                                participant.currentStatus ===
                                                                'ENTRY'
                                                                    ? t(
                                                                          'club.participant.tracking.in',
                                                                      )
                                                                    : t(
                                                                          'club.participant.tracking.out',
                                                                      )
                                                            }
                                                            color={
                                                                participant.currentStatus ===
                                                                'ENTRY'
                                                                    ? 'success'
                                                                    : 'default'
                                                            }
                                                            size="small"
                                                        />
                                                    </HtmlTooltip>
                                                )}
                                            </TableCell>
                                        </TableRow>
                                    )),
                                )}
                            </TableBody>
                        </Table>
                    )
                },
            },
            {
                field: 'optionalFees',
                headerName: t('event.registration.optionalFee'),
                sortable: false,
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
                minWidth: 100,
                maxWidth: 170,
                flex: 1,
                valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
            },
        ],
        [],
    )

    return (
        <Fragment>
            <EntityTable
                {...props}
                withSearch={false}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                entityName={t('event.registration.registration')}
            />
        </Fragment>
    )
}

export default CompetitionRegistrationTable
