import {useForm} from 'react-hook-form'
import {FormContainer} from 'react-hook-form-mui'
import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Slider,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {InfoViewConfigurationDto, InfoViewConfigurationRequest} from '@api/types.gen'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'

interface ViewConfigurationFormProps {
    view?: InfoViewConfigurationDto | null
    onSubmit: (data: InfoViewConfigurationRequest) => void
    onCancel: () => void
}

const ViewConfigurationForm = ({view, onSubmit, onCancel}: ViewConfigurationFormProps) => {
    const {t} = useTranslation()

    const VIEW_TYPES = [
        {id: 'UPCOMING_MATCHES', label: t('event.info.viewTypes.upcomingMatches')},
        {id: 'LATEST_MATCH_RESULTS', label: t('event.info.viewTypes.latestMatchResults')},
        {id: 'RUNNING_MATCHES', label: t('event.info.viewTypes.runningMatches')},
    ]

    const formContext = useForm<InfoViewConfigurationRequest>({
        defaultValues: view
            ? {
                  viewType: view.viewType,
                  displayDurationSeconds: view.displayDurationSeconds,
                  dataLimit: view.dataLimit,
                  filters: view.filters,
                  sortOrder: view.sortOrder,
                  isActive: view.isActive,
              }
            : {
                  viewType: 'UPCOMING_MATCHES',
                  displayDurationSeconds: 10,
                  dataLimit: 10,
                  filters: undefined,
                  sortOrder: 0,
                  isActive: true,
              },
    })

    const {watch, setValue} = formContext
    const displayDuration = watch('displayDurationSeconds')
    const dataLimit = watch('dataLimit')

    return (
        <>
            <DialogTitle>{view ? t('event.info.editView') : t('event.info.addView')}</DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={onSubmit}
                FormProps={{style: {display: 'contents'}}}>
                <DialogContent>
                    <Box sx={{display: 'flex', flexDirection: 'column', gap: 3, pt: 1}}>
                        <FormInputSelect
                            label={t('event.info.viewType') as string}
                            required={true}
                            name="viewType"
                            options={VIEW_TYPES}
                            fullWidth
                        />

                        <Box>
                            <Typography gutterBottom>
                                {t('event.info.displayDuration')}: {displayDuration}{' '}
                                {String(t('common.seconds' as any))}
                            </Typography>
                            <Slider
                                value={displayDuration}
                                onChange={(_, value) =>
                                    setValue('displayDurationSeconds', value as number)
                                }
                                min={5}
                                max={60}
                                step={5}
                                marks={[
                                    {value: 5, label: '5s'},
                                    {value: 20, label: '20s'},
                                    {value: 40, label: '40s'},
                                    {value: 60, label: '60s'},
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
                                    {value: 1, label: '1'},
                                    {value: 10, label: '10'},
                                    {value: 25, label: '25'},
                                    {value: 50, label: '50'},
                                ]}
                            />
                        </Box>
                    </Box>
                </DialogContent>

                <DialogActions>
                    <Button onClick={onCancel}>{t('common.cancel')}</Button>
                    <Button type="submit" variant="contained">
                        {view ? t('common.update') : t('common.create')}
                    </Button>
                </DialogActions>
            </FormContainer>
        </>
    )
}

export default ViewConfigurationForm
