import * as React from "react";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";

interface MainLayoutProps {
  children: React.ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);

  return (
    <div className="chat-shell">
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="chat-shell__main">
        <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
        <main className="chat-shell__content">{children}</main>
      </div>
    </div>
  );
}
