import {Box, IconButton, ToggleButton, ToggleButtonGroup, Typography} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import {useTranslation} from 'react-i18next'
import {MyEventCode} from '@utils/myEventStorage.ts'

type MyEventPersonSwitcherProps = {
    codes: MyEventCode[]
    activeQrCode: string
    onSelect: (qrCode: string) => void
    onRemove: (qrCode: string) => void
}

/**
 * Umschalter für Eltern und Betreuende, die mehrere Bänder gescannt haben. Bei nur einer
 * Person bleibt er weg: ein Umschalter mit genau einem Knopf sieht nach einem Fehler aus.
 */
export const MyEventPersonSwitcher = ({
    codes,
    activeQrCode,
    onSelect,
    onRemove,
}: MyEventPersonSwitcherProps) => {
    const {t} = useTranslation()

    if (codes.length <= 1) {
        return null
    }

    return (
        <ToggleButtonGroup
            value={activeQrCode}
            exclusive
            size="small"
            sx={{mb: 2, flexWrap: 'wrap'}}
            onChange={(_, value: string | null) => {
                // MUI meldet beim erneuten Tippen auf den aktiven Knopf null — dann bleibt
                // die Auswahl stehen, statt die Anzeige leer zu räumen.
                if (value !== null) {
                    onSelect(value)
                }
            }}>
            {codes.map(code => (
                <ToggleButton key={code.qrCode} value={code.qrCode} sx={{textTransform: 'none'}}>
                    <Typography variant="body2" sx={{mr: 0.5}}>
                        {/* Vor dem ersten erfolgreichen Abruf ist der Name noch nicht bekannt;
                            der Codeanfang reicht, um zwei Bänder zu unterscheiden. */}
                        {code.displayName ?? code.qrCode.slice(0, 8)}
                    </Typography>
                    <Box
                        component="span"
                        onClick={event => {
                            // Der Klick auf das Kreuz darf nicht zusätzlich die Person wechseln.
                            event.stopPropagation()
                        }}
                        sx={{display: 'inline-flex'}}>
                        <IconButton
                            size="small"
                            aria-label={t('myEvent.remove')}
                            title={t('myEvent.remove')}
                            onClick={() => onRemove(code.qrCode)}>
                            <CloseIcon fontSize="inherit" />
                        </IconButton>
                    </Box>
                </ToggleButton>
            ))}
        </ToggleButtonGroup>
    )
}
