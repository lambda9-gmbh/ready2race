import {Pagination, Privilege, Scope} from '../api'
import {
    DataGrid,
    DataGridProps,
    GridActionsCellItem,
    GridActionsCellItemProps,
    GridColDef,
    GridPaginationModel,
    GridRenderCellParams,
    GridRowParams,
    GridSortModel,
    GridValidRowModel,
} from '@mui/x-data-grid'
import {ReactElement, useState} from 'react'
import {paginationParameters, PaginationParameters} from '../utils/ApiUtils.ts'
import {BaseEntityTableProps, PartialRequired} from '../utils/types.ts'
import {Link, LinkComponentProps} from '@tanstack/react-router'
import {RequestResult} from '@hey-api/client-fetch'
import {useTranslation} from 'react-i18next'
import {useDebounce, useFeedback, useFetch} from '../utils/hooks.ts'
import {useConfirmation} from '../contexts/confirmation/ConfirmationContext.ts'
import {Box, Button, TextField, Typography} from '@mui/material'
import {Add, Delete, Edit, Input} from '@mui/icons-material'

type EntityTableProps<Entity extends GridValidRowModel, Error> = BaseEntityTableProps<Entity> &
    ExtendedEntityTableProps<Entity, Error> &
    (
        | {
              deleteRequest: (entity: Entity) => RequestResult<void, string, false> // todo: specific error type
              onDelete: () => void
              deletableWhen?: (entity: Entity) => boolean
          }
        | {}
    )

type ExtendedEntityTableProps<Entity extends GridValidRowModel, Error> = {
    initialPagination: GridPaginationModel
    pageSizeOptions: (number | {value: number; label: string})[]
    initialSort: GridSortModel
    columns:
        | GridColDef<Entity>[]
        | ((readScope: Scope, changeScope: Scope | null) => GridColDef<Entity>[])
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<Entity>, Error, false>
    omitEditAction?: boolean
    omitAddAction?: boolean
    customActions?: (
        entity: Entity,
        changeScope: Scope | null,
    ) => ReactElement<GridActionsCellItemProps>[]
    jumpToColumn?: (entity: Entity) => PartialRequired<LinkComponentProps<'a'>, 'to' | 'params'>
    entityName?: string
    gridProps?: Partial<DataGridProps>
    readPermission?: Privilege
    changePermission?: Privilege
    hideSearch?: boolean
}

type PageResponse<E> = {
    data: E[]
    pagination: Pagination
}

