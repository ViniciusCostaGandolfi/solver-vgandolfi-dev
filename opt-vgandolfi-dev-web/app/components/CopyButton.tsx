import { useRef, useState } from "react";
import { copyToClipboard } from "../lib/format";
import { IconCheck, IconCopy } from "./icons";

interface CopyButtonProps {
  text: string;
  className?: string;
  label?: string;
}

/** Botão de copiar com feedback visual de "copiado". */
export function CopyButton({ text, className = "", label }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<number | null>(null);

  const onCopy = async () => {
    const ok = await copyToClipboard(text);
    if (!ok) return;
    setCopied(true);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => setCopied(false), 1600);
  };

  return (
    <button
      type="button"
      className={`btn btn-ghost btn-sm ${copied ? "text-success" : ""} ${className}`}
      onClick={onCopy}
      aria-label={label ?? "Copiar"}
      title={copied ? "Copiado!" : "Copiar"}
    >
      {copied ? (
        <IconCheck width={16} height={16} />
      ) : (
        <IconCopy width={16} height={16} />
      )}
      {label && <span className="text-xs">{copied ? "Copiado" : label}</span>}
    </button>
  );
}