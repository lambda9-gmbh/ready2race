import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {
    EventDto,
    getActiveParticipantRequirementsForEvent,
    getNamedParticipantsForEvent,
    getParticipantsForEvent,
    ParticipantForEventDto,
    ParticipantRequirementCheckForEventConfigDto,
    resendAccessToken,
} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {Cancel, CheckCircle, Delete, Edit, Email, Info, VerifiedUser} from '@mui/icons-material'
import SplitButton, {SplitButtonOption} from '@components/SplitButton.tsx'
import {Fragment, useMemo, useState} from 'react'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import ParticipantRequirementApproveManuallyForEventDialog, {
    ParticipantRequirementApproveManuallyForEventForm,
} from '@components/event/participantRequirement/ParticipantRequirementApproveManuallyForEventDialog.tsx'
import ParticipantRequirementCheckForEventUploadFileDialog from '@components/event/participantRequirement/ParticipantRequirementCheckForEventUploadFileDialog.tsx'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {Box, Stack, Typography, useMediaQuery, useTheme} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {deleteQrCode} from '@api/sdk.gen.ts'
import {QrCodeEditDialog} from '@components/participant/QrCodeEditDialog.tsx'
import QrCodeIcon from '@mui/icons-material/QrCode'

// TODO: validate/sanitize basepath (also in routes.tsx)
const basepath = document.getElementById('ready2race-root')!.dataset.basepath

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

type Props = BaseEntityTableProps<ParticipantForEventDto> & {
    eventData: EventDto
}

