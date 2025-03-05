import {
    DataGrid,
    DataGridProps,
    GridActionsCellItem,
    GridColDef,
    GridPaginationModel,
    GridRenderCellParams,
    GridRowParams,
    GridSortModel,
    GridValidRowModel,
} from '@mui/x-data-grid'
import {useState} from 'react'
import {paginationParameters, PaginationParameters} from '@utils/ApiUtils.ts'
import {BaseEntityTableProps, EntityTableAction, PartialRequired} from '@utils/types.ts'
import {Link, LinkComponentProps} from '@tanstack/react-router'
import {RequestResult} from '@hey-api/client-fetch'
import {useTranslation} from 'react-i18next'
import {useDebounce, useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {Alert, Box, Button, TextField, Typography} from '@mui/material'
import {Add, Delete, Edit, Input} from '@mui/icons-material'
import {useUser} from '@contexts/user/UserContext.ts'
import {ApiError, Pagination, Resource} from '@api/types.gen.ts'

type EntityTableProps<
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
> = BaseEntityTableProps<Entity> & ExtendedEntityTableProps<Entity, GetError, DeleteError>

type ExtendedEntityTableProps<
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
> = {
    initialPagination: GridPaginationModel
    pageSizeOptions: (number | {value: number; label: string})[]
    initialSort: GridSortModel
    columns: GridColDef<Entity>[]
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<Entity>, GetError, false>
    customActions?: (entity: Entity) => EntityTableAction[]
    linkColumn?: (entity: Entity) => PartialRequired<LinkComponentProps<'a'>, 'to' | 'params'>
    gridProps?: Partial<DataGridProps>
    withSearch?: boolean
} & (
    | {
          deleteRequest: (entity: Entity) => RequestResult<void, DeleteError, false>
          onDelete?: () => void
          deletableIf?: (entity: Entity) => boolean
      }
    | {
          deleteRequest?: never
          onDelete?: never
          deletableIf?: never
      }
) &
    (
        | {
              resource: Resource
              parentResource?: never
          }
        | {
              resource?: never
              parentResource: Resource
          }
    )

type PageResponse<E> = {
    data: E[]
    pagination: Pagination
}

type Crud = {
    create: boolean
    read: boolean
    update: boolean
    delete: boolean
}

// todo: @fix: sometimes on refreshing content, datagrid is simple empty

const EntityTable = <
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
>({
    resource,
    parentResource,
    ...props
}: EntityTableProps<Entity, GetError, DeleteError>) => {
    const user = useUser()

    let crud: Crud | undefined

    if (user.loggedIn) {
        if (resource) {
            crud = {
                create: user.checkPrivilege({action: 'CREATE', resource, scope: 'GLOBAL'}),
                read: user.checkPrivilege({action: 'READ', resource, scope: 'GLOBAL'}),
                update: user.checkPrivilege({action: 'UPDATE', resource, scope: 'GLOBAL'}),
                delete: user.checkPrivilege({action: 'DELETE', resource, scope: 'GLOBAL'}),
            }
        } else {
            const rest = user.checkPrivilege({
                action: 'UPDATE',
                resource: parentResource,
                scope: 'GLOBAL',
            })
            crud = {
                create: rest,
                read: user.checkPrivilege({
                    action: 'READ',
                    resource: parentResource,
                    scope: 'GLOBAL',
                }),
                update: rest,
                delete: rest,
            }
        }
    }

    return crud?.read && <EntityTableInternal {...props} crud={crud} />
}

type EntityTableInternalProps<
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
> = Omit<EntityTableProps<Entity, GetError, DeleteError>, 'resource' | 'privilege'> & {crud: Crud}

const EntityTableInternal = <
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
>({
    entityName,
    title,
    lastRequested,
    reloadData,
    openDialog,
    options,
    initialPagination,
    pageSizeOptions,
    initialSort,
    columns,
    dataRequest,
    customActions = () => [],
    linkColumn,
    gridProps,
    withSearch = true,
    crud,
    deleteRequest,
    onDelete,
    deletableIf,
}: EntityTableInternalProps<Entity, GetError, DeleteError>) => {
    const user = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()

    const [isDeletingRow, setIsDeletingRow] = useState(false)

    const cols: GridColDef<Entity>[] = [
        ...(linkColumn
            ? [
                  {
                      field: 'jumpTo',
                      headerName: '',
                      sortable: false,
                      width: 80,
                      renderCell: (params: GridRenderCellParams<Entity>) => (
                          <Box display={'flex'} justifyContent={'center'} width={1}>
                              <Link {...linkColumn(params.row)}>
                                  <Box display={'flex'} alignItems={'center'}>
                                      <Input />
                                  </Box>
                              </Link>
                          </Box>
                      ),
                  },
              ]
            : []),
        ...columns,
        {
            field: 'actions',
            type: 'actions' as 'actions',
            getActions: (params: GridRowParams<Entity>) => [
                ...customActions(params.row)
                    .map(action => {
                        const {privilege} = action.props
                        delete action.props.privilege
                        return !privilege || (user.loggedIn && user.checkPrivilege(privilege))
                            ? action
                            : null
                    })
                    .filter(action => action !== null),
                ...(crud.update && options.entityUpdate
                    ? [
                          <GridActionsCellItem
                              icon={<Edit />}
                              label={t('common.edit')}
                              onClick={() => openDialog({...params.row})}
                              showInMenu={true}
                          />,
                      ]
                    : []),
                ...(deleteRequest && crud.delete && (deletableIf?.(params.row) ?? true)
                    ? [
                          <GridActionsCellItem
                              icon={<Delete />}
                              label={t('common.delete')}
                              onClick={() => {
                                  confirmAction(async () => {
                                      setIsDeletingRow(true)
                                      const {error} = await deleteRequest(params.row)
                                      setIsDeletingRow(false)
                                      if (error) {
                                          console.error(error)
                                          feedback.error(
                                              t('entity.delete.error', {entity: entityName}),
                                          )
                                      } else {
                                          onDelete?.()
                                          feedback.success(
                                              t('entity.delete.success', {entity: entityName}),
                                          )
                                      }
                                      reloadData()
                                  })
                              }}
                              showInMenu={true}
                          />,
                      ]
                    : []),
            ],
        },
    ]

    const [paginationModel, setPaginationModel] = useState<GridPaginationModel>(initialPagination)
    const [sortModel, setSortModel] = useState<GridSortModel>(initialSort)

    const [searchInput, setSearchInput] = useState<string>('')
    const debouncedSearchInput = useDebounce(searchInput, 700)

    const {data, error, pending} = useFetch(
        signal =>
            dataRequest(
                signal,
                paginationParameters(paginationModel, sortModel, debouncedSearchInput),
            ),
        {
            onResponse: ({error}) => {
                if (error) {
                    console.error(error)
                }
            },
            deps: [paginationModel, sortModel, debouncedSearchInput, lastRequested],
        },
    )

    return (
        <Box>
            {title && <Typography variant={'h2'}>{title}</Typography>}
            {!error ? (
                <>
                    <Box display={'flex'} justifyContent={'space-between'} mb={1} pt={1}>
                        {withSearch && (
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
                        {crud.create && options.entityCreate && (
                            <Button
                                variant={'outlined'}
                                startIcon={<Add />}
                                onClick={() => openDialog()}>
                                {t('entity.add.action', {entity: entityName})}
                            </Button>
                        )}
                    </Box>
                    <Box sx={{display: 'flex', flexDirection: 'column'}}>
                        <DataGrid
                            isRowSelectable={() => false}
                            paginationMode="server"
                            pageSizeOptions={[
                                ...pageSizeOptions,
                                ...(pageSizeOptions.includes(initialPagination.pageSize)
                                    ? []
                                    : [initialPagination.pageSize]),
                            ]}
                            disableColumnFilter={true}
                            paginationModel={paginationModel}
                            onPaginationModelChange={setPaginationModel}
                            sortingMode="server"
                            sortModel={sortModel}
                            onSortModelChange={setSortModel}
                            columns={cols}
                            rows={data?.data ?? []}
                            rowCount={data?.pagination?.total ?? 0}
                            loading={pending || isDeletingRow}
                            density={'compact'}
                            getRowHeight={() => 'auto'}
                            sx={{
                                '&.MuiDataGrid-root--densityCompact .MuiDataGrid-cell': {py: '8px'},
                                '&.MuiDataGrid-root--densityStandard .MuiDataGrid-cell': {
                                    py: '15px',
                                },
                                '&.MuiDataGrid-root--densityComfortable .MuiDataGrid-cell': {
                                    py: '22px',
                                },
                            }}
                            {...gridProps}
                        />
                    </Box>
                </>
            ) : (
                <Alert severity="error">
                    {t('common.load.error.multiple.detailed', {
                        entity: title ?? entityName, // todo: Other entity name than "title". "entityName" is singular, this message needs plural
                    })}
                </Alert>
            )}
        </Box>
    )
}

export default EntityTable
