import React from 'react';
import {Outlet} from "@tanstack/react-router";
import {Container, Box} from "@mui/material";

const AppLayout: React.FC = () => {
    return (
        <Container 
            maxWidth="lg"
            sx={{
                minHeight: '100vh',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                py: { xs: 2, sm: 4 },
                px: { xs: 2, sm: 3 }
            }}
        >
            <Box 
                component="main"
                sx={{
                    width: '100%',
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column'
                }}
            >
                <Outlet />
            </Box>
        </Container>
    );
};

export default AppLayout; 