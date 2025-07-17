import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {eventIndexRoute} from '@routes'
import {
    getActiveParticipantRequirementsForEvent,
    getParticipantsForEvent,
    getNamedParticipantsForEvent,
    ParticipantForEventDto,
    ParticipantRequirementCheckForEventConfigDto,
} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {Cancel, CheckCircle, Delete, Edit, Info, VerifiedUser} from '@mui/icons-material'
import SplitButton, {SplitButtonOption} from '@components/SplitButton.tsx'
import {Fragment, useMemo, useState} from 'react'
import {useEntityAdministration, useFetch} from '@utils/hooks.ts'
import ParticipantRequirementApproveManuallyForEventDialog, {
    ParticipantRequirementApproveManuallyForEventForm,
} from '@components/event/participantRequirement/ParticipantRequirementApproveManuallyForEventDialog.tsx'
import ParticipantRequirementCheckForEventUploadFileDialog
    from '@components/event/participantRequirement/ParticipantRequirementCheckForEventUploadFileDialog.tsx'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {Stack, Typography} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {deleteQrCode} from '../../api/sdk.gen'
import {QrCodeEditDialog} from "@components/participant/QrCodeEditDialog.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | { value: number; label: string })[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const ParticipantForEventTable = (props: BaseEntityTableProps<ParticipantForEventDto>) => {
    const {t} = useTranslation()
    const user = useUser()
    const {eventId} = eventIndexRoute.useParams()
    const {confirmAction} = useConfirmation()

    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [editQrParticipant, setEditQrParticipant] = useState<ParticipantForEventDto | null>(null)

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
            {
                field: 'externalClubName',
                headerName: t('club.participant.externalClub'),
                minWidth: 150,
                flex: 1,
            },
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
                    const rowNamedParticipants = namedParticipantsForEvent?.filter(np => 
                        row.namedParticipantIds?.includes(np.id)
                    ) || []
                    
                    // Get requirements specific to this participant's named participants
                    const namedParticipantRequirements = rowNamedParticipants.flatMap(np => 
                        (np.requirements || []).map(req => ({...req, participantName: np.name}))
                    )
                    
                    // Get all named participant requirement IDs across all named participants
                    const allNamedRequirementIds = new Set(
                        namedParticipantsForEvent?.flatMap(np => 
                            np.requirements?.map(r => r.requirementId) || []
                        ) || []
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
                                qrCodeRequired: false
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
                            qrCodeRequired: req.qrCodeRequired
                        })
                    })
                    
                    const deduplicatedRequirements = Array.from(requirementMap.values())
                    
                    if (deduplicatedRequirements.length === 0) {
                        return ' - '
                    }
                    
                    return (
                        <Stack direction={'row'} spacing={1} alignItems={'center'}>
                            <Typography>
                                {row.participantRequirementsChecked?.length}/
                                {deduplicatedRequirements.length}{' '}
                            </Typography>
                            <HtmlTooltip
                                placement={'right'}
                                title={
                                    <Stack spacing={1} p={1}>
                                        {deduplicatedRequirements.map(req => (
                                            <Stack direction={'row'} spacing={1} key={req.id}>
                                                {row.participantRequirementsChecked?.some(
                                                    c => c.id === req.id,
                                                ) ? (
                                                    <CheckCircle color={'success'}/>
                                                ) : (
                                                    <Cancel color={'error'}/>
                                                )}
                                                <Typography>
                                                    {req.name} 
                                                    ({req.assignmentType === 'global' ? 'Global' : req.participantName})
                                                    {req.qrCodeRequired && ' (QR)'}
                                                </Typography>
                                            </Stack>
                                        ))}
                                    </Stack>
                                }>
                                <Info color={'info'} fontSize={'small'}/>
                            </HtmlTooltip>
                        </Stack>
                    )
                }
            },
            {
                field: 'qrCodeId',
                headerName: t('club.participant.qrCodeId'),
                minWidth: 150,
                sortable: false,
                flex: 1,
            },
        ],
        [requirementsData?.data, namedParticipantsForEvent, t],
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
            namedParticipantsForEvent?.flatMap(np => 
                np.requirements?.map(r => r.requirementId) || []
            ) || []
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
                        name: `${req.requirementName} (${np.name})`
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
    }, [requirementsData?.data, namedParticipantsForEvent, participantRequirementApproveManuallyForEventProps.table, t])

    const handleEditQr = (participant: ParticipantForEventDto) => {
        setEditQrParticipant(participant)
        setEditDialogOpen(true)
    }
    const handleEditQrCancel = () => {
        setEditDialogOpen(false)
        setEditQrParticipant(null)
    }
    const handleEditQrOpen = () => {
    }
    const handleEditQrReload = () => {
        setEditDialogOpen(false)
        setEditQrParticipant(null)
        props.reloadData()
    }

    const handleDeleteQr = (participant: ParticipantForEventDto) => {
        confirmAction(async () => {
            await deleteQrCode({
                path: {qrCodeId: participant.qrCodeId!},
            })
            props.reloadData()
        }, {
            content: t('club.participant.qrCodeDeleteConfirm'),
            okText: t('common.delete'),
        })
    }

    const customEntityActions = (entity: ParticipantForEventDto) => {
        const actions = [
            <GridActionsCellItem
                icon={<Edit/>}
                label={t('club.participant.qrCodeEdit')}
                onClick={() => handleEditQr(entity)}
                showInMenu
            />,
            <GridActionsCellItem
                icon={<Delete/>}
                label={t('club.participant.qrCodeDelete')}
                onClick={() => handleDeleteQr(entity)}
                showInMenu
            />,
        ]

        return actions
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
                {...participantRequirementCheckForEventConfigProps.dialog}
                reloadData={props.reloadData}
            />
            <EntityTable
                {...props}
                customTableActions={
                    user.checkPrivilege(updateEventGlobal) && (
                        <SplitButton
                            main={{
                                icon: <VerifiedUser/>,
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
            />
        </Fragment>
    )
}

export default ParticipantForEventTable
