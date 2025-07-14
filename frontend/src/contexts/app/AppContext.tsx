import React, { createContext, useContext, useState, PropsWithChildren } from 'react';
import { Privilege } from '@api/types.gen';

export type AppFunction = 'APP_QR_MANAGEMENT' | 'APP_COMPETITION_CHECK' | 'APP_EVENT_REQUIREMENT' | null;

interface AppContextType {
  appFunction: AppFunction;
  setAppFunction: (fn: AppFunction) => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const AppProvider: React.FC<PropsWithChildren> = ({ children }) => {
  const [appFunction, setAppFunction] = useState<AppFunction>(null);

  return (
    <AppContext.Provider value={{ appFunction, setAppFunction }}>
      {children}
    </AppContext.Provider>
  );
};

export const useApp = (): AppContextType => {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('AppContext not found');
  return ctx;
}; 