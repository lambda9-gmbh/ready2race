import {IconButton, Stack, ToggleButton, ToggleButtonGroup, Typography} from '@mui/material'
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
 * Umschalter für Eltern und Betreuende, die mehrere Bänder gescannt haben, samt Entfernen-Knopf.
 *
 * Bei nur einer Person bleibt die Knopfreihe weg — ein Umschalter mit genau einem Knopf sieht
 * nach einem Fehler aus. Das Kreuz bleibt trotzdem stehen: es ist der einzige Weg, einen
 * hinterlegten Code wieder loszuwerden, und gerade der häufigste Fall („ich scanne mein eigenes
 * Band auf einem geliehenen Telefon") hat nur einen einzigen Eintrag. Ohne das Kreuz blieben
 * dort Name, Verein, Läufe und der Stand der eigenen Bedingungen dauerhaft auf einem fremden
 * Gerät.
 */
export const MyEventPersonSwitcher = ({
    codes,
    activeQrCode,
    onSelect,
    onRemove,
}: MyEventPersonSwitcherProps) => {
    const {t} = useTranslation()

    if (codes.length === 0) {
        return null
    }

    const label = (code: MyEventCode) =>
        // Vor dem ersten erfolgreichen Abruf ist der Name noch nicht bekannt; der Codeanfang
        // reicht, um zwei Bänder zu unterscheiden.
        code.displayName ?? code.qrCode.slice(0, 8)

    // Ein einzelnes Kreuz braucht die Person im Namen: "Eintrag entfernen" allein sagt nicht,
    // welcher der Einträge verschwindet.
    const active = codes.find(c => c.qrCode === activeQrCode)
    const removeLabel = active ? `${t('myEvent.remove')}: ${label(active)}` : t('myEvent.remove')

    return (
        <Stack
            direction="row"
            gap={0.5}
            flexWrap="wrap"
            alignItems="center"
            // Ohne Knopfreihe steht das Kreuz allein in der Zeile; rechtsbündig liest es sich
            // als Aktion zu der Karte darunter statt als verirrtes Bedienelement.
            justifyContent={codes.length > 1 ? 'flex-start' : 'flex-end'}
            sx={{mb: 2}}>
            {codes.length > 1 && (
                <ToggleButtonGroup
                    value={activeQrCode}
                    exclusive
                    size="small"
                    sx={{flexWrap: 'wrap'}}
                    onChange={(_, value: string | null) => {
                        // MUI meldet beim erneuten Tippen auf den aktiven Knopf null — dann
                        // bleibt die Auswahl stehen, statt die Anzeige leer zu räumen.
                        if (value !== null) {
                            onSelect(value)
                        }
                    }}>
                    {codes.map(code => (
                        <ToggleButton
                            key={code.qrCode}
                            value={code.qrCode}
                            sx={{textTransform: 'none'}}>
                            <Typography variant="body2">{label(code)}</Typography>
                        </ToggleButton>
                    ))}
                </ToggleButtonGroup>
            )}
            {/* Das Kreuz steht neben der Gruppe und nicht in einem der Knöpfe: eine
                Schaltfläche in einer Schaltfläche ist ungültiges HTML und für Tastatur und
                Screenreader nicht sauber erreichbar. Entfernt wird die gerade angezeigte
                Person; wer eine andere loswerden will, schaltet vorher auf sie um. */}
            <IconButton
                size="small"
                aria-label={removeLabel}
                title={removeLabel}
                onClick={() => onRemove(activeQrCode)}>
                <CloseIcon fontSize="inherit" />
            </IconButton>
        </Stack>
    )
}
