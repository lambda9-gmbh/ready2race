import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {
    activateParticipantRequirementForEvent,
    assignRequirementToNamedParticipant,
    getNamedParticipants,
    getParticipantRequirementsForEvent,
    removeParticipantRequirementForEvent,
    removeRequirementFromNamedParticipant,
    updateQrCodeRequirement,
} from '@api/sdk.gen.ts'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {BaseEntityTableProps, EntityTableAction} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {eventIndexRoute} from '@routes'
import {Cancel, Check, CheckCircle, Person} from '@mui/icons-material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {Box, Chip} from '@mui/material'
import {useMemo, useState} from 'react'
import AssignParticipantModal from './AssignParticipantModal.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | { value: number; label: string })[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const ParticipantRequirementForEventTable = (
    props: BaseEntityTableProps<ParticipantRequirementForEventDto>,
) => {
    const {t} = useTranslation() as { t: (key: string, options?: { [key: string]: string | number }) => string }
    const feedback = useFeedback()
    const {eventId} = eventIndexRoute.useParams()
    const {confirmAction} = useConfirmation()
    const [assignModalOpen, setAssignModalOpen] = useState(false)
    const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(null)

    // Fetch current data for modal
    const [modalDataTrigger, setModalDataTrigger] = useState(0)
    const {data: modalData} = useFetch(signal =>
        getParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {page: 0, pageSize: 1000}, // Get all data for modal
        }),
        { deps: [modalDataTrigger] }
    )

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const {data: namedParticipants} = useFetch(signal =>
        getNamedParticipants({
            signal,
            query: {},
        }),
    )


    const columns: GridColDef<ParticipantRequirementForEventDto>[] = useMemo(() => [
        {
            field: 'active',
            headerName: t('participantRequirement.active'),
            sortable: false,
            renderCell: ({value}) =>
                value ? <CheckCircle color={'success'}/> : <Cancel color={'error'}/>,
        },
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            flex: 2,
            sortable: false,
        },
        {
            field: 'optional',
            headerName: t('entity.optional'),
            sortable: false,
            renderCell: ({value}) => (value ? <Check/> : <></>),
        },
        {
            field: 'checkInApp',
            headerName: t('participantRequirement.checkInApp'),
            flex: 1,
            sortable: false,
            renderCell: ({value}) => (value ? <Check/> : <></>),
        },
        {
            field: 'namedParticipantAssignments',
            headerName: t('participantRequirement.namedParticipantAssignments'),
            flex: 1,
            sortable: false,
            renderCell: ({row}) => {
                if (!row.active) return null

                // Use the requirements data directly from the DTO
                const assignedRequirements = row.requirements || []

                // Check if there's a global assignment (where id is null)
                const hasGlobalAssignment = row.active && assignedRequirements.length == 0
                const specificAssignments = assignedRequirements.filter(req => req.id)

                if (hasGlobalAssignment && specificAssignments.length === 0) {
                    return (
                        <Chip
                            size="small"
                            label={t('participantRequirement.global')}
                            color="primary"
                        />
                    )
                }

                return (
                    <Box display="flex" flexWrap="wrap" gap={0.5}>
                        {hasGlobalAssignment && (
                            <Chip
                                size="small"
                                label={t('participantRequirement.global')}
                                color="primary"
                            />
                        )}
                        {specificAssignments.map(req => (
                            <Chip
                                key={req.id}
                                size="small"
                                label={req.name + (req.qrCodeRequired ? ' (QR)' : '')}
                                color="secondary"
                                variant="outlined"
                            />
                        ))}
                    </Box>
                )
            },
        },
    ], [t])

    const handleActivationChange = (entity: ParticipantRequirementForEventDto) => {
        if (entity.active) {
            // Check if it's a global assignment
            const assignedRequirements = entity.requirements || []
            const hasGlobalAssignment = assignedRequirements.some(req => !req.id)
            const specificAssignments = assignedRequirements.filter(req => req.id)

            // Use different confirmation message based on assignment type
            const confirmMessage = hasGlobalAssignment && specificAssignments.length === 0
                ? t('participantRequirement.confirmRemoveGlobal', {requirement: entity.name})
                : t('participantRequirement.confirmRemove', {requirement: entity.name})

            confirmAction(
                () =>
                    removeParticipantRequirementForEvent({
                        path: {eventId: eventId, participantRequirementId: entity.id},
                    }).then(({error}) => {
                        if (error) {
                            feedback.error(t('common.error.unexpected'))
                        } else {
                            props.reloadData()
                        }
                    }),
                {
                    content: confirmMessage,
                    okText: t('common.remove'),
                },
            )
        } else {
            activateParticipantRequirementForEvent({
                path: {eventId: eventId, participantRequirementId: entity.id},
            }).then(({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    props.reloadData()
                }
            })
        }
    }

    const handleAssignToNamedParticipant = async (
        requirementId: string,
        namedParticipantId: string,
        qrCodeRequired: boolean
    ) => {
        const {error} = await assignRequirementToNamedParticipant({
            path: {eventId, namedParticipantId},
            body: {requirementId, qrCodeRequired},
        })

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            props.reloadData()
            setModalDataTrigger(prev => prev + 1)
            feedback.success(t('participantRequirement.assignedToNamedParticipant'))
        }
    }

    const handleQrCodeToggle = async (
        requirementId: string,
        namedParticipantId: string,
        currentQrCodeRequired: boolean
    ) => {
        const {error} = await updateQrCodeRequirement({
            path: {eventId},
            body: {
                requirementId,
                namedParticipantId: namedParticipantId || undefined,
                qrCodeRequired: !currentQrCodeRequired,
            },
        })

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            props.reloadData()
            setModalDataTrigger(prev => prev + 1)
            feedback.success(t('participantRequirement.qrCodeRequirement') + ' ' + t('entity.edit.success', {entity: ''}))
        }
    }

    const handleRemoveFromNamedParticipant = async (
        requirementId: string,
        namedParticipantId: string,
        requirementName?: string
    ) => {
        if (namedParticipantId === '') {
            // Handle global requirement removal with confirmation
            confirmAction(
                async () => {
                    const {error} = await removeParticipantRequirementForEvent({
                        path: {eventId: eventId, participantRequirementId: requirementId},
                    })

                    if (error) {
                        feedback.error(t('common.error.unexpected'))
                    } else {
                        props.reloadData()
                        setModalDataTrigger(prev => prev + 1)
                        feedback.success(t('participantRequirement.removedGlobal'))
                    }
                },
                {
                    content: t('participantRequirement.confirmRemoveGlobal', {requirement: requirementName || ''}),
                    okText: t('common.remove'),
                }
            )
        } else {
            const {error} = await removeRequirementFromNamedParticipant({
                path: {eventId, namedParticipantId},
                body: {requirementId, qrCodeRequired: false},
            })

            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                props.reloadData()
                setModalDataTrigger(prev => prev + 1)
                feedback.success(t('participantRequirement.removedFromNamedParticipant'))
            }
        }
    }

    const customEntityActions = (
        entity: ParticipantRequirementForEventDto,
    ): EntityTableAction[] => {
        // Get already assigned named participants from the requirement's data
        const assignedNamedParticipants = entity.requirements || []

        // Separate global and specific assignments
        const hasGlobalAssignment = assignedNamedParticipants.length == 0 && entity.active
        const specificAssignments = assignedNamedParticipants.filter(req => req.id && req.id !== '')

        return [
            // Activation action - show when not assigned to anyone
            !hasGlobalAssignment && specificAssignments.length === 0 && (
                <GridActionsCellItem
                    icon={<CheckCircle/>}
                    label={t('participantRequirement.activate')}
                    onClick={() => handleActivationChange(entity)}
                    showInMenu
                />
            ),
            // Remove action - show when globally assigned
            hasGlobalAssignment && (
                <GridActionsCellItem
                    icon={<Cancel/>}
                    label={t('participantRequirement.remove')}
                    onClick={() => handleRemoveFromNamedParticipant(entity.id, '', entity.name)}
                    showInMenu
                />
            ),
            // Manage participants action - show when not globally assigned
            !hasGlobalAssignment && (
                <GridActionsCellItem
                    icon={<Person/>}
                    label={t('participantRequirement.manageParticipants')}
                    onClick={() => {
                        setSelectedRequirementId(entity.id)
                        setAssignModalOpen(true)
                    }}
                    showInMenu
                />
            )
        ].filter(Boolean) as EntityTableAction[]
    }

    return (
        <>
            <EntityTable
                {...props}
                customEntityActions={customEntityActions}
                parentResource={'EVENT'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('participantRequirement.participantRequirement')}
            />
            {selectedRequirementId && namedParticipants && modalData && (() => {
                const currentRequirement = modalData.data.find((req: ParticipantRequirementForEventDto) => req.id === selectedRequirementId)
                if (!currentRequirement) return null

                return (
                    <AssignParticipantModal
                        open={assignModalOpen}
                        onClose={() => {
                            setAssignModalOpen(false)
                            setSelectedRequirementId(null)
                        }}
                        onAssign={async (namedParticipantId, qrCodeRequired) => {
                            await handleAssignToNamedParticipant(
                                currentRequirement.id,
                                namedParticipantId,
                                qrCodeRequired
                            )
                            // Keep modal open - data will be updated automatically
                        }}
                        onToggleQrCode={async (namedParticipantId, currentQrCodeRequired) => {
                            await handleQrCodeToggle(
                                currentRequirement.id,
                                namedParticipantId,
                                currentQrCodeRequired
                            )
                            // Keep modal open - data will be updated automatically
                        }}
                        onRemove={async (namedParticipantId) => {
                            await handleRemoveFromNamedParticipant(
                                currentRequirement.id,
                                namedParticipantId,
                                currentRequirement.name
                            )
                            // Keep modal open - data will be updated automatically
                        }}
                        namedParticipants={namedParticipants.data}
                        requirementName={currentRequirement.name}
                        assignedRequirements={
                            currentRequirement.requirements?.filter((req: any) => req.id) || []
                        }
                    />
                )
            })()}
        </>
    )
}

export default ParticipantRequirementForEventTable
