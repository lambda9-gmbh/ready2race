import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {eventIndexRoute} from '@routes'
import {
    AppUserWithQrCodeDto,
    deleteQrCodeForEvent,
    getAppUsersWithQrCodeForEvent,
} from '../../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../../EntityTable.tsx'
import {Delete, Edit} from '@mui/icons-material'
import {Fragment, useMemo, useState} from 'react'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {format} from 'date-fns'
import {AppUserQrCodeEditDialog} from './AppUserQrCodeEditDialog.tsx'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import QrCodeIcon from '@mui/icons-material/QrCode'
import {Box, Typography} from '@mui/material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'lastname', sort: 'asc'}]

const AppUserWithQrCodeTable = (props: BaseEntityTableProps<AppUserWithQrCodeDto>) => {
    const {t} = useTranslation()
    const {eventId} = eventIndexRoute.useParams()
    const {confirmAction} = useConfirmation()

    const [editDialogOpen, setEditDialogOpen] = useState(false)
    const [editQrUser, setEditQrUser] = useState<AppUserWithQrCodeDto | null>(null)

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getAppUsersWithQrCodeForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<AppUserWithQrCodeDto>[] = useMemo(
        () => [
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
                field: 'email',
                headerName: t('contact.email'),
                minWidth: 200,
                flex: 1,
            },
            {
                field: 'qrCodeId',
                headerName: t('qrCode.qrCode'),
                minWidth: 150,
                sortable: false,
                renderCell: ({row}) =>
                    row.qrCodeId ? (
                        <HtmlTooltip title={
                            <Box sx={{p: 1}}>
                            <Typography>{row.qrCodeId}</Typography>
                            </Box>
                        }>
                            <QrCodeIcon />
                        </HtmlTooltip>
                    ) : (
                        <>-</>
                    ),
            },
            {
                field: 'createdAt',
                headerName: t('entity.createdAt'),
                minWidth: 150,
                renderCell: ({row}) => format(new Date(row.createdAt), t('format.datetime')),
            },
        ],
        [t],
    )

    const handleEditQr = (appUser: AppUserWithQrCodeDto) => {
        setEditQrUser(appUser)
        setEditDialogOpen(true)
    }

    const handleEditQrCancel = () => {
        setEditDialogOpen(false)
        setEditQrUser(null)
    }

    const handleEditQrReload = () => {
        setEditDialogOpen(false)
        setEditQrUser(null)
        props.reloadData()
    }

    const handleDeleteQr = (appUser: AppUserWithQrCodeDto) => {
        confirmAction(
            async () => {
                await deleteQrCodeForEvent({
                    path: {eventId, qrCodeId: appUser.qrCodeId},
                })
                props.reloadData()
            },
            {
                content: t('qrCode.deleteConfirm'),
                okText: t('common.delete'),
            },
        )
    }

    const customEntityActions = (entity: AppUserWithQrCodeDto) => [
        <GridActionsCellItem
            icon={<Edit />}
            label={t('qrCode.edit')}
            onClick={() => handleEditQr(entity)}
            showInMenu
        />,
        <GridActionsCellItem
            icon={<Delete />}
            label={t('qrCode.delete')}
            onClick={() => handleDeleteQr(entity)}
            showInMenu
        />,
    ]

    return (
        <Fragment>
            <EntityTable
                {...props}
                customEntityActions={customEntityActions}
                gridProps={{getRowId: row => row.id}}
                parentResource={'USER'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('qrCode.appUsersWithQrCode')}
            />
            <AppUserQrCodeEditDialog
                dialogIsOpen={editDialogOpen}
                closeDialog={handleEditQrCancel}
                reloadData={handleEditQrReload}
                entity={editQrUser}
                onOpen={() => {}}
                eventId={eventId}
            />
        </Fragment>
    )
}

export default AppUserWithQrCodeTable
