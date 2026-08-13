import {Outlet} from '@tanstack/react-router'
import {Container, Box, Link as MuiLink} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'
import {useEffect} from 'react'

const ResultsLayout = () => {
    const {t} = useTranslation()
    // Die Ergebnisseite trägt Namen von Teilnehmenden und über "Mein Event" den Zustand
    // persönlicher Bedingungen. Ein Suchmaschinentreffer würde aus "wer den Link hat"
    // ein "wer den Namen sucht" machen. Der Schutz hängt bewusst am Layout und nicht an
    // der Reiter-Logik, damit er beim Umbau der Reiter nicht verloren geht.
    useEffect(() => {
        const meta = document.createElement('meta')
        meta.name = 'robots'
        meta.content = 'noindex, nofollow'
        document.head.appendChild(meta)
        return () => {
            // remove() statt head.removeChild(): wirkungsgleich, wirft aber nicht, falls der
            // Knoten von außen (Erweiterung, Hot Reload) schon entfernt wurde.
            meta.remove()
        }
    }, [])

    return (
        <Container
            className="mobile-optimized-layout"
            maxWidth="lg"
            sx={{
                minHeight: '100vh',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                py: {xs: 1, sm: 4},
                px: {xs: 2, sm: 3},
            }}>
            <Box
                component="main"
                sx={{
                    width: '100%',
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                }}>
                <Outlet />
            </Box>
            {/* Dezenter Fußbereich: die Datenschutzerklärung muss von der öffentlichen
                Seite aus erreichbar sein (Informationspflicht), ohne die Anzeige zu stören. */}
            <Box component="footer" sx={{py: 2}}>
                <MuiLink
                    component={Link}
                    to="/datenschutz"
                    variant="caption"
                    color="text.secondary"
                    underline="hover">
                    {t('legal.privacy')}
                </MuiLink>
            </Box>
        </Container>
    )
}

export default ResultsLayout
