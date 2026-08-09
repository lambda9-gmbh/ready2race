import {
    Alert,
    AlertTitle,
    Box,
    List,
    ListItem,
    ListItemIcon,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import {useTranslation} from 'react-i18next'
import {MyEventRequirementDto} from '@api/types.gen.ts'
import {openRequirements} from './myEventOrder.ts'

type MyEventRequirementsProps = {
    requirements: MyEventRequirementDto[]
    // "banner": das Band ganz oben, das nur die offenen Pflichtbedingungen nennt.
    // "list": die vollständige Liste weiter unten, erledigte eingeschlossen.
    variant: 'banner' | 'list'
}

/**
 * Bedingungen der eigenen Teilnahme (Pass, Beitrag, Nachweis …), in zwei Ausprägungen aus
 * einer Datei — beide lesen dieselben Daten, und getrennte Dateien wären zwei Orte, an denen
 * derselbe Zustand unterschiedlich benannt werden könnte.
 */
export const MyEventRequirements = ({requirements, variant}: MyEventRequirementsProps) => {
    const {t} = useTranslation()

    if (variant === 'banner') {
        const open = openRequirements(requirements)
        // Kein Band ohne offene Pflicht: ein Band, das nur "alles gut" sagt, würde am Renntag
        // oben Platz kosten und beim nächsten Mal übersehen werden.
        if (open.length === 0) {
            return null
        }
        return (
            <Alert severity="warning" sx={{mb: 2}}>
                <AlertTitle>{t('myEvent.requirementsOpen')}</AlertTitle>
                <Stack component="ul" sx={{pl: 2.5, my: 0.5}} spacing={0.25}>
                    {open.map(r => (
                        <Typography component="li" key={r.id} sx={{fontWeight: 600}}>
                            {r.name}
                        </Typography>
                    ))}
                </Stack>
                <Typography variant="body2">{t('myEvent.requirementsOfficeHint')}</Typography>
            </Alert>
        )
    }

    if (requirements.length === 0) {
        return null
    }

    const allDone = openRequirements(requirements).length === 0

    return (
        <Box>
            {allDone && (
                <Typography variant="body2" color="success.main" sx={{mb: 0.5}}>
                    {t('myEvent.requirementsAllDone')}
                </Typography>
            )}
            <List dense disablePadding>
                {requirements.map(r => (
                    <ListItem key={r.id} disableGutters sx={{alignItems: 'flex-start'}}>
                        <ListItemIcon sx={{minWidth: 36, mt: 0.5}}>
                            {r.fulfilled ? (
                                <CheckCircleOutlineIcon color="success" fontSize="small" />
                            ) : (
                                <RadioButtonUncheckedIcon
                                    color={r.optional ? 'disabled' : 'warning'}
                                    fontSize="small"
                                />
                            )}
                        </ListItemIcon>
                        <ListItemText
                            primary={
                                <Stack
                                    direction="row"
                                    gap={1}
                                    alignItems="baseline"
                                    flexWrap="wrap">
                                    <Typography sx={{fontWeight: r.fulfilled ? 400 : 600}}>
                                        {r.name}
                                    </Typography>
                                    {/* Eine freiwillige Bedingung bleibt auch offen harmlos — der
                                        Zusatz nimmt ihr die Dringlichkeit, die das Symbol suggeriert. */}
                                    {r.optional && (
                                        <Typography variant="caption" color="text.secondary">
                                            {t('myEvent.requirementOptional')}
                                        </Typography>
                                    )}
                                    <Typography
                                        variant="caption"
                                        color={r.fulfilled ? 'success.main' : 'text.secondary'}>
                                        {r.fulfilled
                                            ? t('myEvent.requirementFulfilled')
                                            : t('myEvent.requirementOpen')}
                                    </Typography>
                                </Stack>
                            }
                            secondary={r.description ?? undefined}
                        />
                    </ListItem>
                ))}
            </List>
        </Box>
    )
}
