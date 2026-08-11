import {Box, Typography} from '@mui/material'
import {BoardElement} from '@api/types.gen'

/**
 * Freitext-Element: ein statischer Hinweis („Siegerehrung 15 Uhr am Zelt"), gepflegt in
 * der Board-Konfiguration. Zeilenumbrüche bleiben erhalten.
 */
const BoardTextElement = ({element}: {element: BoardElement}) => (
    <Box
        sx={{
            height: '100%',
            minHeight: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            p: 'clamp(0.5rem, 1vw, 1.5rem)',
        }}>
        <Typography
            sx={{
                fontSize: 'clamp(1rem, 2.5vw, 4rem)',
                fontWeight: 700,
                textAlign: 'center',
                whiteSpace: 'pre-wrap',
            }}>
            {element.text ?? ''}
        </Typography>
    </Box>
)

export default BoardTextElement
