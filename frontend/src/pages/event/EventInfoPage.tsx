import {useCallback, useEffect, useState} from 'react'
import {Box, CircularProgress, Fade, IconButton, Dialog, DialogContent, Button} from '@mui/material'
import {
    Fullscreen as FullscreenIcon,
    FullscreenExit as FullscreenExitIcon,
    Settings as SettingsIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks'
import InfoViewConfiguration from '@components/event/info/InfoViewConfiguration'
import InfoViewDisplay from '@components/event/info/InfoViewDisplay'
import ViewRotationControl from '@components/event/info/ViewRotationControl'
import {InfoViewConfigurationDto} from '@api/types.gen'
import {getInfoViews} from '@api/sdk.gen'
import {eventInfoRoute} from "@routes";

const EventInfoPage = () => {
    const {t} = useTranslation()
    const {eventId} = eventInfoRoute.useParams()

    const [configOpen, setConfigOpen] = useState(false)
    const [fullscreen, setFullscreen] = useState(false)
    const [currentViewIndex, setCurrentViewIndex] = useState(0)
    const [isPlaying, setIsPlaying] = useState(true)
    const [views, setViews] = useState<InfoViewConfigurationDto[]>([])
    const [dataRefreshKey, setDataRefreshKey] = useState(0)
    const [viewsRefreshKey, setViewsRefreshKey] = useState(0)
    const [showControls, setShowControls] = useState(true)
    const [mouseTimer, setMouseTimer] = useState<NodeJS.Timeout | null>(null)

    // Fetch views
    const {data: viewsData, pending} = useFetch(signal => getInfoViews({signal, path: {eventId}}), {
        deps: [eventId, viewsRefreshKey],
    })

    useEffect(() => {
        if (viewsData) {
            setViews(viewsData.filter(v => v.isActive))
        }
    }, [viewsData])

    // Handle fullscreen
    const toggleFullscreen = useCallback(() => {
        setFullscreen(prev => !prev)
    }, [])

    // Handle mouse movement for auto-hiding controls
    const handleMouseMove = useCallback(() => {
        setShowControls(true)

        // Clear existing timer
        if (mouseTimer) {
            clearTimeout(mouseTimer)
        }

        // Set new timer to hide controls after 5 seconds
        const timer = setTimeout(() => {
            setShowControls(false)
        }, 5000)

        setMouseTimer(timer)
    }, [mouseTimer])

    // Cleanup timer on unmount
    useEffect(() => {
        return () => {
            if (mouseTimer) {
                clearTimeout(mouseTimer)
            }
        }
    }, [mouseTimer])

    // Handle keyboard shortcuts
    useEffect(() => {
        const handleKeyPress = (e: KeyboardEvent) => {
            switch (e.key) {
                case ' ':
                    e.preventDefault()
                    setIsPlaying(prev => !prev)
                    break
                case 'ArrowRight':
                    setCurrentViewIndex(prev => (prev + 1) % views.length)
                    break
                case 'ArrowLeft':
                    setCurrentViewIndex(prev => (prev - 1 + views.length) % views.length)
                    break
                case 'f':
                    toggleFullscreen()
                    break
                case 'Escape':
                    if (fullscreen) {
                        setFullscreen(false)
                    } else {
                        setConfigOpen(false)
                    }
                    break
            }
        }

        window.addEventListener('keydown', handleKeyPress)
        return () => window.removeEventListener('keydown', handleKeyPress)
    }, [views.length, toggleFullscreen, fullscreen])

    // Auto-rotation/refresh effect
    useEffect(() => {
        if (!isPlaying || configOpen || views.length === 0) return

        const currentView = views[currentViewIndex]
        if (!currentView) return

        const timer = setTimeout(() => {
            if (views.length > 1) {
                // Multiple views: rotate to next view
                setCurrentViewIndex(prev => (prev + 1) % views.length)
            } else {
                // Single view: refresh data
                setDataRefreshKey(prev => prev + 1)
            }
        }, currentView.displayDurationSeconds * 1000)

        return () => clearTimeout(timer)
    }, [currentViewIndex, isPlaying, views, configOpen, dataRefreshKey])

    if (pending) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    height: '100vh',
                }}>
                <CircularProgress />
            </Box>
        )
    }

    const currentView = views[currentViewIndex]

    return (
        <Box
            sx={{height: '90vh', position: 'relative', overflow: 'hidden'}}
            onMouseMove={handleMouseMove}>
            {/* Main content */}
            <Box
                sx={{
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    p: configOpen ? 2 : 0,
                }}>
                {/* Controls */}
                {!configOpen && (
                    <Box
                        sx={{
                            position: 'absolute',
                            top: 16,
                            right: 16,
                            zIndex: 10,
                            display: 'flex',
                            gap: 1,
                        }}>
                        <Button
                            onClick={() => setConfigOpen(true)}
                            startIcon={<SettingsIcon />}
                            variant="contained"
                            color="primary"
                            size="small">
                            {t('event.info.configure')}
                        </Button>
                        <Button
                            onClick={toggleFullscreen}
                            startIcon={fullscreen ? <FullscreenExitIcon /> : <FullscreenIcon />}
                            variant="contained"
                            color="primary"
                            size="small">
                            {fullscreen ? t('common.exitFullscreen') : t('common.fullscreen')}
                        </Button>
                    </Box>
                )}

                {/* Rotation control - show for multiple views or single view when playing */}
                {!configOpen && (views.length > 1 || (views.length === 1 && isPlaying)) && (
                    <ViewRotationControl
                        currentIndex={currentViewIndex}
                        totalViews={views.length}
                        isPlaying={isPlaying}
                        onPlayPause={() => setIsPlaying(!isPlaying)}
                        onNavigate={setCurrentViewIndex}
                        currentView={currentView}
                        showControls={showControls}
                    />
                )}

                {/* View display with preloading */}
                {currentView && !configOpen && (
                    <Box sx={{flex: 1, overflow: 'auto', position: 'relative'}}>
                        {/* Current view */}
                        <InfoViewDisplay
                            key={`${currentView.id}-${dataRefreshKey}`}
                            eventId={eventId}
                            view={currentView}
                        />

                        {/* Preload next view */}
                        {views.length > 1 && (
                            <Box
                                sx={{
                                    position: 'absolute',
                                    visibility: 'hidden',
                                    pointerEvents: 'none',
                                }}>
                                <InfoViewDisplay
                                    key={`${views[(currentViewIndex + 1) % views.length].id}-${dataRefreshKey}-preload`}
                                    eventId={eventId}
                                    view={views[(currentViewIndex + 1) % views.length]}
                                />
                            </Box>
                        )}
                    </Box>
                )}

                {/* No views message */}
                {!currentView && !configOpen && (
                    <Box
                        sx={{
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                        }}>
                        <Box sx={{textAlign: 'center'}}>
                            <h2>{t('event.info.noViews')}</h2>
                            <p>{t('event.info.configureViews')}</p>
                        </Box>
                    </Box>
                )}
            </Box>

            {/* Configuration panel */}
            <InfoViewConfiguration
                eventId={eventId}
                open={configOpen}
                onClose={() => setConfigOpen(false)}
                onUpdate={() => setViewsRefreshKey(prev => prev + 1)}
            />

            {/* Fullscreen Dialog */}
            <Dialog
                fullScreen
                open={fullscreen}
                onClose={toggleFullscreen}
                PaperProps={{
                    sx: {
                        bgcolor: 'background.default',
                        backgroundImage: 'none',
                    },
                }}>
                <DialogContent
                    sx={{p: 0, position: 'relative', height: '100vh', overflow: 'hidden'}}
                    onMouseMove={handleMouseMove}>
                    {/* Exit fullscreen button */}
                    <Fade in={showControls}>
                        <IconButton
                            onClick={toggleFullscreen}
                            sx={{
                                position: 'absolute',
                                top: 16,
                                right: 16,
                                zIndex: 10,
                                bgcolor: 'background.paper',
                                boxShadow: 1,
                                '&:hover': {
                                    bgcolor: 'background.paper',
                                },
                            }}>
                            <FullscreenExitIcon />
                        </IconButton>
                    </Fade>

                    {/* Rotation control in fullscreen */}
                    {(views.length > 1 || (views.length === 1 && isPlaying)) && (
                        <ViewRotationControl
                            currentIndex={currentViewIndex}
                            totalViews={views.length}
                            isPlaying={isPlaying}
                            onPlayPause={() => setIsPlaying(!isPlaying)}
                            onNavigate={setCurrentViewIndex}
                            currentView={currentView}
                            showControls={showControls}
                        />
                    )}

                    {/* View display in fullscreen with preloading */}
                    {currentView && (
                        <Box sx={{height: '100%', overflow: 'auto', position: 'relative'}}>
                            {/* Current view */}
                            <InfoViewDisplay
                                key={`${currentView.id}-${dataRefreshKey}`}
                                eventId={eventId}
                                view={currentView}
                            />

                            {/* Preload next view */}
                            {views.length > 1 && (
                                <Box
                                    sx={{
                                        position: 'absolute',
                                        visibility: 'hidden',
                                        pointerEvents: 'none',
                                    }}>
                                    <InfoViewDisplay
                                        key={`${views[(currentViewIndex + 1) % views.length].id}-${dataRefreshKey}-preload`}
                                        eventId={eventId}
                                        view={views[(currentViewIndex + 1) % views.length]}
                                    />
                                </Box>
                            )}
                        </Box>
                    )}

                    {/* No views message in fullscreen */}
                    {!currentView && (
                        <Box
                            sx={{
                                height: '100%',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                            }}>
                            <Box sx={{textAlign: 'center'}}>
                                <h2>{t('event.info.noViews')}</h2>
                                <p>{t('event.info.configureViews')}</p>
                            </Box>
                        </Box>
                    )}
                </DialogContent>
            </Dialog>
        </Box>
    )
}

export default EventInfoPage
