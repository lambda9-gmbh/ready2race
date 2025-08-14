import {Outlet} from '@tanstack/react-router'
import {Container, Box} from '@mui/material'
import {AppSessionProvider} from '@contexts/app/AppSessionContext.tsx'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'

const AppLayout = () => {
    return (
        <Container
            maxWidth="lg"
            sx={{
                minHeight: '100vh',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                py: {xs: 2, sm: 4},
                px: {xs: 2, sm: 3},
            }}>
            <Box sx={{width: '100%', display: 'flex', justifyContent: 'end'}}>
                <LanguageWidget />
            </Box>
            <Box
                component="main"
                sx={{
                    width: '100%',
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                }}>
                <AppSessionProvider>
                    <Outlet />
                </AppSessionProvider>
            </Box>
        </Container>
    )
}

export default AppLayout
