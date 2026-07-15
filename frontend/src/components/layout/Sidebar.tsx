import * as React from "react";
import { differenceInCalendarDays, isValid } from "date-fns";
import {
  Bot,
  LogOut,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Settings,
  Trash2
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import { Loading } from "@/components/common/Loading";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const {
    sessions,
    currentSessionId,
    isLoading,
    sessionsLoaded,
    createSession,
    deleteSession,
    renameSession,
    selectSession,
    fetchSessions
  } = useChatStore();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [query, setQuery] = React.useState("");
  const [renamingId, setRenamingId] = React.useState<string | null>(null);
  const [renameValue, setRenameValue] = React.useState("");
  const [deleteTarget, setDeleteTarget] = React.useState<{ id: string; title: string } | null>(null);
  const [avatarFailed, setAvatarFailed] = React.useState(false);
  const renameInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (sessions.length === 0) fetchSessions().catch(() => null);
  }, [fetchSessions, sessions.length]);

  React.useEffect(() => {
    if (renamingId) {
      renameInputRef.current?.focus();
      renameInputRef.current?.select();
    }
  }, [renamingId]);

  React.useEffect(() => setAvatarFailed(false), [user?.avatar, user?.userId]);

  const filteredSessions = React.useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return sessions;
    return sessions.filter((session) =>
      `${session.title || "新对话"} ${session.id}`.toLowerCase().includes(keyword)
    );
  }, [query, sessions]);

  const groupedSessions = React.useMemo(() => {
    const now = new Date();
    const groups = new Map<string, typeof filteredSessions>();
    const order: string[] = [];
    const resolveLabel = (value?: string) => {
      const parsed = value ? new Date(value) : now;
      const date = isValid(parsed) ? parsed : now;
      const diff = Math.max(0, differenceInCalendarDays(now, date));
      if (diff === 0) return "今天";
      if (diff <= 7) return "7 天内";
      if (diff <= 30) return "30 天内";
      return "更早";
    };

    filteredSessions.forEach((session) => {
      const label = resolveLabel(session.lastTime);
      if (!groups.has(label)) {
        groups.set(label, []);
        order.push(label);
      }
      groups.get(label)?.push(session);
    });
    return order.map((label) => ({ label, items: groups.get(label) || [] }));
  }, [filteredSessions]);

  const avatarUrl = user?.avatar?.trim();
  const showAvatar = Boolean(avatarUrl) && !avatarFailed;
  const avatarFallback = (user?.username || user?.userId || "用户").slice(0, 1).toUpperCase();

  const handleCreateSession = () => {
    createSession().catch(() => null);
    navigate("/chat");
    onClose();
  };

  const startRename = (id: string, title: string) => {
    setRenamingId(id);
    setRenameValue(title || "新对话");
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameValue("");
  };

  const commitRename = async () => {
    if (!renamingId) return;
    const nextTitle = renameValue.trim();
    const currentTitle = sessions.find((session) => session.id === renamingId)?.title || "新对话";
    if (!nextTitle || nextTitle === currentTitle) {
      cancelRename();
      return;
    }
    await renameSession(renamingId, nextTitle);
    cancelRename();
  };

  const selectConversation = (id: string) => {
    if (renamingId === id) return;
    if (renamingId) cancelRename();
    selectSession(id).catch(() => null);
    navigate(`/chat/${id}`);
    onClose();
  };

  return (
    <>
      <button
        type="button"
        aria-label="关闭会话列表"
        className={cn("chat-sidebar__overlay", isOpen && "is-visible")}
        onClick={onClose}
      />
      <aside className={cn("chat-sidebar", isOpen && "chat-sidebar--open")} aria-label="会话列表">
        <div className="chat-sidebar__brand">
          <span className="chat-sidebar__logo"><Bot className="h-4 w-4" /></span>
          <span>Ragent</span>
        </div>

        <button type="button" className="chat-sidebar__new" onClick={handleCreateSession}>
          <Plus className="h-4 w-4" />
          新对话
        </button>

        <label className="chat-sidebar__search">
          <Search className="h-4 w-4" aria-hidden="true" />
          <span className="sr-only">搜索对话</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索对话" />
        </label>

        <div className="chat-sidebar__sessions sidebar-scroll">
          {sessions.length === 0 && (!sessionsLoaded || isLoading) ? (
            <Loading label="加载会话中" />
          ) : filteredSessions.length === 0 ? (
            <div className="chat-sidebar__empty">
              <MessageSquare className="h-5 w-5" />
              <span>暂无对话</span>
            </div>
          ) : (
            groupedSessions.map((group) => (
              <section key={group.label} className="chat-session-group">
                <p>{group.label}</p>
                {group.items.map((session) => (
                  <div
                    key={session.id}
                    className={cn("chat-session", currentSessionId === session.id && "chat-session--active")}
                    role="button"
                    tabIndex={0}
                    onClick={() => selectConversation(session.id)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        selectConversation(session.id);
                      }
                    }}
                  >
                    {renamingId === session.id ? (
                      <input
                        ref={renameInputRef}
                        value={renameValue}
                        onChange={(event) => setRenameValue(event.target.value)}
                        onClick={(event) => event.stopPropagation()}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            event.preventDefault();
                            commitRename().catch(() => null);
                          }
                          if (event.key === "Escape") {
                            event.preventDefault();
                            cancelRename();
                          }
                        }}
                        onBlur={() => commitRename().catch(() => null)}
                        className="chat-session__rename"
                        aria-label="会话标题"
                      />
                    ) : (
                      <span className="chat-session__title">{session.title || "新对话"}</span>
                    )}
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <button
                          type="button"
                          className="chat-session__menu"
                          onClick={(event) => event.stopPropagation()}
                          aria-label="会话操作"
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="start">
                        <DropdownMenuItem onSelect={() => startRename(session.id, session.title || "新对话")}>
                          <Pencil className="mr-2 h-4 w-4" />重命名
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          className="text-destructive focus:text-destructive"
                          onSelect={() => setDeleteTarget({ id: session.id, title: session.title || "新对话" })}
                        >
                          <Trash2 className="mr-2 h-4 w-4" />删除
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                ))}
              </section>
            ))
          )}
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" className="chat-sidebar__account" aria-label="用户菜单">
              <span className="chat-sidebar__avatar">
                {showAvatar ? (
                  <img src={avatarUrl} alt="" onError={() => setAvatarFailed(true)} />
                ) : (
                  avatarFallback
                )}
              </span>
              <span className="chat-sidebar__username">
                {/^\d+$/.test(user?.username || user?.userId || "") ? "用户" : user?.username || user?.userId || "用户"}
              </span>
              <MoreHorizontal className="h-4 w-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" side="top" sideOffset={8} className="w-48">
            {user?.role === "admin" ? (
              <DropdownMenuItem onSelect={() => window.open("/admin", "_blank")}>
                <Settings className="mr-2 h-4 w-4" />管理后台
              </DropdownMenuItem>
            ) : null}
            <DropdownMenuItem onSelect={() => logout()} className="text-destructive focus:text-destructive">
              <LogOut className="mr-2 h-4 w-4" />退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </aside>

      <AlertDialog open={Boolean(deleteTarget)} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除该会话？</AlertDialogTitle>
            <AlertDialogDescription>
              “{deleteTarget?.title || "该会话"}”将被永久删除，无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!deleteTarget) return;
                const target = deleteTarget;
                const isCurrent = currentSessionId === target.id;
                setDeleteTarget(null);
                deleteSession(target.id)
                  .then(() => isCurrent && navigate("/chat"))
                  .catch(() => null);
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
