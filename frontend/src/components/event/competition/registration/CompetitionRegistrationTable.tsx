import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {
    CompetitionRegistrationDto,
    deleteCompetitionRegistration,
    DeleteCompetitionRegistrationError,
    getCompetitionRegistrations,
    OpenForRegistrationType,
    revertCompetitionDeregistration,
} from '../../../../api'
import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo, useState} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {
    Button,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from '@mui/material'
import {Add, PendingActions} from '@mui/icons-material'
import {format} from 'date-fns'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateRegistrationGlobal} from '@authorization/privileges.ts'
import Cancel from '@mui/icons-material/Cancel'
import GroupRemoveIcon from '@mui/icons-material/GroupRemove'
import GroupAddIcon from '@mui/icons-material/GroupAdd'
import CompetitionDeregistrationDialog from '@components/event/competition/registration/CompetitionDeregistrationDialog.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useFeedback} from '@utils/hooks.ts'
import InitializeRegistrationDialog from '@components/eventRegistration/InitializeRegistrationDialog.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

type Props = BaseEntityTableProps<CompetitionRegistrationDto> & {
    registrationState: OpenForRegistrationType
    reloadEvent: () => void
    challengeEvent: boolean
    registrationInitialized: boolean
    documentsAccepted: boolean | null
}

const CompetitionRegistrationTable = ({
    registrationState,
    reloadEvent,
    challengeEvent,
    ...props
}: Props) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()

    const [showInitRegisterDialog, setShowInitRegisterDialog] = useState(false)

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const canInitializeRegistration =
        user.loggedIn && user.clubId !== undefined && !props.registrationInitialized
    const canAcceptDocuments =
        user.loggedIn &&
        user.clubId !== undefined &&
        props.registrationInitialized &&
        !props.documentsAccepted

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrations({
            signal,
            path: {eventId, competitionId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: CompetitionRegistrationDto) =>
        deleteCompetitionRegistration({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: dto.id,
            },
        })

    const onDeleteError = (error: DeleteCompetitionRegistrationError) => {
        if (error.status.value === 409) {
            feedback.error(t('event.competition.registration.delete.error.roundExisting'))
        } else {
            feedback.error(t('entity.delete.error', {entity: props.entityName}))
        }
    }

    const columns: GridColDef<CompetitionRegistrationDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.registrant'),
                minWidth: 150,
            },
            {
                field: 'name',
                headerName: t('entity.name'),
                valueGetter: value => value ?? '-',
                renderCell: ({row}) => {
                    return (
                        <Stack direction={'row'} alignItems={'center'} spacing={1}>
                            {row.name && <Typography>{row.name}</Typography>}
                            {row.isLate ? (
                                <Tooltip title={t('event.competition.registration.isLate')}>
                                    <PendingActions />
                                </Tooltip>
                            ) : (
                                <></>
                            )}
                        </Stack>
                    )
                },
            },
            {
                field: 'ratingCategory',
                headerName: t('event.competition.registration.ratingCategory'),
                minWidth: 150,
                renderCell: ({row}) => (
                    <Tooltip title={row.ratingCategory?.description}>
                        <Typography>{row.ratingCategory?.name ?? '-'}</Typography>
                    </Tooltip>
                ),
            },
            {
                field: 'namedParticipants',
                headerName: t('event.registration.teamMembers'),
                flex: 2,
                minWidth: 350,
                sortable: false,
                renderCell: ({row}) => {
                    return (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{width: '26%'}}>{t('entity.name')}</TableCell>
                                    <TableCell sx={{width: '26%'}}>
                                        {t('event.competition.namedParticipant.namedParticipant')}
                                    </TableCell>
                                    <TableCell sx={{width: '26%'}}>{t('club.club')}</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {row.namedParticipants.map(np =>
                                    np.participants.map(participant => (
                                        <TableRow key={participant.id}>
                                            <TableCell
                                                sx={{
                                                    width: '26%',
                                                }}>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                            <TableCell sx={{width: '26%'}}>
                                                {np.namedParticipantName}
                                            </TableCell>
                                            <TableCell sx={{width: '26%'}}>
                                                {participant.externalClubName ??
                                                    participant.clubName}
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
                minWidth: 120,
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
                minWidth: 150,
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
        useState<CompetitionRegistrationDto | null>(null)
    const showDeregistrationDialog = selectedRegForDeregistration !== null

    const handleCloseDeregistrationDialog = () => {
        setSelectedRegForDeregistration(null)
    }

    const {confirmAction} = useConfirmation()

    const revertDeregistration = async (selectedRegistration: CompetitionRegistrationDto) => {
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

    const afterRegistration = (isLate: boolean) =>
        isLate ? registrationState === 'CLOSED' : registrationState !== 'REGULAR'

    const customEntityActions = (entity: CompetitionRegistrationDto): EntityAction[] => [
        entity.deregistration === undefined &&
        user.checkPrivilege(updateRegistrationGlobal) &&
        afterRegistration(entity.isLate) ? (
            <GridActionsCellItem
                icon={<GroupRemoveIcon />}
                label={t('event.competition.registration.deregister.deregister')}
                onClick={() => setSelectedRegForDeregistration(entity)}
                showInMenu
            />
        ) : undefined,
        entity.deregistration !== undefined && user.checkPrivilege(updateRegistrationGlobal) ? (
            <GridActionsCellItem
                icon={<GroupAddIcon />}
                label={t(
                    'event.competition.registration.deregister.revertDeregistration.revertDeregistration',
                )}
                onClick={() => revertDeregistration(entity)}
                showInMenu
            />
        ) : undefined,
    ]

    // closed is already checked in parent component
    const writable = (dto: CompetitionRegistrationDto) =>
        dto.isLate === (registrationState === 'LATE') ||
        user.checkPrivilege(updateRegistrationGlobal)

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
                onDeleteError={onDeleteError}
                entityName={t('event.registration.registration')}
                creatable={props.registrationInitialized && (props.documentsAccepted ?? false)}
                deletableIf={writable}
                editableIf={writable}
                customEntityActions={customEntityActions}
                customTableActions={
                    canInitializeRegistration || canAcceptDocuments ? (
                        <Button
                            variant={'outlined'}
                            startIcon={<Add />}
                            onClick={() => setShowInitRegisterDialog(true)}>
                            {t('entity.add.action', {entity: t('event.registration.registration')})}
                        </Button>
                    ) : undefined
                }
            />
            {(canInitializeRegistration || canAcceptDocuments) && (
                <InitializeRegistrationDialog
                    eventId={eventId}
                    open={showInitRegisterDialog}
                    onClose={() => setShowInitRegisterDialog(false)}
                    onSuccess={() => {
                        reloadEvent()
                        props.openDialog()
                    }}
                    registrationInitialized={props.registrationInitialized}
                />
            )}
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
