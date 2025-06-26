import {CheckCircle, Pending, RadioButtonUnchecked, Unpublished} from '@mui/icons-material'
import {TaskState} from '@api/types.gen.ts'
import {Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'

export const TaskStateIcon = ({
    state,
    showLabel = false,
}: {
    state: TaskState
    showLabel?: boolean
}) => {
    const {t} = useTranslation()

    switch (state) {
        case 'OPEN':
            return (
                <Stack direction={'row'} spacing={1} alignItems={'center'}>
                    <RadioButtonUnchecked sx={{color: 'gray'}} />
                    {showLabel && <Typography variant={'subtitle2'}>{t('task.OPEN')}</Typography>}
                </Stack>
            )
        case 'IN_PROGRESS':
            return (
                <Stack direction={'row'} spacing={1} alignItems={'center'}>
                    <Pending sx={{color: '#f9d500'}} />
                    {showLabel && (
                        <Typography variant={'subtitle2'}>{t('task.IN_PROGRESS')}</Typography>
                    )}
                </Stack>
            )
        case 'DONE':
            return (
                <Stack direction={'row'} spacing={1} alignItems={'center'}>
                    <CheckCircle sx={{color: 'green'}} />
                    {showLabel && <Typography variant={'subtitle2'}>{t('task.DONE')}</Typography>}
                </Stack>
            )
        case 'CANCELED':
            return (
                <Stack direction={'row'} spacing={1} alignItems={'center'}>
                    <Unpublished sx={{color: 'gray'}} />
                    {showLabel && (
                        <Typography variant={'subtitle2'}>{t('task.CANCELED')}</Typography>
                    )}
                </Stack>
            )
    }
}