const EntityTable = <Entity extends GridValidRowModel, Error>({
    jumpToColumn,
    entityName,
    ...props
}: EntityTableProps<Entity, Error>) => {
    //todo: Change PrivilegeScope
    const readPermitted: Scope = 'GLOBAL'
    const changePermitted: Scope = 'GLOBAL'

    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()

    const [isDeletingRow, setIsDeletingRow] = useState(false)

    const entityTitle = entityName ?? t('entity.entity')

    const columns: GridColDef<Entity>[] = [
        ...(jumpToColumn
            ? [
                  {
                      field: 'jumpTo',
                      headerName: '',
                      sortable: false,
                      width: 80,
                      renderCell: (params: GridRenderCellParams<Entity>) => {
                          const {...rest} = jumpToColumn(params.row)
                          return (
                              <Box display={'flex'} justifyContent={'center'} width={1}>
                                  <Link
                                      {...rest}
                                      onClick={e => {
                                          rest.onClick?.(e)
                                      }}>
                                      <Box display={'flex'} alignItems={'center'}>
                                          <Input />
                                      </Box>
                                  </Link>
                              </Box>
                          )
                      },
                  },
              ]
            : []),
        ...(typeof props.columns === 'function'
            ? props.columns(readPermitted, changePermitted)
            : props.columns),
        {
            field: 'actions',
            type: 'actions' as 'actions',
            getActions: (params: GridRowParams<Entity>) => [
                ...(props.customActions?.(params.row, changePermitted) ?? []),
                ...(!props.omitEditAction && changePermitted
                    ? [
                          <GridActionsCellItem
                              icon={<Edit />}
                              label={t('common.edit')}
                              onClick={() => props.openDialog(params.row)}
                              showInMenu={true}
                          />,
                      ]
                    : []),
                ...('deleteRequest' in props &&
                (props.deletableWhen?.(params.row) ?? true) &&
                changePermitted
                    ? [
                          <GridActionsCellItem
                              icon={<Delete />}
                              label={t('common.delete')}
                              onClick={() => {
                                  confirmAction(async () => {
                                      setIsDeletingRow(true)
                                      const {error} = await props.deleteRequest(params.row)
                                      setIsDeletingRow(false)
                                      if (error) {
                                          // todo better error display with specific error types
                                          console.log(error)
                                          feedback.error(
                                              t('entity.delete.error', {entity: entityTitle}),
                                          )
                                      } else {
                                          props.reloadData()
                                          props.onDelete()
                                          feedback.success(
                                              t('entity.delete.success', {entity: entityTitle}),
                                          )
                                      }
                                  })
                              }}
                              showInMenu={true}
                          />,
                      ]
                    : []),
            ],
        },
    ]

    const [paginationModel, setPaginationModel] = useState<GridPaginationModel>(
        props.initialPagination,
    )
    const [sortModel, setSortModel] = useState<GridSortModel>(props.initialSort)

    const [searchInput, setSearchInput] = useState<string>('')
    const debouncedSearchInput = useDebounce(searchInput, 700)

    const [triggerReloadRows, setTriggerReloadRows] = useState(false)

    const {data, pending} = useFetch(
        signal =>
            props.dataRequest(
                signal,
                paginationParameters(paginationModel, sortModel, debouncedSearchInput),
            ),
        {
            onResponse: _ => setTriggerReloadRows(!triggerReloadRows),
        },
        [paginationModel, sortModel, debouncedSearchInput],
    )

    return (
        <>
            {props.title && <Typography variant={'h6'}>{props.title}</Typography>}
            <Box display={'flex'} justifyContent={'space-between'} mb={1} pt={1}>
                {!props.hideSearch && (
                    <TextField
                        size={'small'}
                        variant={'outlined'}
                        label={t('common.search')}
                        value={searchInput}
                        onChange={e => {
                            setSearchInput(e.target.value)
                        }}
                    />
                )}
                {changePermitted && !props.omitAddAction && (
                    <Button
                        variant={'outlined'}
                        startIcon={<Add />}
                        onClick={() => props.openDialog()}>
                        {t('entity.add.action', {entity: entityTitle ?? t('entity.entity')})}
                    </Button>
                )}
            </Box>
            {triggerReloadRows !== undefined && ( // todo: better solution? This fixes a bug where sometimes after deleting a search entry the rows would not be displayed
                <Box sx={{display: 'flex', flexDirection: 'column'}}>
                    <DataGrid
                        isRowSelectable={() => false}
                        paginationMode="server"
                        pageSizeOptions={[
                            ...props.pageSizeOptions,
                            ...(props.pageSizeOptions.includes(props.initialPagination.pageSize)
                                ? []
                                : [props.initialPagination.pageSize]),
                        ]}
                        disableColumnFilter={true}
                        paginationModel={paginationModel}
                        onPaginationModelChange={setPaginationModel}
                        sortingMode="server"
                        sortModel={sortModel}
                        onSortModelChange={setSortModel}
                        columns={columns}
                        rows={data?.data ?? []}
                        rowCount={data?.pagination?.total ?? 0}
                        loading={pending || isDeletingRow}
                        density={'compact'}
                        getRowHeight={() => 'auto'}
                        sx={{
                            '&.MuiDataGrid-root--densityCompact .MuiDataGrid-cell': {py: '8px'},
                            '&.MuiDataGrid-root--densityStandard .MuiDataGrid-cell': {py: '15px'},
                            '&.MuiDataGrid-root--densityComfortable .MuiDataGrid-cell': {
                                py: '22px',
                            },
                        }}
                        {...props.gridProps}
                    />
                </Box>
            )}
        </>
    )
}

export default EntityTable
