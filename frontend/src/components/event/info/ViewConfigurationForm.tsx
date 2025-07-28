import { useForm } from 'react-hook-form'
import {
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    MenuItem,
    Box,
    Typography,
    Slider
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { InfoViewConfigurationDto, InfoViewConfigurationRequest } from '@api/types.gen'

interface ViewConfigurationFormProps {
    view?: InfoViewConfigurationDto | null
    onSubmit: (data: InfoViewConfigurationRequest) => void
    onCancel: () => void
}

const VIEW_TYPES = [
    { value: 'UPCOMING_MATCHES', label: 'event.info.viewType.upcomingMatches' },
    { value: 'LATEST_MATCH_RESULTS', label: 'event.info.viewType.latestMatchResults' }
]

const ViewConfigurationForm = ({ view, onSubmit, onCancel }: ViewConfigurationFormProps) => {
    const { t } = useTranslation()
    
    const { register, handleSubmit, watch, setValue, formState: { errors } } = useForm<InfoViewConfigurationRequest>({
        defaultValues: view ? {
            viewType: view.viewType,
            displayDurationSeconds: view.displayDurationSeconds,
            dataLimit: view.dataLimit,
            filters: view.filters,
            sortOrder: view.sortOrder,
            isActive: view.isActive
        } : {
            viewType: 'UPCOMING_MATCHES',
            displayDurationSeconds: 10,
            dataLimit: 10,
            filters: undefined,
            sortOrder: 0,
            isActive: true
        }
    })
    
    const displayDuration = watch('displayDurationSeconds')
    const dataLimit = watch('dataLimit')
    
    return (
        <form onSubmit={handleSubmit(onSubmit)}>
            <DialogTitle>
                {view ? t('event.info.editView') : t('event.info.addView')}
            </DialogTitle>
            
            <DialogContent>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, pt: 1 }}>
                    <TextField
                        select
                        label={String(t('event.info.viewType' as any))}
                        {...register('viewType', { required: true })}
                        error={!!errors.viewType}
                        fullWidth
                    >
                        {VIEW_TYPES.map(type => (
                            <MenuItem key={type.value} value={type.value}>
                                {String(t(type.label as any))}
                            </MenuItem>
                        ))}
                    </TextField>
                    
                    <Box>
                        <Typography gutterBottom>
                            {t('event.info.displayDuration')}: {displayDuration} {String(t('common.seconds' as any))}
                        </Typography>
                        <Slider
                            value={displayDuration}
                            onChange={(_, value) => setValue('displayDurationSeconds', value as number)}
                            min={5}
                            max={60}
                            step={5}
                            marks={[
                                { value: 5, label: '5s' },
                                { value: 20, label: '20s' },
                                { value: 40, label: '40s' },
                                { value: 60, label: '60s' }
                            ]}
                        />
                    </Box>
                    
                    <Box>
                        <Typography gutterBottom>
                            {t('event.info.dataLimit')}: {dataLimit} {t('common.items')}
                        </Typography>
                        <Slider
                            value={dataLimit}
                            onChange={(_, value) => setValue('dataLimit', value as number)}
                            min={1}
                            max={50}
                            step={1}
                            marks={[
                                { value: 1, label: '1' },
                                { value: 10, label: '10' },
                                { value: 25, label: '25' },
                                { value: 50, label: '50' }
                            ]}
                        />
                    </Box>
                    
                    <Typography variant="caption" color="text.secondary">
                        {t('event.info.filtersComingSoon')}
                    </Typography>
                </Box>
            </DialogContent>
            
            <DialogActions>
                <Button onClick={onCancel}>
                    {t('common.cancel')}
                </Button>
                <Button type="submit" variant="contained">
                    {view ? t('common.update') : t('common.create')}
                </Button>
            </DialogActions>
        </form>
    )
}

export default ViewConfigurationForm