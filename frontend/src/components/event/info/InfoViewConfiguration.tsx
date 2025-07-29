import {useState} from 'react'
import {
    Box,
    Drawer,
    Fab,
    IconButton,
    List,
    ListItem,
    ListItemSecondaryAction,
    ListItemText,
    Switch,
    Typography,
} from '@mui/material'
import {
    Add as AddIcon,
    Close as CloseIcon,
    Delete as DeleteIcon,
    DragHandle as DragHandleIcon,
    Edit as EditIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks'
import {
    closestCenter,
    DndContext,
    DragEndEvent,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
} from '@dnd-kit/core'
import {
    arrayMove,
    SortableContext,
    sortableKeyboardCoordinates,
    useSortable,
    verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import {CSS} from '@dnd-kit/utilities'
import ViewConfigurationForm from './ViewConfigurationForm'
import BaseDialog from '@components/BaseDialog'
import {InfoViewConfigurationDto, InfoViewConfigurationRequest} from '@api/types.gen'
import {createInfoView, deleteInfoView, getInfoViews, updateInfoView} from '@api/sdk.gen'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext'

interface InfoViewConfigurationProps {
    eventId: string
    open: boolean
    onClose: () => void
    onUpdate: () => void
}

interface SortableItemProps {
    view: InfoViewConfigurationDto
    onEdit: () => void
    onDelete: () => void
    onToggle: () => void
}

const SortableItem = ({view, onEdit, onDelete, onToggle}: SortableItemProps) => {
    const {t} = useTranslation()
    const {attributes, listeners, setNodeRef, transform, transition} = useSortable({id: view.id})

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
    }

    const getViewTypeName = (type: string) => {
        switch (type) {
            case 'UPCOMING_MATCHES':
                return t('event.info.viewTypes.upcomingMatches')
            case 'LATEST_MATCH_RESULTS':
                return t('event.info.viewTypes.latestMatchResults')
            default:
                return type
        }
    }

    return (
        <ListItem
            ref={setNodeRef}
            style={style}
            sx={{
                bgcolor: 'background.paper',
                mb: 1,
                borderRadius: 1,
                boxShadow: 1,
            }}>
            <Box {...attributes} {...listeners} sx={{cursor: 'grab', mr: 2}}>
                <DragHandleIcon />
            </Box>
            <ListItemText
                primary={getViewTypeName(view.viewType)}
                secondary={`${view.displayDurationSeconds}s • ${view.dataLimit} ${t('common.items')}`}
            />
            <ListItemSecondaryAction>
                <Switch edge="end" checked={view.isActive} onChange={onToggle} />
                <IconButton onClick={onEdit} size="small">
                    <EditIcon />
                </IconButton>
                <IconButton onClick={onDelete} size="small">
                    <DeleteIcon />
                </IconButton>
            </ListItemSecondaryAction>
        </ListItem>
    )
}

const InfoViewConfiguration = ({eventId, open, onClose, onUpdate}: InfoViewConfigurationProps) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()
    const [formOpen, setFormOpen] = useState(false)
    const [editingView, setEditingView] = useState<InfoViewConfigurationDto | null>(null)

    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        }),
    )

    const [reloadKey, setReloadKey] = useState(0)

    const {data: views} = useFetch(
        signal => getInfoViews({signal, path: {eventId}, query: {includeInactive: true}}),
        {deps: [eventId, open, reloadKey]},
    )

    const handleDragEnd = async (event: DragEndEvent) => {
        const {active, over} = event

        if (active.id !== over?.id && views) {
            const oldIndex = views.findIndex(v => v.id === active.id)
            const newIndex = views.findIndex(v => v.id === over?.id)

            const newViews = arrayMove(views, oldIndex, newIndex)

            // Update sort order for all affected views
            const updates = newViews.map((view, index) => ({
                ...view,
                sortOrder: index,
            }))

            try {
                await Promise.all(
                    updates.map(view =>
                        updateInfoView({
                            path: {eventId, viewId: view.id},
                            body: {
                                viewType: view.viewType,
                                displayDurationSeconds: view.displayDurationSeconds,
                                dataLimit: view.dataLimit,
                                filters: view.filters,
                                sortOrder: view.sortOrder,
                                isActive: view.isActive,
                            },
                        }),
                    ),
                )
                setReloadKey(prev => prev + 1)
                onUpdate()
            } catch (error) {
                feedback.error(t('event.info.updateError'))
            }
        }
    }

    const handleCreate = async (request: InfoViewConfigurationRequest) => {
        try {
            await createInfoView({
                path: {eventId},
                body: request,
            })
            feedback.success(t('event.info.viewCreated'))
            setFormOpen(false)
            setReloadKey(prev => prev + 1)
            onUpdate()
        } catch (error) {
            feedback.error(t('event.info.createError'))
        }
    }

    const handleUpdate = async (request: InfoViewConfigurationRequest) => {
        if (!editingView) return

        try {
            await updateInfoView({
                path: {eventId, viewId: editingView.id},
                body: request,
            })
            feedback.success(t('event.info.viewUpdated'))
            setEditingView(null)
            setFormOpen(false)
            setReloadKey(prev => prev + 1)
            onUpdate()
        } catch (error) {
            feedback.error(t('event.info.updateError'))
        }
    }

    const handleToggle = async (view: InfoViewConfigurationDto) => {
        try {
            await updateInfoView({
                path: {eventId, viewId: view.id},
                body: {
                    viewType: view.viewType,
                    displayDurationSeconds: view.displayDurationSeconds,
                    dataLimit: view.dataLimit,
                    filters: view.filters,
                    sortOrder: view.sortOrder,
                    isActive: !view.isActive,
                },
            })
            setReloadKey(prev => prev + 1)
            onUpdate()
        } catch (error) {
            feedback.error(t('event.info.updateError'))
        }
    }

    const handleDelete = (viewId: string) => {
        confirmAction(
            async () => {
                try {
                    await deleteInfoView({
                        path: {eventId, viewId},
                    })
                    feedback.success(t('event.info.viewDeleted'))
                    setReloadKey(prev => prev + 1)
                    onUpdate()
                } catch (error) {
                    feedback.error(t('event.info.deleteError'))
                }
            },
            {
                title: t('common.confirmDelete'),
                content: t('event.info.confirmDeleteView'),
                okText: t('common.delete'),
            },
        )
    }

    return (
        <>
            <Drawer
                anchor="right"
                open={open}
                onClose={onClose}
                sx={{
                    '& .MuiDrawer-paper': {
                        width: {xs: '100%', sm: 600},
                        p: 3,
                        bottom: 0,
                        height: 'auto',
                    },
                }}>
                <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 3}}>
                    <Typography variant="h5">{t('event.info.configuration')}</Typography>
                    <IconButton onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                </Box>

                <Typography variant="body2" color="text.secondary">
                    {t('event.info.configurationHelp')}
                </Typography>

                {views && views.length > 0 ? (
                    <DndContext
                        sensors={sensors}
                        collisionDetection={closestCenter}
                        onDragEnd={handleDragEnd}>
                        <SortableContext
                            items={views.map(v => v.id)}
                            strategy={verticalListSortingStrategy}>
                            <List>
                                {views.map(view => (
                                    <SortableItem
                                        key={view.id}
                                        view={view}
                                        onEdit={() => {
                                            setEditingView(view)
                                            setFormOpen(true)
                                        }}
                                        onDelete={() => handleDelete(view.id)}
                                        onToggle={() => handleToggle(view)}
                                    />
                                ))}
                            </List>
                        </SortableContext>
                    </DndContext>
                ) : (
                    <Box sx={{textAlign: 'center', py: 4}}>
                        <Typography color="text.secondary">
                            {t('event.info.noViewsConfigured')}
                        </Typography>
                    </Box>
                )}

                <Fab
                    color="primary"
                    sx={{position: 'absolute', bottom: 24, right: 24}}
                    onClick={() => {
                        setEditingView(null)
                        setFormOpen(true)
                    }}>
                    <AddIcon />
                </Fab>
            </Drawer>

            <BaseDialog
                open={formOpen}
                onClose={() => {
                    setFormOpen(false)
                    setEditingView(null)
                }}
                maxWidth="sm">
                <ViewConfigurationForm
                    view={editingView}
                    onSubmit={editingView ? handleUpdate : handleCreate}
                    onCancel={() => {
                        setFormOpen(false)
                        setEditingView(null)
                    }}
                />
            </BaseDialog>
        </>
    )
}

export default InfoViewConfiguration
