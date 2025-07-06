import React from 'react';
import {Outlet} from "@tanstack/react-router";

const AppLayout: React.FC = () => {
    return (
        <div className="app-layout">
            <header>
                <h1>App Bereich</h1>
            </header>
            <main>
                <Outlet />
            </main>
        </div>
    );
};

export default AppLayout; 