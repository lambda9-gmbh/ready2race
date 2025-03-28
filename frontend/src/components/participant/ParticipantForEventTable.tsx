import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {eventIndexRoute} from '@routes'
import {
    getActiveParticipantRequirementsForEvent,
    getParticipantsForEvent,
    ParticipantForEventDto,
    ParticipantRequirementCheckForEventConfigDto,
    ParticipantRequirementReducedDto,
} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {VerifiedUser} from '@mui/icons-material'
import SplitButton, {SplitButtonOption} from '@components/SplitButton.tsx'
import {Fragment, useMemo} from 'react'
import {useEntityAdministration, useFetch} from '@utils/hooks.ts'
import ParticipantRequirementApproveManuallyForEventDialog, {
    ParticipantRequirementApproveManuallyForEventForm,
} from '@components/event/participantRequirement/ParticipantRequirementApproveManuallyForEventDialog.tsx'
import ParticipantRequirementCheckForEventUploadFileDialog from '@components/event/participantRequirement/ParticipantRequirementCheckForEventUploadFileDialog.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const ParticipantForEventTable = (props: BaseEntityTableProps<ParticipantForEventDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<ParticipantForEventDto>[] = [
        {
            field: 'clubName',
            headerName: t('club.club'),
            flex: 1,
        },
        {
            field: 'firstname',
            headerName: t('entity.firstname'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'lastname',
            headerName: t('entity.lastname'),
            minWidth: 150,
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
            minWidth: 150,
            flex: 1,
            sortable: false,
            valueFormatter: (v: ParticipantRequirementReducedDto[]) =>
                v.map(r => r.name).join(', '),
        },
    ]

    const {data: requirementsData} = useFetch(signal =>
        getActiveParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}])},
        }),
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

    const splitOptions: SplitButtonOption[] = useMemo(
        () =>
            requirementsData?.data.map(r => ({
                label: t('event.participantRequirement.checkManually', {name: r.name}),
                onClick: () => {
                    participantRequirementApproveManuallyForEventProps.table.openDialog({
                        requirementId: r.id,
                        requirementName: r.name,
                        approvedParticipants: [],
                    })
                },
            })) ?? [],
        [requirementsData?.data],
    )

    return (
        <Fragment>
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
                }
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

export default ParticipantForEventTable
