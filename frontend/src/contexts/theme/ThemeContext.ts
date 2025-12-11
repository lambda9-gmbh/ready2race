import { createContext, useContext } from 'react';
import type { ThemeConfigDto } from '../../api';

export interface ThemeContextValue {
  themeConfig: ThemeConfigDto | null;
  reloadTheme: () => Promise<void>;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function useThemeConfig(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemeConfig must be used within ThemeProvider');
  }
  return context;
}

export default ThemeContext;
