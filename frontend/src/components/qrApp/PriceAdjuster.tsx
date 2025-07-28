import {Box, Button} from "@mui/material";

export const PriceAdjuster = ({price, onPriceChange}: {
    price: string;
    onPriceChange: (price: string) => void;
}) => {
    const adjustPrice = (amount: number) => {
        const currentPrice = parseFloat(price || '0');
        const newPrice = Math.max(0, currentPrice + amount);
        onPriceChange(newPrice.toFixed(2));
    };

    return (
        <Box sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(5, minmax(0, 1fr))',
            gap: 0.5,
            width: '100%',
            maxWidth: 300,
            margin: '0 auto'
        }}>
            <Button
                variant="outlined"
                onClick={() => onPriceChange('0')}
                sx={{
                    fontSize: '0.875rem',
                    py: 0.5,
                    px: 1,
                    minWidth: 0,
                }}
                color="secondary"
                size="small"
            >
                C
            </Button>
            <Button
                variant="outlined"
                onClick={() => adjustPrice(0.1)}
                sx={{
                    fontSize: '0.875rem',
                    py: 0.5,
                    px: 1,
                    minWidth: 0,
                }}
                size="small"
            >
                +0.1
            </Button>
            <Button
                variant="outlined"
                onClick={() => adjustPrice(0.2)}
                sx={{
                    fontSize: '0.875rem',
                    py: 0.5,
                    px: 1,
                    minWidth: 0,
                }}
                size="small"
            >
                +0.2
            </Button>
            <Button
                variant="outlined"
                onClick={() => adjustPrice(0.5)}
                sx={{
                    fontSize: '0.875rem',
                    py: 0.5,
                    px: 1,
                    minWidth: 0,
                }}
                size="small"
            >
                +0.5
            </Button>
            <Button
                variant="outlined"
                onClick={() => adjustPrice(1)}
                sx={{
                    fontSize: '0.875rem',
                    py: 0.5,
                    px: 1,
                    minWidth: 0,
                }}
                size="small"
            >
                +1
            </Button>
        </Box>
    );
};