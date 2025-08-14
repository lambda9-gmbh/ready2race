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
import {ReactNode, useMemo, useRef, useState} from 'react'
import {paginationParameters, PaginationParameters} from '@utils/ApiUtils.ts'
import {BaseEntityTableProps, EntityAction, PageResponse, PartialRequired} from '@utils/types.ts'
import {Link, LinkComponentProps} from '@tanstack/react-router'
import {RequestResult} from '@hey-api/client-fetch'
import {useTranslation} from 'react-i18next'
import {useDebounce, useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {Alert, Box, Button, Stack, TextField, Typography, useTheme} from '@mui/material'
import {Add, Delete, Edit, Input} from '@mui/icons-material'
import {useUser} from '@contexts/user/UserContext.ts'
import {ApiError, Privilege, Resource} from '@api/types.gen.ts'

type EntityTableProps<
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
> = BaseEntityTableProps<Entity> & ExtendedEntityTableProps<Entity, GetError, DeleteError>

export type ExtendedGridColDef<R extends GridValidRowModel = any, V = any, F = V> = {
    requiredPrivilege?: Privilege
} & GridColDef<R, V, F>

type ExtendedEntityTableProps<
    Entity extends GridValidRowModel,
    GetError extends ApiError,
    DeleteError extends ApiError,
> = {
    initialPagination: GridPaginationModel
    pageSizeOptions: (number | {value: number; label: string})[]
    initialSort: GridSortModel
    columns: ExtendedGridColDef<Entity>[]
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<Entity>, GetError, false>
    customTableActions?: ReactNode
    customEntityActions?: (
        entity: Entity,
        checkPrivilege: (privilege: Privilege) => boolean,
    ) => EntityAction[]
    linkColumn?: (entity: Entity) => PartialRequired<LinkComponentProps<'a'>, 'to' | 'params'>
    gridProps?: Partial<DataGridProps>
    withSearch?: boolean
    editableIf?: (entity: Entity) => boolean
} & (
    | {
          deleteRequest: (entity: Entity) => RequestResult<void, DeleteError, false>
          onDelete?: () => void
          onDeleteError?: (error: DeleteError) => void
          deletableIf?: (entity: Entity) => boolean
      }
    | {
          deleteRequest?: never
          onDelete?: never
          onDeleteError?: never
          deletableIf?: never
      }
) &
    (
        | {
              resource: Resource
              parentResource?: never
              publicRead?: boolean
          }
        | {
              resource?: never
              parentResource: Resource
              publicRead?: boolean
          }
    )

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
    publicRead = false,
    ...props
}: EntityTableProps<Entity, GetError, DeleteError>) => {
    const user = useUser()

    let crud: Crud | undefined

    if (user.loggedIn) {
        if (resource) {
            crud = {
                create: user.checkPrivilege({action: 'CREATE', resource, scope: 'OWN'}),
                read: user.checkPrivilege({action: 'READ', resource, scope: 'OWN'}) || publicRead,
                update: user.checkPrivilege({action: 'UPDATE', resource, scope: 'OWN'}),
                delete: user.checkPrivilege({action: 'DELETE', resource, scope: 'OWN'}),
            }
        } else {
            const rest = user.checkPrivilege({
                action: 'UPDATE',
                resource: parentResource,
                scope: 'OWN',
            })
            crud = {
                create: rest,
                read:
                    user.checkPrivilege({
                        action: 'READ',
                        resource: parentResource,
                        scope: 'OWN',
                    }) || publicRead,
                update: rest,
                delete: rest,
            }
        }
    } else {
        crud = {
            create: false,
            read: publicRead,
            update: false,
            delete: false,
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
    id,
    hints,
    lastRequested,
    reloadData,
    openDialog,
    options,
    initialPagination,
    pageSizeOptions,
    initialSort,
    columns,
    dataRequest,
    customTableActions,
    customEntityActions = () => [],
    linkColumn,
    gridProps,
    withSearch = true,
    crud,
    deleteRequest,
    onDelete,
    onDeleteError,
    deletableIf,
    editableIf,
}: EntityTableInternalProps<Entity, GetError, DeleteError>) => {
    const user = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()
    const theme = useTheme()

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
        ...columns.filter(c => !c.requiredPrivilege || user.checkPrivilege(c.requiredPrivilege)),
        {
            field: 'actions',
            type: 'actions' as 'actions',
            getActions: (params: GridRowParams<Entity>) => [
                ...customEntityActions(params.row, user.checkPrivilege).filter(action => !!action),
                ...(crud.update && options.entityUpdate && (editableIf?.(params.row) ?? true)
                    ? [
                          <GridActionsCellItem
                              icon={<Edit />}
                              label={t('common.edit')}
                              onClick={() => openDialog({...params.row})}
                              showInMenu={true}
                          />,
                      ]
                    : []),
                ...(deleteRequest && crud.delete && options.entityDelete && (deletableIf?.(params.row) ?? true)
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
                                          onDeleteError
                                              ? onDeleteError(error)
                                              : feedback.error(
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
            deps: [paginationModel, sortModel, debouncedSearchInput, lastRequested],
        },
    )

    // TODO: @Later - there seems to be a bug in DataGrid. After using search to find no entries, the next search that find entries, shows none of them (not even the message "no entries")
    // TODO: this hack fixes the problem at the cost of more rerenders
    //
    // TODO: not sure, if this also fixes the similar bug of showing nothing in the grid on reloading in general (like by changing page or editing/deleting items)
    useDebounce(data, 0)

    const rowCountRef = useRef(data?.pagination?.total ?? 0)

    const rowCount = useMemo(() => {
        const total = data?.pagination?.total
        if (total !== undefined) {
            rowCountRef.current = total
        }
        return rowCountRef.current
    }, [data?.pagination?.total])

    return (
        <Box id={id}>
            {title && <Typography variant={'h2'}>{title}</Typography>}
            {hints &&
                hints.map((hint, index) => (
                    <Box key={index} sx={{color: theme.palette.text.secondary}}>
                        {hint}
                    </Box>
                ))}
            {!error ? (
                <>
                    <Box display={'flex'} justifyContent={'space-between'} mb={1} pt={1}>
                        {withSearch ? (
                            <TextField
                                size={'small'}
                                variant={'outlined'}
                                label={t('common.search')}
                                value={searchInput}
                                onChange={e => {
                                    setSearchInput(e.target.value)
                                }}
                            />
                        ) : (
                            <Box />
                        )}
                        <Stack direction={'row'} spacing={1}>
                            {customTableActions}
                            {crud.create && options.entityCreate && (
                                <Button
                                    variant={'outlined'}
                                    startIcon={<Add />}
                                    onClick={() => openDialog()}>
                                    {t('entity.add.action', {entity: entityName})}
                                </Button>
                            )}
                        </Stack>
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
                            rowCount={rowCount}
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
                                '& .MuiDataGrid-columnHeader': {
                                    backgroundColor: t => t.palette.primary.light,
                                },
                            }}
                            {...gridProps}
                        />
                    </Box>
                </>
            ) : (
                <Alert severity="error">
                    {t('common.load.error.multiple.detailed', {
                        entity: title ?? entityName,
                    })}
                </Alert>
            )}
        </Box>
    )
}

export default EntityTable
