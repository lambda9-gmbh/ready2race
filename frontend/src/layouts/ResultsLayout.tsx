import {Outlet} from '@tanstack/react-router'
import {Container, Box} from '@mui/material'
import {useEffect} from 'react'

const ResultsLayout = () => {
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
        </Container>
    )
}

export default ResultsLayout
