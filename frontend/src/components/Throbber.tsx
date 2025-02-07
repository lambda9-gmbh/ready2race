import {Box, CircularProgress, CircularProgressProps} from "@mui/material";

type Props = CircularProgressProps
const Throbber = ({...rest}: Props) => {
    const throbberSize = rest.size ?? 24
    return (
        <Box sx={{height: 1, display: 'flex'}}>
            <CircularProgress size={throbberSize} sx={{m: 'auto'}}/>
        </Box>
    )
}

export default Throbber