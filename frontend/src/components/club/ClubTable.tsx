import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {ClubDto, deleteClub, getClubs} from '../../api'
import {Trans, useTranslation} from 'react-i18next'
import EntityTable from '../EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {format} from 'date-fns'
import {Upload} from '@mui/icons-material'
import {Button} from '@mui/material'
import {useState} from 'react'
import ClubImportDialog from '@components/club/ClubImportDialog.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getClubs({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: ClubDto) => {
    return deleteClub({path: {clubId: dto.id}})
}

const ClubTable = (props: BaseEntityTableProps<ClubDto>) => {
    const {t} = useTranslation()
    const [showImportDialog, setShowImportDialog] = useState(false)

    const columns: GridColDef<ClubDto>[] = [
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 150,
            flex: 2,
        },
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            flex: 1,
            sortable: true,
            valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
        },
    ]

    return (
        <>
            <EntityTable
                {...props}
                resource={'CLUB'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                linkColumn={entity => ({
                    to: '/club/$clubId',
                    params: {clubId: entity.id},
                })}
                entityName={t('club.club')}
                deleteRequest={deleteRequest}
                onDelete={() => {}}
                customTableActions={
                    <Button
                        variant={'outlined'}
                        startIcon={<Upload />}
                        onClick={() => setShowImportDialog(true)}>
                        <Trans i18nKey={'club.import'} />
                    </Button>
                }
            />
            <ClubImportDialog
                open={showImportDialog}
                onClose={() => setShowImportDialog(false)}
                reloadClubs={props.reloadData}
            />
        </>
    )
}

export default ClubTable
