import React, { useState, useEffect, useMemo, useCallback } from 'react';
import ThemeContext from './ThemeContext';
import type { ThemeConfigDto } from '../../api';

const DEFAULT_THEME: ThemeConfigDto = {
  version: '1.0',
  primaryColor: '#4d9f85',
  textColor: '#1d1d1d',
  backgroundColor: '#ffffff',
  customFont: {
    enabled: false,
    filename: null,
  },
};

interface ThemeProviderProps {
  children: React.ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const [themeConfig, setThemeConfig] = useState<ThemeConfigDto | null>(null);

  const loadTheme = useCallback(async () => {
    try {
      const response = await fetch('/static/theme.json');
      if (!response.ok) {
        console.warn('Failed to load theme, using defaults');
        setThemeConfig(DEFAULT_THEME);
        return;
      }
      const theme = await response.json();
      setThemeConfig(theme);

      // Inject custom font if enabled
      if (theme.customFont?.enabled && theme.customFont?.filename) {
        const fontFace = `
          @font-face {
            font-family: 'CustomFont';
            src: url('/static/fonts/${theme.customFont.filename}') format('woff2'),
                 url('/static/fonts/${theme.customFont.filename}') format('woff');
            font-weight: normal;
            font-style: normal;
          }
        `;

        // Remove existing custom font style if any
        const existingStyle = document.getElementById('custom-font-style');
        if (existingStyle) {
          existingStyle.remove();
        }

        // Inject new custom font style
        const styleElement = document.createElement('style');
        styleElement.id = 'custom-font-style';
        styleElement.textContent = fontFace;
        document.head.appendChild(styleElement);
      } else {
        // Remove custom font style if custom font is disabled
        const existingStyle = document.getElementById('custom-font-style');
        if (existingStyle) {
          existingStyle.remove();
        }
      }
    } catch (error) {
      console.error('Error loading theme:', error);
      setThemeConfig(DEFAULT_THEME);
    }
  }, []);

  useEffect(() => {
    loadTheme();
  }, [loadTheme]);

  const contextValue = useMemo(
    () => ({
      themeConfig,
      reloadTheme: loadTheme,
    }),
    [themeConfig, loadTheme]
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      {children}
    </ThemeContext.Provider>
  );
}
