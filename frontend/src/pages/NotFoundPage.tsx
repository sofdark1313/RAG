import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <main className="empty-state">
      <div className="state-card">
        <h1>页面不存在</h1>
        <p>你访问的地址无效，请返回聊天页面。</p>
        <Button asChild className="mt-6 rounded-lg shadow-none">
          <Link to="/chat">返回聊天</Link>
        </Button>
      </div>
    </main>
  );
}
