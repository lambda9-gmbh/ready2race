import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getUsers} from "@api/sdk.gen.ts";
import {AppUserDto} from "@api/types.gen.ts";
import {Fragment, useState} from 'react'
import {QrCode2} from '@mui/icons-material'
import {QrCodeDisplayDialog} from "@components/user/QrCodeDisplayDialog.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'lastname', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getUsers({
        signal,
        query: paginationParameters,
    })

const UserTable = (props: BaseEntityTableProps<AppUserDto>) => {
    const {t} = useTranslation()
    const [qrDialogOpen, setQrDialogOpen] = useState(false)
    const [selectedUser, setSelectedUser] = useState<AppUserDto | null>(null)

    const columns: GridColDef<AppUserDto>[] = [
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
    ]

    const handleShowQr = (user: AppUserDto) => {
        setSelectedUser(user)
        setQrDialogOpen(true)
    }

    const handleCloseQr = () => {
        setQrDialogOpen(false)
        setSelectedUser(null)
    }

    const customEntityActions = (entity: AppUserDto) => {
        return [
            <GridActionsCellItem
                key="show-qr"
                icon={<QrCode2/>}
                label={t('qrCode.show')}
                onClick={() => handleShowQr(entity)}
                showInMenu
            />,
        ]
    }

    return (
        <Fragment>
            <QrCodeDisplayDialog
                dialogIsOpen={qrDialogOpen}
                closeDialog={handleCloseQr}
                entity={selectedUser}
            />
            <EntityTable
                {...props}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                resource={'USER'}
                linkColumn={entity => ({
                    to: '/user/$userId',
                    params: {userId: entity.id}
                })}
                customEntityActions={customEntityActions}
            />
        </Fragment>
    )
}

export default UserTable
