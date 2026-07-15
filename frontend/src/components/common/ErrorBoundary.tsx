import * as React from "react";

import { Button } from "@/components/ui/button";

interface ErrorBoundaryState {
  hasError: boolean;
  message?: string;
}

export class ErrorBoundary extends React.Component<React.PropsWithChildren, ErrorBoundaryState> {
  constructor(props: React.PropsWithChildren) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, message: error.message };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("App error", error, info);
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <main className="error-state">
        <div className="state-card">
          <h1>页面暂时无法显示</h1>
          <p>{this.state.message || "页面运行时发生错误。"}</p>
          <Button className="mt-6 rounded-lg shadow-none" onClick={this.handleReload}>
            刷新
          </Button>
        </div>
      </main>
    );
  }
}
