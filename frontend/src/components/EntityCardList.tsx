import {Box, CircularProgress, Stack, TablePagination, Typography} from '@mui/material'
import {GridColDef, GridPaginationModel, GridValidRowModel, GridActionsCellItemProps} from '@mui/x-data-grid'
import EntityCard from './EntityCard'
import {ReactElement} from 'react'
import {EntityAction} from '@utils/types.ts'

type EntityCardListProps<Entity extends GridValidRowModel> = {
    data: Entity[]
    columns: GridColDef<Entity>[]
    getActions: (entity: Entity) => EntityAction[]
    onRowClick?: (entity: Entity) => void
    loading?: boolean
    rowCount: number
    paginationModel: GridPaginationModel
    onPaginationModelChange: (model: GridPaginationModel) => void
    pageSizeOptions: (number | {value: number; label: string})[]
    emptyMessage?: string
}

const EntityCardList = <Entity extends GridValidRowModel>({
    data,
    columns,
    getActions,
    onRowClick,
    loading = false,
    rowCount,
    paginationModel,
    onPaginationModelChange,
    pageSizeOptions,
    emptyMessage = 'No entries found',
}: EntityCardListProps<Entity>) => {
    const handlePageChange = (_: unknown, newPage: number) => {
        onPaginationModelChange({
            ...paginationModel,
            page: newPage,
        })
    }

    const handlePageSizeChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        onPaginationModelChange({
            page: 0,
            pageSize: parseInt(event.target.value, 10),
        })
    }

    if (loading) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
                <CircularProgress />
            </Box>
        )
    }

    if (!data || data.length === 0) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
                <Typography color="text.secondary">{emptyMessage}</Typography>
            </Box>
        )
    }

    return (
        <Box>
            <Stack spacing={2}>
                {data.map(entity => {
                    const actions = getActions(entity).filter(
                        (action): action is ReactElement<GridActionsCellItemProps> => !!action,
                    )
                    return (
                        <EntityCard
                            key={entity.id}
                            entity={entity}
                            columns={columns}
                            actions={actions}
                            onRowClick={onRowClick ? () => onRowClick(entity) : undefined}
                        />
                    )
                })}
            </Stack>
            <TablePagination
                component="div"
                count={rowCount}
                page={paginationModel.page}
                onPageChange={handlePageChange}
                rowsPerPage={paginationModel.pageSize}
                onRowsPerPageChange={handlePageSizeChange}
                rowsPerPageOptions={pageSizeOptions.map(option =>
                    typeof option === 'number' ? option : option.value,
                )}
                sx={{mt: 2}}
            />
        </Box>
    )
}

export default EntityCardList
