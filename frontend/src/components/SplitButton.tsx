import * as React from 'react'
import {ReactNode} from 'react'
import Button from '@mui/material/Button'
import ButtonGroup from '@mui/material/ButtonGroup'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import ClickAwayListener from '@mui/material/ClickAwayListener'
import Grow from '@mui/material/Grow'
import Paper from '@mui/material/Paper'
import Popper from '@mui/material/Popper'
import MenuItem from '@mui/material/MenuItem'
import MenuList from '@mui/material/MenuList'
import {Typography} from '@mui/material'

export type SplitButtonOption = {
    icon?: ReactNode
    label: string
    onClick: () => void
}

const SplitButton = (props: {main: SplitButtonOption; options: SplitButtonOption[]}) => {
    const [open, setOpen] = React.useState(false)
    const anchorRef = React.useRef<HTMLDivElement>(null)

    const handleMenuItemClick = () => {
        setOpen(false)
    }

    const handleToggle = () => {
        setOpen(prevOpen => !prevOpen)
    }

    const handleClose = (event: Event) => {
        if (anchorRef.current && anchorRef.current.contains(event.target as HTMLElement)) {
            return
        }

        setOpen(false)
    }

    return (
        <React.Fragment>
            <ButtonGroup variant="outlined" ref={anchorRef}>
                <Button startIcon={props.main.icon} onClick={props.main.onClick}>
                    {props.main.label}
                </Button>
                <Button
                    size="small"
                    aria-controls={open ? 'split-button-menu' : undefined}
                    aria-expanded={open ? 'true' : undefined}
                    aria-haspopup="menu"
                    onClick={handleToggle}>
                    <ArrowDropDownIcon />
                </Button>
            </ButtonGroup>
            <Popper
                sx={{zIndex: 1}}
                open={open}
                anchorEl={anchorRef.current}
                role={undefined}
                transition
                placement="bottom-end"
                disablePortal>
                {({TransitionProps}) => (
                    <Grow
                        {...TransitionProps}
                        style={{
                            transformOrigin: 'center top',
                        }}>
                        <Paper>
                            <ClickAwayListener onClickAway={handleClose}>
                                <MenuList id="split-button-menu" autoFocusItem>
                                    {props.options.map((option, index) => (
                                        <MenuItem
                                            key={`option-${index}`}
                                            onClick={() => {
                                                handleMenuItemClick()
                                                option.onClick()
                                            }}>
                                            <Typography variant={'subtitle2'} color={'primary'}>
                                                {option.label}
                                            </Typography>
                                        </MenuItem>
                                    ))}
                                </MenuList>
                            </ClickAwayListener>
                        </Paper>
                    </Grow>
                )}
            </Popper>
        </React.Fragment>
    )
}
export default SplitButton
