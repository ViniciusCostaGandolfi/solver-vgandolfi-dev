import { CopyButton } from "./CopyButton";

interface CodeBlockProps {
  code: string;
  title?: string;
  description?: string;
}

/**
 * Bloco de código com estética de editor (mockup-code) e botão de copiar.
 * A primeira linha recebe o prefixo "$", as seguintes ">".
 */
export function CodeBlock({ code, title, description }: CodeBlockProps) {
  const lines = code.split("\n").filter((l) => l.length > 0);

  return (
    <div className="min-w-0">
      {(title || description) && (
        <div className="mb-2 flex items-start justify-between gap-3">
          <div>
            {title && (
              <h4 className="font-display text-sm font-semibold">{title}</h4>
            )}
            {description && (
              <p className="mt-0.5 text-xs text-base-content/60">
                {description}
              </p>
            )}
          </div>
        </div>
      )}
      <div className="mockup-code relative overflow-x-auto pr-14">
        <div className="absolute right-2 top-2 z-10">
          <CopyButton
            text={code}
            className="btn-ghost text-neutral-content/80 hover:bg-neutral-content/10 hover:text-neutral-content"
          />
        </div>
        {lines.map((line, i) => (
          <pre key={i} data-prefix={i === 0 ? "$" : ">"}>
            <code>{line}</code>
          </pre>
        ))}
      </div>
    </div>
  );
}