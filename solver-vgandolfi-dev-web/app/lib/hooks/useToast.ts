import { useRef, useState } from "react";

export type ToastKind = "success" | "error" | "info";

export interface ToastState {
  msg: string;
  kind: ToastKind;
}

export interface UseToast {
  toast: ToastState | null;
  showToast: (msg: string, kind: ToastKind) => void;
}

const TOAST_DURATION_MS = 4200;

/** Estado e controle do toast global da página. */
export function useToast(): UseToast {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timerRef = useRef<number | null>(null);

  const showToast = (msg: string, kind: ToastKind) => {
    setToast({ msg, kind });
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => setToast(null), TOAST_DURATION_MS);
  };

  return { toast, showToast };
}
