import { Box, IconButton, LinearProgress, Typography } from '@mui/material'
import { 
    PlayArrow as PlayArrowIcon, 
    Pause as PauseIcon, 
    SkipPrevious as SkipPreviousIcon, 
    SkipNext as SkipNextIcon 
} from '@mui/icons-material'
import { InfoViewConfigurationDto } from '@api/types.gen'
import { useTranslation } from 'react-i18next'
import { useState, useEffect } from 'react'

interface ViewRotationControlProps {
    currentIndex: number
    totalViews: number
    isPlaying: boolean
    onPlayPause: () => void
    onNavigate: (index: number) => void
    currentView?: InfoViewConfigurationDto
    showControls?: boolean
}

const ViewRotationControl = ({
    currentIndex,
    totalViews,
    isPlaying,
    onPlayPause,
    onNavigate,
    currentView,
    showControls = true
}: ViewRotationControlProps) => {
    const { t } = useTranslation()
    const [progress, setProgress] = useState(0)
    
    useEffect(() => {
        if (!isPlaying || !currentView) {
            setProgress(0)
            return
        }
        
        const startTime = Date.now()
        const duration = currentView.displayDurationSeconds * 1000
        
        const updateProgress = () => {
            const elapsed = Date.now() - startTime
            const newProgress = Math.min((elapsed / duration) * 100, 100)
            setProgress(newProgress)
            
            if (newProgress < 100) {
                requestAnimationFrame(updateProgress)
            }
        }
        
        requestAnimationFrame(updateProgress)
        
        return () => setProgress(0)
    }, [currentIndex, isPlaying, currentView])
    
    const handlePrevious = () => {
        onNavigate((currentIndex - 1 + totalViews) % totalViews)
    }
    
    const handleNext = () => {
        onNavigate((currentIndex + 1) % totalViews)
    }
    
    const getViewTypeName = (type: string) => {
        switch (type) {
            case 'UPCOMING_MATCHES':
                return t('event.info.viewType.upcomingMatches')
            case 'LATEST_MATCH_RESULTS':
                return t('event.info.viewType.latestMatchResults')
            default:
                return type
        }
    }
    
    return (
        <Box sx={{ 
            position: 'absolute', 
            bottom: 0, 
            left: 0, 
            right: 0, 
            bgcolor: 'background.paper',
            boxShadow: 3,
            zIndex: 5
        }}>
            <LinearProgress 
                variant="determinate" 
                value={isPlaying ? progress : 0} 
                sx={{ height: 4 }}
            />
            <Box sx={{ 
                overflow: 'hidden',
                height: showControls ? 'auto' : 0,
                transition: 'height 0.3s ease-in-out'
            }}>
                <Box sx={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    justifyContent: 'center', 
                    gap: 2,
                    p: 1
                }}>
                    <IconButton onClick={handlePrevious} disabled={totalViews <= 1}>
                        <SkipPreviousIcon />
                    </IconButton>
                    
                    <IconButton onClick={onPlayPause}>
                        {isPlaying ? <PauseIcon /> : <PlayArrowIcon />}
                    </IconButton>
                    
                    <IconButton onClick={handleNext} disabled={totalViews <= 1}>
                        <SkipNextIcon />
                    </IconButton>
                    
                    <Typography variant="body2" sx={{ ml: 2 }}>
                        {currentView && getViewTypeName(currentView.viewType)} ({currentIndex + 1}/{totalViews})
                    </Typography>
                </Box>
            </Box>
        </Box>
    )
}

export default ViewRotationControl