import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {
    CompetitionDto,
    CompetitionRegistrationTeamDto,
    deleteCompetitionRegistration,
    revertCompetitionDeregistration,
    getCompetitionRegistrations,
    OpenForRegistrationType,
} from '../../../../api'
import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo, useState} from 'react'
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
    Tooltip,
    Typography,
} from '@mui/material'
import {Warning} from '@mui/icons-material'
import QrCodeIcon from '@mui/icons-material/QrCode'
import {format} from 'date-fns'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {useUser} from "@contexts/user/UserContext.ts";
import {updateRegistrationGlobal} from "@authorization/privileges.ts";
import {PendingActions} from "@mui/icons-material";
import Cancel from '@mui/icons-material/Cancel'
import GroupRemoveIcon from '@mui/icons-material/GroupRemove'
import GroupAddIcon from '@mui/icons-material/GroupAdd'
import CompetitionDeregistrationDialog from '@components/event/competition/registration/CompetitionDeregistrationDialog.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useFeedback} from '@utils/hooks.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

type Props = BaseEntityTableProps<CompetitionRegistrationTeamDto> & {
    registrationState: OpenForRegistrationType
    competition: CompetitionDto
}

const CompetitionRegistrationTable = (
    {registrationState, ...props}: Props,
) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()

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
                renderCell: ({row}) => {
                    const teamName = row.name ? ` - ${row.name}` : ''
                    return (
                        <Stack direction={'row'} alignItems={'center'} spacing={1}>
                            <Typography>
                                {row.clubName}{teamName}
                            </Typography>
                            {row.isLate ? (
                                <Tooltip title={t('event.competition.registration.isLate')}>
                                    <PendingActions />
                                </Tooltip>
                            ) : (
                                <></>
                            )
                            }
                        </Stack>
                    )
                },
            },
            {
                field: 'name',
                headerName: t('entity.name'),
                valueGetter: value => value ?? '-',
            },
            {
                field: 'ratingCategory',
                headerName: t('event.competition.registration.ratingCategory'),
                minWidth: 150,
                renderCell: ({row}) => (
                    <Tooltip title={row.ratingCategory?.description}>
                        <Typography>
                            {row.ratingCategory?.name ?? '-'}
                        </Typography>
                    </Tooltip>
                )
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
                field: 'infos',
                headerName: t('event.competition.registration.infos'),
                sortable: false,
                minWidth: 200,
                renderCell: ({row}) => (
                    <Stack spacing={1}>
                        {row.deregistration ? (
                            <Stack direction={'row'} spacing={1}>
                                <Cancel />
                                <Typography>
                                    {t('event.competition.registration.deregister.deregistered') +
                                        (row.deregistration.reason
                                            ? ` (${row.deregistration.reason})`
                                            : '')}
                                </Typography>
                            </Stack>
                        ) : (
                            '-'
                        )}
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

    const [selectedRegForDeregistration, setSelectedRegForDeregistration] =
        useState<CompetitionRegistrationTeamDto | null>(null)
    const showDeregistrationDialog = selectedRegForDeregistration !== null

    const handleCloseDeregistrationDialog = () => {
        setSelectedRegForDeregistration(null)
    }

    const {confirmAction} = useConfirmation()

    const revertDeregistration = async (selectedRegistration: CompetitionRegistrationTeamDto) => {
        confirmAction(
            async () => {
                const {error} = await revertCompetitionDeregistration({
                    path: {
                        eventId: eventId,
                        competitionId: competitionId,
                        competitionRegistrationId: selectedRegistration.id,
                    },
                })
                if (error) {
                    if (error.status.value === 409) {
                        feedback.error(
                            t(
                                'event.competition.registration.deregister.revertDeregistration.error.locked',
                            ),
                        )
                    } else {
                        feedback.error(
                            t(
                                'event.competition.registration.deregister.revertDeregistration.error.unexpected',
                            ),
                        )
                    }
                } else {
                    feedback.success(
                        t('event.competition.registration.deregister.revertDeregistration.success'),
                    )
                }
                props.reloadData()
            },
            {
                content: t(
                    'event.competition.registration.deregister.revertDeregistration.confirmation',
                ),
                okText: t('event.competition.registration.deregister.revertDeregistration.revert'),
            },
        )
    }

    // todo: only allow revert if no next round has been created since
    // comment: validated in api?

    const afterRegistration = (isLate: boolean) =>
        isLate ? registrationState === 'CLOSED' : registrationState !== 'REGULAR'

    const customEntityActions = (entity: CompetitionRegistrationTeamDto): EntityAction[] => [
        entity.deregistration === undefined && afterRegistration(entity.isLate) ? (
            <GridActionsCellItem
                icon={<GroupRemoveIcon />}
                label={t('event.competition.registration.deregister.deregister')}
                onClick={() => setSelectedRegForDeregistration(entity)}
                showInMenu
            />
        ) : undefined,
        entity.deregistration !== undefined ? (
            <GridActionsCellItem
                icon={<GroupAddIcon />}
                label={t('event.competition.registration.deregister.revertDeregistration.revertDeregistration')}
                onClick={() => revertDeregistration(entity)}
                showInMenu
            />
        ) : undefined,
    ]

    // closed is already checked in parent component
    const writable = (dto: CompetitionRegistrationTeamDto) =>
        dto.isLate === (registrationState === 'LATE') || user.checkPrivilege(updateRegistrationGlobal)

    return (
        <Fragment>
            <EntityTable
                {...props}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                entityName={t('event.registration.registration')}
                deletableIf={writable}
                editableIf={writable}
                customEntityActions={customEntityActions}
            />
            <CompetitionDeregistrationDialog
                open={showDeregistrationDialog}
                competitionRegistration={
                    selectedRegForDeregistration
                        ? {
                              id: selectedRegForDeregistration?.id,
                              teamName:
                                  selectedRegForDeregistration.clubName +
                                  (selectedRegForDeregistration.name
                                      ? ` ${selectedRegForDeregistration.name}`
                                      : ''),
                          }
                        : null
                }
                onClose={handleCloseDeregistrationDialog}
                reloadData={props.reloadData}
            />
        </Fragment>
    )
}

export default CompetitionRegistrationTable
