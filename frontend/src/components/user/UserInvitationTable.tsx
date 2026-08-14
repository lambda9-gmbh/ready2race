import {BaseEntityTableProps, EntityAction} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteInvitation, getInvitations, resendInvitation} from '@api/sdk.gen.ts'
import {AppUserInvitationDto, Privilege} from '@api/types.gen.ts'
import {Delete, Email} from '@mui/icons-material'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useFeedback} from '@utils/hooks.ts'
import {createUserGlobal} from '@authorization/privileges.ts'
import {format} from 'date-fns'

// TODO: validate/sanitize basepath (also in routes.tsx)
const basepath = document.getElementById('ready2race-root')!.dataset.basepath

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'lastname', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getInvitations({
        signal,
        query: paginationParameters,
    })

const UserInvitationTable = (props: BaseEntityTableProps<AppUserInvitationDto>) => {
    const {t} = useTranslation()
    const {confirmAction} = useConfirmation()
    const feedback = useFeedback()

    const callbackUrl = location.origin + (basepath ? `/${basepath}` : '') + '/invitation/'

    const handleWithdraw = (invitation: AppUserInvitationDto) => {
        confirmAction(
            async () => {
                const {error} = await deleteInvitation({path: {invitationId: invitation.id}})

                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    feedback.success(t('user.invitation.withdrawSuccess'))
                }
                props.reloadData()
            },
            {
                content: t('user.invitation.withdrawConfirm', {email: invitation.email}),
                okText: t('user.invitation.withdraw'),
            },
        )
    }

    const handleResend = (invitation: AppUserInvitationDto) => {
        confirmAction(
            async () => {
                const {error} = await resendInvitation({
                    path: {invitationId: invitation.id},
                    body: {callbackUrl},
                })

                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    feedback.success(t('user.invitation.resendSuccess'))
                }
                props.reloadData()
            },
            {
                content: t('user.invitation.resendConfirm', {email: invitation.email}),
                okText: t('user.invitation.resend'),
            },
        )
    }

    const columns: GridColDef<AppUserInvitationDto>[] = [
        {
            field: 'firstname',
            headerName: t('user.firstname'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'lastname',
            headerName: t('user.lastname'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'email',
            headerName: t('user.email.email'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'expiresAt',
            headerName: t('user.invitation.expiresAt'),
            minWidth: 160,
            flex: 1,
            sortable: false,
            valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
        },
    ]

    // Beide Aktionen haengen am Einladen-Recht - es gibt kein Loeschrecht auf USER, der eingebaute
    // Papierkorb von EntityTable bliebe deshalb unsichtbar.
    const entityActions = (
        entity: AppUserInvitationDto,
        checkPrivilege: (privilege: Privilege) => boolean,
    ): EntityAction[] =>
        checkPrivilege(createUserGlobal)
            ? [
                  <GridActionsCellItem
                      icon={<Email />}
                      label={t('user.invitation.resend')}
                      onClick={() => handleResend(entity)}
                      showInMenu
                  />,
                  <GridActionsCellItem
                      icon={<Delete />}
                      label={t('user.invitation.withdraw')}
                      onClick={() => handleWithdraw(entity)}
                      showInMenu
                  />,
              ]
            : []

    return (
        <EntityTable
            {...props}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            customEntityActions={entityActions}
            resource={'USER'}
        />
    )
}

export default UserInvitationTable
