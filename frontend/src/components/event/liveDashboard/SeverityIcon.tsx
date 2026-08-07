import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import {EffectiveSeverity} from '@api/types.gen.ts'

/**
 * Draußen zählt Kontrast: die dunklen Palette-Varianten bleiben auch bei Sonne lesbar, während die
 * konfigurierten main-Töne verblassen.
 */
const SeverityIcon = ({severity, size = 28}: {severity: EffectiveSeverity; size?: number}) => {
    const sx = {fontSize: size, display: 'block'}
    switch (severity) {
        case 'OK':
            return <CheckCircleIcon sx={{...sx, color: 'success.dark'}} />
        case 'WARNING':
            return <WarningAmberIcon sx={{...sx, color: 'warning.dark'}} />
        case 'CRITICAL':
            return <CancelIcon sx={{...sx, color: 'error.dark'}} />
        case 'NEUTRAL':
            return <RadioButtonUncheckedIcon sx={{...sx, color: 'text.disabled'}} />
    }
}

export default SeverityIcon