const ParticipantForEventTable = ({eventData, ...props}: Props) => {
    const {t} = useTranslation()
    const user = useUser()
    const {confirmAction} = useConfirmation()
    const feedback = useFeedback()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('lg'))

    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [editQrParticipant, setEditQrParticipant] = useState<ParticipantForEventDto | null>(null)

    const eventId = eventData.id

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const {data: requirementsData} = useFetch(signal =>
        getActiveParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}])},
        }),
    )

    const {data: namedParticipantsForEvent} = useFetch(signal =>
        getNamedParticipantsForEvent({
            signal,
            path: {eventId},
        }),
    )

    const columns: GridColDef<ParticipantForEventDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                flex: 1,
                minWidth: 100,
            },
            {
                field: 'firstname',
                headerName: t('entity.firstname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'lastname',
                headerName: t('entity.lastname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'gender',
                headerName: t('entity.gender'),
            },
            {
                field: 'year',
                headerName: t('club.participant.year'),
            },
            ...(!isMobile
                ? [
                      {
                          field: 'externalClubName',
                          headerName: t('club.participant.externalClub'),
                          minWidth: 150,
                          flex: 1,
                          renderCell: ({row}: {row: ParticipantForEventDto}) => (
                              <Typography>{row.externalClubName ?? '-'}</Typography>
                          ),
                      },
                  ]
                : []),
            {
                field: 'participantRequirementsChecked',
                headerName: t('event.participantRequirement.approved'),
                maxWidth: 200,
                minWidth: 120,
                flex: 1,
                sortable: false,
                renderCell: ({row}) => {
                    const globalRequirements = requirementsData?.data || []

                    // Find all named participants for this specific row
                    const rowNamedParticipants =
                        namedParticipantsForEvent?.filter(np =>
                            row.namedParticipantIds?.includes(np.id),
                        ) || []

                    // Get requirements specific to this participant's named participants
                    const namedParticipantRequirements = rowNamedParticipants.flatMap(np =>
                        (np.requirements || []).map(req => ({...req, participantName: np.name})),
                    )

                    // Get all named participant requirement IDs across all named participants
                    const allNamedRequirementIds = new Set(
                        namedParticipantsForEvent?.flatMap(
                            np => np.requirements?.map(r => r.requirementId) || [],
                        ) || [],
                    )

                    // Create a map to deduplicate requirements and track their assignment type
                    const requirementMap = new Map()

                    // Add global requirements ONLY if they are not assigned to any named participant
                    globalRequirements.forEach(r => {
                        if (!allNamedRequirementIds.has(r.id)) {
                            requirementMap.set(r.id, {
                                id: r.id,
                                name: r.name,
                                assignmentType: 'global',
                                qrCodeRequired: false,
                                checked:
                                    row.participantRequirementsChecked?.some(c => c.id === r.id) ??
                                    false,
                                note: row.participantRequirementsChecked?.find(c => c.id === r.id)
                                    ?.note,
                            })
                        }
                    })

                    // Add named participant requirements for this specific participant
                    namedParticipantRequirements.forEach(req => {
                        requirementMap.set(req.requirementId, {
                            id: req.requirementId,
                            name: req.requirementName,
                            assignmentType: 'named',
                            participantName: req.participantName || 'Unknown',
                            qrCodeRequired: req.qrCodeRequired,
                            checked:
                                row.participantRequirementsChecked?.some(
                                    c => c.id === req.requirementId,
                                ) ?? false,
                            note: row.participantRequirementsChecked?.find(
                                c => c.id === req.requirementId,
                            )?.note,
                        })
                    })

                    const deduplicatedRequirements = Array.from(requirementMap.values())

                    if (deduplicatedRequirements.length === 0) {
                        return ' - '
                    }

                    if (isMobile) {
                        return (
                            <Stack spacing={0.5} sx={{width: 1}}>
                                {!isMobile && (
                                    <Typography variant="caption" color="text.secondary">
                                        ({row.participantRequirementsChecked?.length ?? 0}/
                                        {deduplicatedRequirements.length})
                                    </Typography>
                                )}
                                <Stack spacing={0.5} sx={{pl: 0.5}}>
                                    {deduplicatedRequirements.map(req => (
                                        <Stack
                                            direction="row"
                                            spacing={0.5}
                                            alignItems="center"
                                            key={req.id}>
                                            {req.checked ? (
                                                <CheckCircle color="success" sx={{fontSize: 16}} />
                                            ) : (
                                                <Cancel color="error" sx={{fontSize: 16}} />
                                            )}
                                            <Typography variant="caption">
                                                {req.name} (
                                                {req.assignmentType === 'global'
                                                    ? t('participantRequirement.global')
                                                    : req.participantName}
                                                ){req.qrCodeRequired && ' (QR)'}
                                                {req.note && ` [ ${req.note} ]`}
                                            </Typography>
                                        </Stack>
                                    ))}
                                </Stack>
                            </Stack>
                        )
                    }

                    return (
                        <Stack direction={'row'} spacing={1} alignItems={'center'}>
                            <Typography>
                                {row.participantRequirementsChecked?.length ?? 0}/
                                {deduplicatedRequirements.length}{' '}
                            </Typography>
                            <HtmlTooltip
                                placement={'right'}
                                title={
                                    <Stack spacing={1} p={1}>
                                        {deduplicatedRequirements.map(req => (
                                            <Stack direction={'row'} spacing={1} key={req.id}>
                                                {req.checked ? (
                                                    <CheckCircle color={'success'} />
                                                ) : (
                                                    <Cancel color={'error'} />
                                                )}
                                                <Typography>
                                                    {req.name} (
                                                    {req.assignmentType === 'global'
                                                        ? t('participantRequirement.global')
                                                        : req.participantName}
                                                    ){req.qrCodeRequired && ' (QR)'}
                                                    {req.note && ` [ ${req.note} ]`}
                                                </Typography>
                                            </Stack>
                                        ))}
                                    </Stack>
                                }>
                                <Info color={'info'} fontSize={'small'} />
                            </HtmlTooltip>
                        </Stack>
                    )
                },
            },
            {
                field: 'qrCodeId',
                headerName: t('qrCode.qrCode'),
                minWidth: 100,
                sortable: false,
                renderCell: ({row}) => {
                    if (!row.qrCodeId) {
                        return <>-</>
                    }

                    if (isMobile) {
                        return <Typography variant="body2">{row.qrCodeId}</Typography>
                    }

                    return (
                        <HtmlTooltip
                            title={
                                <Box sx={{p: 1}}>
                                    <Typography fontWeight={'bold'} gutterBottom>
                                        {t('qrCode.value')}:
                                    </Typography>
                                    <Typography>{row.qrCodeId}</Typography>
                                </Box>
                            }>
                            <QrCodeIcon />
                        </HtmlTooltip>
                    )
                },
            },
        ],
        [requirementsData?.data, namedParticipantsForEvent, t, isMobile],
    )

    const participantRequirementCheckForEventConfigProps =
        useEntityAdministration<ParticipantRequirementCheckForEventConfigDto>(
            t('participantRequirement.participantRequirement'),
            {entityCreate: false, entityUpdate: false},
        )

    const participantRequirementApproveManuallyForEventProps =
        useEntityAdministration<ParticipantRequirementApproveManuallyForEventForm>(
            t('participantRequirement.participantRequirement'),
            {entityUpdate: true},
        )

    const splitOptions: SplitButtonOption[] = useMemo(() => {
        const options: SplitButtonOption[] = []

        // Get all named participant requirement IDs
        const namedRequirementIds = new Set(
            namedParticipantsForEvent?.flatMap(
                np => np.requirements?.map(r => r.requirementId) || [],
            ) || [],
        )

        // Add global requirements (those not assigned to any named participant)
        requirementsData?.data
            .filter(r => !namedRequirementIds.has(r.id))
            .forEach(r => {
                options.push({
                    label: t('event.participantRequirement.checkManually', {name: r.name}),
                    onClick: () => {
                        participantRequirementApproveManuallyForEventProps.table.openDialog({
                            requirementId: r.id,
                            requirementName: r.name,
                            isGlobal: true,
                            approvedParticipants: [],
                        })
                    },
                })
            })

        // Add named participant requirements
        namedParticipantsForEvent?.forEach(np => {
            np.requirements?.forEach(req => {
                options.push({
                    label: t('event.participantRequirement.checkManually', {
                        name: `${req.requirementName} (${np.name})`,
                    }),
                    onClick: () => {
                        participantRequirementApproveManuallyForEventProps.table.openDialog({
                            requirementId: req.requirementId,
                            requirementName: req.requirementName,
                            isGlobal: false,
                            namedParticipantId: np.id,
                            namedParticipantName: np.name,
                            approvedParticipants: [],
                        })
                    },
                })
            })
        })

        return options
    }, [
        requirementsData?.data,
        namedParticipantsForEvent,
        participantRequirementApproveManuallyForEventProps.table,
        t,
    ])

    const handleEditQr = (participant: ParticipantForEventDto) => {
        setEditQrParticipant(participant)
        setEditDialogOpen(true)
    }
    const handleEditQrCancel = () => {
        setEditDialogOpen(false)
        setEditQrParticipant(null)
    }
    const handleEditQrOpen = () => {}
    const handleEditQrReload = () => {
        setEditDialogOpen(false)
        setEditQrParticipant(null)
        props.reloadData()
    }

    const handleDeleteQr = (participant: ParticipantForEventDto) => {
        confirmAction(
            async () => {
                await deleteQrCode({
                    path: {qrCodeId: participant.qrCodeId!},
                })
                props.reloadData()
            },
            {
                content: t('club.participant.qrCodeDeleteConfirm'),
                okText: t('common.delete'),
            },
        )
    }

    const handleResendAccessToken = (participant: ParticipantForEventDto) => {
        confirmAction(async () => {
            const {error} = await resendAccessToken({
                path: {eventId, participantId: participant.id},
                body: {
                    callbackUrl: location.origin + (basepath ? `/${basepath}` : '') + '/challenge/',
                },
            })

            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success(t('club.participant.resendAccessTokenSuccess'))
            }
            props.reloadData()
        })
    }

    const customEntityActions = (entity: ParticipantForEventDto) => {
        return [
            <GridActionsCellItem
                icon={<Edit />}
                label={t('club.participant.qrCodeEdit')}
                onClick={() => handleEditQr(entity)}
                showInMenu
            />,
            <GridActionsCellItem
                icon={<Delete />}
                label={t('club.participant.qrCodeDelete')}
                onClick={() => handleDeleteQr(entity)}
                showInMenu
            />,
            ...(eventData.challengeEvent && eventData.allowSelfSubmission && entity.email
                ? [
                      <GridActionsCellItem
                          icon={<Email />}
                          label={t('club.participant.resendAccessToken')}
                          onClick={() => handleResendAccessToken(entity)}
                          showInMenu
                      />,
                  ]
                : []),
        ]
    }

    return (
        <Fragment>
            <QrCodeEditDialog
                dialogIsOpen={editDialogOpen}
                closeDialog={handleEditQrCancel}
                reloadData={handleEditQrReload}
                entity={editQrParticipant}
                onOpen={handleEditQrOpen}
                eventId={eventId}
            />
            <ParticipantRequirementApproveManuallyForEventDialog
                {...participantRequirementApproveManuallyForEventProps.dialog}
                reloadData={props.reloadData}
            />
            <ParticipantRequirementCheckForEventUploadFileDialog
                open={participantRequirementCheckForEventConfigProps.dialog.dialogIsOpen}
                onClose={participantRequirementCheckForEventConfigProps.dialog.closeDialog}
                onSuccess={props.reloadData}
            />
            <EntityTable
                {...props}
                customTableActions={
                    user.checkPrivilege(updateEventGlobal) && (
                        <SplitButton
                            main={{
                                icon: <VerifiedUser />,
                                label: t('event.participantRequirement.checkUpload'),
                                onClick: () =>
                                    participantRequirementCheckForEventConfigProps.table.openDialog(
                                        undefined,
                                    ),
                            }}
                            options={splitOptions}
                        />
                    )
                }
                customEntityActions={customEntityActions}
                gridProps={{getRowId: row => row.id}}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('club.participant.title')}
                mobileBreakpoint={'lg'}
            />
        </Fragment>
    )
}

export default ParticipantForEventTable
