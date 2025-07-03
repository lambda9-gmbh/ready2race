import {Menu, MenuItem} from '@mui/material'
import {MouseEvent, ReactNode, useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'

type Props = {
    buttonContent?: ReactNode
    items?: {id: string; label: string}[]
    keyLabel: string
    onSelectItem: (id: string) => Promise<void>
    pending?: boolean
}
const SelectionMenu = ({items, keyLabel, ...props}: Props) => {
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
                }}>
                {items?.map((item, idx) => (
                    <MenuItem
                        key={idx + item.id}
                        onClick={() => {
                            void props.onSelectItem(item.id)
                            handleMenuClose()
                        }}
                        value={item.id}>
                        {item.label}
                    </MenuItem>
                ))}
            </Menu>
        </>
    )
}
export default SelectionMenu
