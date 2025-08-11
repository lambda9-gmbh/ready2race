import {Box, Card, CardContent, Stack, Typography, TextField, InputAdornment} from '@mui/material'
import {
    closestCenter,
    DndContext,
    DragEndEvent,
    DragOverlay,
    DragStartEvent,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
    useDroppable,
} from '@dnd-kit/core'
import {
    SortableContext,
    sortableKeyboardCoordinates,
    useSortable,
    verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import {CSS} from '@dnd-kit/utilities'
import {MatchForRunningStatusDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {Stop, DragIndicator, OpenInNew, PlayCircle, Search} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'

interface DragDropMatchListsProps {
    runningMatches: MatchForRunningStatusDto[]
    availableMatches: MatchForRunningStatusDto[]
    onMatchMove: (matchId: string, toRunning: boolean) => void
    disabled?: boolean
    eventId: string
}

interface MatchCardProps {
    match: MatchForRunningStatusDto
    isDragging?: boolean
    dragHandleProps?: any
    eventId: string
}

const MatchCard = ({match, isDragging, dragHandleProps, eventId}: MatchCardProps) => {
    const {t} = useTranslation()

    return (
        <Card
            sx={{
                mb: 1,
                opacity: isDragging ? 0.5 : 1,
            }}>
            <CardContent sx={{py: 1.5, '&:last-child': {pb: 1.5}}}>
                <Stack direction="row" alignItems="center" spacing={1}>
                    <Box
                        {...dragHandleProps}
                        sx={{
                            cursor: 'grab',
                            '&:active': {cursor: 'grabbing'},
                        }}>
                        <DragIndicator
                            sx={{
                                color: 'action.disabled',
                            }}
                        />
                    </Box>
                    <Box flex={1}>
                        <Typography variant="subtitle2">{match.competitionName}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {match.roundName} -{' '}
                            {match.matchName ||
                                `${t('event.competition.execution.match.match' as any) as string} ${
                                    match.matchNumber
                                }`}
                        </Typography>
                    </Box>
                    <Link
                        to="/event/$eventId/competition/$competitionId"
                        params={{
                            eventId: eventId,
                            competitionId: match.competitionId,
                        }}
                        search={{tab: 'execution'}}
                        className="cursor-pointer"
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            textDecoration: 'none',
                            color: '#1976d2',
                        }}
                        onClick={(e: React.MouseEvent) => e.stopPropagation()}>
                        <OpenInNew fontSize="small" />
                    </Link>
                </Stack>
            </CardContent>
        </Card>
    )
}

const SortableMatchCard = ({
    match,
    eventId,
}: {
    match: MatchForRunningStatusDto
    eventId: string
}) => {
    const {attributes, listeners, setNodeRef, transform, transition, isDragging} = useSortable({
        id: match.id,
    })

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
    }

    return (
        <div ref={setNodeRef} style={style} {...attributes}>
            <MatchCard
                match={match}
                isDragging={isDragging}
                dragHandleProps={listeners}
                eventId={eventId}
            />
        </div>
    )
}

const DroppableContainer = ({
    id,
    children,
    disabled,
}: {
    id: string
    children: React.ReactNode
    disabled?: boolean
}) => {
    const {setNodeRef, isOver} = useDroppable({
        id,
        disabled,
    })

    return (
        <Box
            ref={setNodeRef}
            sx={{
                minHeight: 300,
                p: 2,
                bgcolor: isOver ? 'action.hover' : 'background.paper',
                border: 1,
                borderColor: isOver ? 'primary.main' : 'divider',
                borderRadius: 1,
                opacity: disabled ? 0.6 : 1,
                transition: 'all 0.2s',
            }}>
            {children}
        </Box>
    )
}

