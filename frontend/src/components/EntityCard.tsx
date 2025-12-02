import {Box, Card, CardContent, IconButton, Menu, MenuItem, Stack, Typography} from '@mui/material'
import {
    GridColDef,
    GridValidRowModel,
    GridActionsCellItemProps,
    GridRenderCellParams,
} from '@mui/x-data-grid'
import {MoreVert} from '@mui/icons-material'
import React, {useState, ReactElement} from 'react'

type EntityCardProps<Entity extends GridValidRowModel> = {
    entity: Entity
    columns: GridColDef<Entity>[]
    actions: ReactElement<GridActionsCellItemProps>[]
    onRowClick?: () => void
}

const EntityCard = <Entity extends GridValidRowModel>({
    entity,
    columns,
    actions,
    onRowClick,
}: EntityCardProps<Entity>) => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
    const open = Boolean(anchorEl)

    const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
        event.stopPropagation()
        setAnchorEl(event.currentTarget)
    }

    const handleMenuClose = () => {
        setAnchorEl(null)
    }

    const handleActionClick =
        (action: ReactElement<GridActionsCellItemProps>) =>
        (event: React.MouseEvent<HTMLButtonElement | HTMLLIElement>) => {
            handleMenuClose()
            if (action.props.onClick) {
                action.props.onClick(
                    event as React.MouseEvent<HTMLButtonElement> & React.MouseEvent<HTMLLIElement>,
                )
            }
        }

    return (
        <Card
            sx={{
                cursor: onRowClick ? 'pointer' : 'default',
                '&:hover': onRowClick ? {boxShadow: 3} : {},
                position: 'relative',
            }}
            onClick={onRowClick}>
            <CardContent>
                {actions.length > 0 && (
                    <Box sx={{position: 'absolute', top: 8, right: 8}}>
                        <IconButton size="small" onClick={handleMenuOpen} sx={{cursor: 'pointer'}}>
                            <MoreVert />
                        </IconButton>
                        <Menu
                            anchorEl={anchorEl}
                            open={open}
                            onClose={handleMenuClose}
                            anchorOrigin={{
                                vertical: 'bottom',
                                horizontal: 'right',
                            }}
                            transformOrigin={{
                                vertical: 'top',
                                horizontal: 'right',
                            }}>
                            {actions.map((action, index) => (
                                <MenuItem
                                    key={index}
                                    onClick={handleActionClick(action)}
                                    sx={{cursor: 'pointer'}}>
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        {action.props.icon}
                                        <Typography>{action.props.label}</Typography>
                                    </Stack>
                                </MenuItem>
                            ))}
                        </Menu>
                    </Box>
                )}
                <Stack spacing={1.5}>
                    {columns
                        .filter(col => col.field !== 'actions')
                        .map((col, index) => {
                            let value = entity[col.field]
                            let displayValue: React.ReactNode = value

                            // First, compute the value using valueGetter if present
                            if (col.valueGetter) {
                                // @ts-expect-error - valueGetter requires GridApi ref which we don't have in card view
                                value = col.valueGetter(value, entity, col, {current: null})
                            }

                            // Then apply formatting or custom rendering
                            if (col.renderCell) {
                                const params = {
                                    value,
                                    row: entity,
                                    field: col.field,
                                    id: entity.id,
                                    tabIndex: 0,
                                    hasFocus: false,
                                    formattedValue: value,
                                    colDef: col,
                                    isEditable: false,
                                    cellMode: 'view' as const,
                                } as GridRenderCellParams<Entity>
                                displayValue = col.renderCell(params)
                            } else if (col.valueFormatter) {
                                // @ts-expect-error - valueFormatter requires GridApi ref which we don't have in card view
                                displayValue = col.valueFormatter(value, entity, col, {
                                    current: null,
                                })
                            } else {
                                displayValue = value
                            }

                            return (
                                <Box key={col.field}>
                                    <Typography
                                        variant="caption"
                                        color="text.secondary"
                                        sx={{
                                            fontWeight: 500,
                                            display: 'block',
                                        }}>
                                        {col.headerName}
                                    </Typography>
                                    <Typography
                                        variant="body2"
                                        sx={{
                                            fontWeight: index === 0 ? 600 : 400,
                                            fontSize: index === 0 ? '1rem' : '0.875rem',
                                        }}>
                                        {displayValue}
                                    </Typography>
                                </Box>
                            )
                        })}
                </Stack>
            </CardContent>
        </Card>
    )
}

export default EntityCard
