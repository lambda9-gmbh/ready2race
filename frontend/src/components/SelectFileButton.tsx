import {Button, ButtonProps} from '@mui/material'
import {Upload} from '@mui/icons-material'

type Props = ButtonProps & {accept?: string} & (
        | {
              multiple: true
              onSelected: (files: FileList) => void
          }
        | {
              multiple?: false
              onSelected: (file: File) => void
          }
    )

const SelectFileButton = ({multiple, accept, onSelected, children, ...props}: Props) => {
    return (
        <Button component={'label'} role={'undefined'} startIcon={<Upload />} {...props}>
            {children}
            <input
                type={'file'}
                multiple={multiple}
                onChange={e => {
                    const files = e.target.files
                    if (files) {
                        if (multiple) {
                            onSelected(files)
                        } else {
                            onSelected(files[0])
                        }
                    }
                }}
                style={{
                    clipPath: 'inset(50%)',
                    height: 1,
                    overflow: 'hidden',
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    whiteSpace: 'nowrap',
                    width: 1,
                }}
            />
        </Button>
    )
}

export default SelectFileButton