const DragDropMatchLists = ({
    runningMatches,
    availableMatches,
    onMatchMove,
    disabled,
    eventId,
}: DragDropMatchListsProps) => {
    const {t} = useTranslation()
    const [activeId, setActiveId] = useState<string | null>(null)
    const [searchTerm, setSearchTerm] = useState('')
    const [runningSearchTerm, setRunningSearchTerm] = useState('')

    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        }),
    )

    const handleDragStart = (event: DragStartEvent) => {
        setActiveId(event.active.id as string)
    }

    const handleDragEnd = (event: DragEndEvent) => {
        const {active, over} = event

        if (over && active.id !== over.id) {
            const activeId = active.id as string
            const overId = over.id as string

            // Determine which list the item was dragged to
            const isInRunning = runningMatches.some(m => m.id === activeId)
            const isOverRunning = overId === 'running' || runningMatches.some(m => m.id === overId)
            const isOverAvailable =
                overId === 'available' || availableMatches.some(m => m.id === overId)

            if (isInRunning && isOverAvailable) {
                onMatchMove(activeId, false)
            } else if (!isInRunning && isOverRunning) {
                onMatchMove(activeId, true)
            }
        }

        setActiveId(null)
    }

    const activeMatch = [...runningMatches, ...availableMatches].find(m => m.id === activeId)

    // Filter running matches based on search term
    const filteredRunningMatches = runningMatches.filter(match => {
        const searchLower = runningSearchTerm.toLowerCase()
        return (
            match.competitionName.toLowerCase().includes(searchLower) ||
            match.roundName.toLowerCase().includes(searchLower) ||
            (match.matchName && match.matchName.toLowerCase().includes(searchLower)) ||
            match.matchNumber.toString().includes(searchLower)
        )
    })

    // Filter available matches based on search term
    const filteredAvailableMatches = availableMatches.filter(match => {
        const searchLower = searchTerm.toLowerCase()
        return (
            match.competitionName.toLowerCase().includes(searchLower) ||
            match.roundName.toLowerCase().includes(searchLower) ||
            (match.matchName && match.matchName.toLowerCase().includes(searchLower)) ||
            match.matchNumber.toString().includes(searchLower)
        )
    })

    return (
        <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}>
            <Stack direction="row" spacing={3} sx={{minHeight: 400}}>
                <Box flex={1}>
                    <Typography variant="h6" gutterBottom>
                        <PlayCircle sx={{verticalAlign: 'middle', mr: 1}} />
                        {t('event.competition.execution.match.currentlyRunning')}
                    </Typography>
                    <TextField
                        fullWidth
                        size="small"
                        placeholder={t('common.search' as any) as string}
                        value={runningSearchTerm}
                        onChange={e => setRunningSearchTerm(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <Search />
                                </InputAdornment>
                            ),
                        }}
                        sx={{mb: 2}}
                        disabled={disabled}
                    />
                    <DroppableContainer id="running" disabled={disabled}>
                        <SortableContext
                            items={filteredRunningMatches.map(m => m.id)}
                            strategy={verticalListSortingStrategy}>
                            {filteredRunningMatches.length === 0 ? (
                                <Typography
                                    variant="body2"
                                    color="text.secondary"
                                    align="center"
                                    sx={{mt: 4}}>
                                    {runningSearchTerm
                                        ? (t('common.noResults' as any) as string)
                                        : t('event.competition.execution.match.noRunningMatches')}
                                </Typography>
                            ) : (
                                filteredRunningMatches.map(match => (
                                    <SortableMatchCard
                                        key={match.id}
                                        match={match}
                                        eventId={eventId}
                                    />
                                ))
                            )}
                        </SortableContext>
                    </DroppableContainer>
                </Box>

                <Box flex={1}>
                    <Typography variant="h6" gutterBottom>
                        <Stop sx={{verticalAlign: 'middle', mr: 1}} />
                        {t('event.competition.execution.match.availableMatches')}
                    </Typography>
                    <TextField
                        fullWidth
                        size="small"
                        placeholder={t('common.search' as any) as string}
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <Search />
                                </InputAdornment>
                            ),
                        }}
                        sx={{mb: 2}}
                        disabled={disabled}
                    />
                    <DroppableContainer id="available" disabled={disabled}>
                        <SortableContext
                            items={filteredAvailableMatches.map(m => m.id)}
                            strategy={verticalListSortingStrategy}>
                            {filteredAvailableMatches.length === 0 ? (
                                <Typography
                                    variant="body2"
                                    color="text.secondary"
                                    align="center"
                                    sx={{mt: 4}}>
                                    {searchTerm
                                        ? (t('common.noResults' as any) as string)
                                        : t('event.competition.execution.match.noAvailableMatches')}
                                </Typography>
                            ) : (
                                filteredAvailableMatches.map(match => (
                                    <SortableMatchCard
                                        key={match.id}
                                        match={match}
                                        eventId={eventId}
                                    />
                                ))
                            )}
                        </SortableContext>
                    </DroppableContainer>
                </Box>
            </Stack>

            <DragOverlay>
                {activeId && activeMatch ? (
                    <MatchCard match={activeMatch} isDragging eventId={eventId} />
                ) : null}
            </DragOverlay>
        </DndContext>
    )
}

export default DragDropMatchLists
