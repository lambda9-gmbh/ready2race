import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {
    activateParticipantRequirementForEvent,
    getParticipantRequirementsForEvent,
    removeParticipantRequirementForEvent,
} from '@api/sdk.gen.ts'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {BaseEntityTableProps, EntityTableAction} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {eventIndexRoute} from '@routes'
import {Cancel, Check, CheckCircle} from '@mui/icons-material'
import {useFeedback} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const ParticipantRequirementForEventTable = (
    props: BaseEntityTableProps<ParticipantRequirementForEventDto>,
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {eventId} = eventIndexRoute.useParams()
    const {confirmAction} = useConfirmation()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<ParticipantRequirementForEventDto>[] = [
        {
            field: 'active',
            headerName: t('participantRequirement.active'),
            sortable: false,
            renderCell: ({value}) =>
                value ? <CheckCircle color={'success'} /> : <Cancel color={'error'} />,
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
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
    ]

    const handleActivationChange = (entity: ParticipantRequirementForEventDto) => {
        if (entity.active) {
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
                    content: t('participantRequirement.confirmRemove', {requirement: entity.name}),
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

    const customEntityActions = (
        entity: ParticipantRequirementForEventDto,
    ): EntityTableAction[] => [
        entity.active ? (
            <GridActionsCellItem
                icon={<Cancel />}
                label={t('participantRequirement.remove')}
                onClick={() => handleActivationChange(entity)}
                showInMenu
            />
        ) : (
            <GridActionsCellItem
                icon={<CheckCircle />}
                label={t('participantRequirement.activate')}
                onClick={() => handleActivationChange(entity)}
                showInMenu
            />
        ),
    ]

    return (
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
    )
}

export default ParticipantRequirementForEventTable
