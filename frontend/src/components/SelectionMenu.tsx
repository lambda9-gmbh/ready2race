import {
    Box,
    List,
    ListItem,
    ListItemText,
    Menu,
    MenuItem,
    PopoverOrigin,
    Tooltip,
} from '@mui/material'
import {MouseEvent, ReactNode, useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'

type Anchor = {
    button: PopoverOrigin
    menu: PopoverOrigin
}

type Props = {
    buttonContent?: ReactNode
    items?: {id: string; label: string; problems?: string[]}[]
    keyLabel: string
    onSelectItem: (id: string) => Promise<void>
    pending?: boolean
    anchor?: Anchor
}
const SelectionMenu = ({items, keyLabel, anchor, ...props}: Props) => {
    const [menuAnchorEl, setMenuAnchorEl] = useState<HTMLElement | null>(null)
    const menuOpen = Boolean(menuAnchorEl)
    const handleMenuClick = (event: MouseEvent<HTMLButtonElement>) => {
        setMenuAnchorEl(event.currentTarget)
    }
    const handleMenuClose = () => {
        setMenuAnchorEl(null)
    }

    return (
        <>
            <LoadingButton
                id={`${keyLabel}-button`}
                variant="outlined"
                aria-controls={menuOpen ? `id="${keyLabel}-menu"` : undefined}
                aria-haspopup={'true'}
                aria-expanded={menuOpen ? 'true' : undefined}
                onClick={handleMenuClick}
                pending={props.pending ?? false}>
                {props.buttonContent}
            </LoadingButton>
            <Menu
                id={`${keyLabel}-menu`}
                anchorEl={menuAnchorEl}
                open={menuOpen}
                onClose={handleMenuClose}
                disableScrollLock={true}
                MenuListProps={{
                    'aria-labelledby': `${keyLabel}-button`,
                }}
                anchorOrigin={anchor?.button}
                transformOrigin={anchor?.menu}>
                {items?.map((item, idx) => (
                    <Tooltip
                        key={item.id}
                        title={
                            item.problems &&
                            item.problems.length > 0 && (
                                <List>
                                    {item.problems.map(p => (
                                        <ListItem key={p}>
                                            <ListItemText primary={p} />
                                        </ListItem>
                                    ))}
                                </List>
                            )
                        }>
                        <Box>
                            <MenuItem
                                key={idx + item.id}
                                disabled={(item.problems?.length ?? 0) > 0}
                                onClick={() => {
                                    void props.onSelectItem(item.id)
                                    handleMenuClose()
                                }}
                                value={item.id}>
                                {item.label}
                            </MenuItem>
                        </Box>
                    </Tooltip>
                ))}
            </Menu>
        </>
    )
}
export default SelectionMenu
