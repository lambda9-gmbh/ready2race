import {Card, CardActionArea, CardContent, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {Controller, useFormContext} from 'react-hook-form-mui'
import {RegistrationForm} from '@components/user/registration/common.ts'
import GroupsIcon from '@mui/icons-material/Groups'
import PersonIcon from '@mui/icons-material/Person'

interface Step1RegistrationTypeProps {
    anySingleCompetitionsAvailable: boolean | null
    onSelect: () => void
}

const TYPE_OPTIONS = [
    {
        value: 'CLUB',
        icon: GroupsIcon,
        titleKey: 'user.registration.asClubRepresentative',
        descriptionKey: 'user.registration.type.club.description',
    },
    {
        value: 'PARTICIPANT',
        icon: PersonIcon,
        titleKey: 'user.registration.asParticipant',
        descriptionKey: 'user.registration.type.participant.description',
    },
] as const

export const Step1RegistrationType = ({
    anySingleCompetitionsAvailable,
    onSelect,
}: Step1RegistrationTypeProps) => {
    const {t} = useTranslation()
    const formContext = useFormContext<RegistrationForm>()

    // Participant-only registration is only possible when there are single competitions
    // open for self-registration.
    const participantAvailable = anySingleCompetitionsAvailable === true

    return (
        <Stack spacing={2}>
            <Controller
                name="registrationType"
                control={formContext.control}
                render={({field}) => (
                    <Stack spacing={2} direction={{xs: 'column', sm: 'row'}}>
                        {TYPE_OPTIONS.map(option => {
                            const Icon = option.icon
                            const disabled = option.value === 'PARTICIPANT' && !participantAvailable
                            const selected = field.value === option.value
                            return (
                                <Card
                                    key={option.value}
                                    variant="outlined"
                                    sx={{
                                        flex: 1,
                                        borderColor: selected ? 'primary.main' : 'divider',
                                        borderWidth: selected ? 2 : 1,
                                        opacity: disabled ? 0.5 : 1,
                                    }}>
                                    <CardActionArea
                                        disabled={disabled}
                                        onClick={() => {
                                            field.onChange(option.value)
                                            onSelect()
                                        }}
                                        sx={{height: '100%'}}>
                                        <CardContent sx={{textAlign: 'center', py: 3}}>
                                            <Icon
                                                sx={{
                                                    fontSize: 48,
                                                    mb: 1,
                                                    color: selected
                                                        ? 'primary.main'
                                                        : 'text.secondary',
                                                }}
                                            />
                                            <Typography variant="h6">{t(option.titleKey)}</Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {disabled
                                                    ? t(
                                                          'user.registration.type.participant.unavailable',
                                                      )
                                                    : t(option.descriptionKey)}
                                            </Typography>
                                        </CardContent>
                                    </CardActionArea>
                                </Card>
                            )
                        })}
                    </Stack>
                )}
            />
        </Stack>
    )
}
