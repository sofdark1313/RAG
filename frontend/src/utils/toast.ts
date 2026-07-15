export type ToastType = "success" | "error" | "info";

export interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
}

type ToastHandler = (message: ToastMessage) => void;

const target = new EventTarget();
let nextToastId = 1;

export function pushToast(type: ToastType, message: string) {
  const toast: ToastMessage = {
    id: nextToastId++,
    type,
    message
  };
  target.dispatchEvent(new CustomEvent<ToastMessage>("toast", { detail: toast }));
}

export function subscribeToast(handler: ToastHandler) {
  const listener = (event: Event) => {
    handler((event as CustomEvent<ToastMessage>).detail);
  };
  target.addEventListener("toast", listener);
  return () => target.removeEventListener("toast", listener);
}

export const toast = {
  success: (message: string) => pushToast("success", message),
  error: (message: string) => pushToast("error", message),
  info: (message: string) => pushToast("info", message)
};
