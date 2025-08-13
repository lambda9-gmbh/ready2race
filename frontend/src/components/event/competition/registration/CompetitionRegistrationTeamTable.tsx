import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {CompetitionRegistrationTeamDto, getCompetitionRegistrationTeams} from '../../../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {useMemo} from 'react'
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
import {CheckCircle, Info, Warning} from '@mui/icons-material'
import QrCodeIcon from '@mui/icons-material/QrCode'
import {format} from 'date-fns'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import Cancel from '@mui/icons-material/Cancel'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

type Props = BaseEntityTableProps<CompetitionRegistrationTeamDto>

const CompetitionRegistrationTeamTable = ({...props}: Props) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrationTeams({
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
                headerName: t('event.registration.teamMembers'),
                flex: 2,
                minWidth: 300,
                sortable: false,
                renderCell: ({row}) => {
                    return (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{width: '30%'}}>{t('entity.name')}</TableCell>
                                    <TableCell sx={{width: '25%'}}>
                                        {t('event.competition.namedParticipant.namedParticipant')}
                                    </TableCell>
                                    <TableCell sx={{width: '15%'}}>{t('qrCode.qrCode')}</TableCell>
                                    <TableCell sx={{width: '15%'}}>
                                        {t('club.participant.tracking.status')}
                                    </TableCell>
                                    <TableCell sx={{width: '15%'}}>
                                        {t('event.participantRequirement.approved')}
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {row.namedParticipants.map(np =>
                                    np.participants.map(participant => (
                                        <TableRow key={participant.id}>
                                            <TableCell
                                                sx={{
                                                    width: '30%',
                                                }}>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                            <TableCell sx={{width: '25%'}}>
                                                {np.namedParticipantName}
                                            </TableCell>
                                            <TableCell sx={{width: '15%'}}>
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
                                            <TableCell sx={{width: '15%'}}>
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
                                            <TableCell sx={{width: '15%'}}>
                                                <Stack
                                                    direction={'row'}
                                                    spacing={1}
                                                    alignItems={'center'}>
                                                    <Typography>
                                                        {
                                                            participant
                                                                .participantRequirementsChecked
                                                                .length
                                                        }
                                                        /
                                                        {row.globalParticipantRequirements.length +
                                                            np.participantRequirements.length}{' '}
                                                    </Typography>
                                                    <HtmlTooltip
                                                        placement={'right'}
                                                        title={
                                                            <Stack spacing={1} p={1}>
                                                                {[
                                                                    ...row.globalParticipantRequirements.map(
                                                                        gpr => ({
                                                                            ...gpr,
                                                                            qrCodeRequired: false,
                                                                        }),
                                                                    ),
                                                                    ...np.participantRequirements,
                                                                ].map(req => (
                                                                    <Stack
                                                                        direction={'row'}
                                                                        spacing={1}
                                                                        key={req.id}>
                                                                        {participant.participantRequirementsChecked.some(
                                                                            c => c.id === req.id,
                                                                        ) ? (
                                                                            <CheckCircle
                                                                                color={'success'}
                                                                            />
                                                                        ) : (
                                                                            <Cancel
                                                                                color={'error'}
                                                                            />
                                                                        )}
                                                                        <Typography>
                                                                            {req.name}{' '}
                                                                            {req.optional
                                                                                ? ` (${t('entity.optional')})`
                                                                                : ''}
                                                                            {req.qrCodeRequired &&
                                                                                ' (QR)'}
                                                                        </Typography>
                                                                    </Stack>
                                                                ))}
                                                            </Stack>
                                                        }>
                                                        <Info color={'info'} fontSize={'small'} />
                                                    </HtmlTooltip>
                                                </Stack>
                                            </TableCell>
                                        </TableRow>
                                    )),
                                )}
                            </TableBody>
                        </Table>
                    )
                },
            },
        ],
        [],
    )

    return (
        <EntityTable
            {...props}
            parentResource={'REGISTRATION'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('event.registration.teams')}
        />
    )
}

export default CompetitionRegistrationTeamTable
