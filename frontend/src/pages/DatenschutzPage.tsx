import {Container, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useDocumentTitle} from '@utils/useDocumentTitle.ts'

/**
 * Öffentliche Datenschutzerklärung. Der Fließtext bleibt bewusst deutsch und hart kodiert:
 * Er ist die rechtsverbindliche Fassung und soll sich nicht je Sprachwahl unterscheiden —
 * nur Seitentitel und Verweise laufen über i18n. Die Seite ist so geschnitten, dass weitere
 * Abschnitte (Verantwortlicher, Weitergabe an die Zeitnahme, Veröffentlichung von
 * Ergebnissen, Betroffenenrechte, …) als zusätzliche <section>-Blöcke davor oder danach
 * ergänzt werden können.
 */
const DatenschutzPage = () => {
    const {t} = useTranslation()
    useDocumentTitle(t('legal.privacyTitle'))

    return (
        <Container maxWidth="md" sx={{py: {xs: 3, sm: 6}}}>
            <Stack spacing={3}>
                <Typography variant="h1">{t('legal.privacyTitle')}</Typography>

                {/* Abschnitt: Cookies und lokale Speicherung (Wortlaut vom 13.08.2026) */}
                <section>
                    <Typography variant="h2" gutterBottom>
                        Cookies und lokale Speicherung im Browser
                    </Typography>
                    <Stack spacing={2}>
                        <Typography>
                            Diese Anwendung verwendet <strong>keine Cookies oder vergleichbare
                            Techniken zu Werbe-, Tracking- oder Analysezwecken</strong> und bindet
                            keine Dienste von Drittanbietern ein, die solche Techniken einsetzen.
                        </Typography>
                        <Typography>
                            Zur Bereitstellung der von Ihnen angeforderten Funktionen speichert die
                            Anwendung technisch erforderliche Informationen im lokalen Speicher
                            Ihres Browsers (localStorage/sessionStorage) sowie im
                            Anwendungs-Cache. Diese Speicherung ist nach § 25 Abs. 2 Nr. 2 TDDDG
                            einwilligungsfrei, da sie unbedingt erforderlich ist, um den von Ihnen
                            ausdrücklich gewünschten Dienst zur Verfügung zu stellen. Ein
                            Einwilligungsbanner ist daher nicht erforderlich. Im Einzelnen:
                        </Typography>

                        <Typography variant="h3">
                            Anmeldung (nur bei aktiver Nutzung eines Kontos)
                        </Typography>
                        <Typography>
                            <em>Sitzungs-Token</em> („session.admin“ bzw. „session.app“): hält Ihre
                            Anmeldung aufrecht. Der Token verfällt serverseitig spätestens 6 Stunden
                            nach der letzten Aktivität und wird beim Abmelden gelöscht. In der
                            Helfer-App wird zusätzlich Ihre Berechtigungsstufe gespeichert
                            („session.user“), damit die App nach einem Neustart ohne Netzverbindung
                            funktionsfähig bleibt.
                        </Typography>

                        <Typography variant="h3">
                            Persönliches Event-Dashboard „Mein Event“ (nur nach Scan Ihres
                            Teilnehmerbands)
                        </Typography>
                        <Typography>
                            <em>Zugangscode des Teilnehmerbands</em> („my_event_codes“): wird
                            ausschließlich auf Ihrem Gerät gespeichert, wenn Sie den QR-Code Ihres
                            Bands scannen, damit Ihr persönlicher Zeitplan beim nächsten Aufruf ohne
                            erneuten Scan erscheint. Sie können den Eintrag jederzeit in der Ansicht
                            selbst entfernen.
                        </Typography>

                        <Typography variant="h3">
                            Anzeige-Einstellungen (gerätebezogen, ohne Personenbezug)
                        </Typography>
                        <Typography>
                            Darstellungs- und Filtereinstellungen, z.&nbsp;B. Kurz-/Langform von
                            Wettkampfnamen, Filter und Schriftgröße des Schiedsrichter-Dashboards,
                            Kameraauswahl des QR-Scanners, Fensterverhalten des Zeitplans sowie das
                            Ausblenden einmal geschlossener Hinweise. Diese Werte enthalten keine
                            personenbezogenen Daten und verbleiben ausschließlich auf Ihrem Gerät.
                        </Typography>

                        <Typography variant="h3">Offline-Fähigkeit der Helfer-App</Typography>
                        <Typography>
                            Die als App installierbare Helferansicht (PWA) legt über einen Service
                            Worker Programmdateien und – nur auf dafür eingerichteten Geräten – den
                            zuletzt geladenen Datenstand im Browser-Cache ab, damit die Anzeige bei
                            kurzzeitigem Verbindungsverlust am Veranstaltungsgelände nutzbar bleibt.
                        </Typography>

                        <Divider />

                        <Typography>
                            Alle genannten Informationen werden ausschließlich durch diese Anwendung
                            selbst („First Party“) gespeichert und gelesen; eine Weitergabe an
                            Dritte findet nicht statt. Sie können die gespeicherten Daten jederzeit
                            über die Einstellungen Ihres Browsers einsehen und löschen; die
                            Anwendung bleibt danach nutzbar, gegebenenfalls ist eine erneute
                            Anmeldung bzw. ein erneuter Scan erforderlich.
                        </Typography>
                    </Stack>
                </section>
            </Stack>
        </Container>
    )
}

export default DatenschutzPage
