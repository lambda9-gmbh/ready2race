import {Trans} from 'react-i18next'
import {Alert, AlertTitle, Container} from '@mui/material'

const PanicPage = () => {
    return (
        <Container maxWidth={'sm'} sx={{pt: 8}}>
            <Alert variant={'outlined'} severity={'error'}>
                <AlertTitle>
                    <Trans i18nKey={'panic.title'} />
                </AlertTitle>
                <Trans i18nKey={'panic.text'} />
            </Alert>
        </Container>
    )
}

export default PanicPage
