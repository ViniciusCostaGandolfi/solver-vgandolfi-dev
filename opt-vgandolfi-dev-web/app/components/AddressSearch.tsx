import { useEffect, useRef, useState } from "react";
import { ApiError, geocode } from "../lib/api";
import type { GeocodeResult } from "../lib/types";
import { IconMapPin, IconSearch } from "./icons";

interface AddressSearchProps {
  onSelect: (result: GeocodeResult) => void;
  placeholder?: string;
  onError?: (message: string) => void;
  className?: string;
  compact?: boolean;
}

/**
 * Campo de busca de endereço com autocomplete via /api/v1/geo/geocode.
 * Debounce + comprimento mínimo para respeitar o rate limit de ~30 req/min.
 */
export function AddressSearch({
  onSelect,
  placeholder = "Buscar endereço…",
  onError,
  className = "",
  compact = false,
}: AddressSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GeocodeResult[] | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const debounceRef = useRef<number | null>(null);
  const seqRef = useRef(0);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, []);

  const runSearch = (value: string) => {
    if (value.trim().length < 3) {
      setResults(null);
      setOpen(false);
      return;
    }
    const seq = ++seqRef.current;
    setLoading(true);
    setError(null);
    void geocode(value.trim())
      .then((res) => {
        if (seq !== seqRef.current) return;
        setResults(res);
        setOpen(true);
      })
      .catch((err: unknown) => {
        if (seq !== seqRef.current) return;
        const message =
          err instanceof ApiError && err.status === 429
            ? "Limite de buscas atingido. Tente novamente em instantes."
            : "Não foi possível buscar o endereço.";
        setResults(null);
        setError(message);
        setOpen(true);
        onError?.(message);
      })
      .finally(() => {
        if (seq === seqRef.current) setLoading(false);
      });
  };

  const onChange = (value: string) => {
    setQuery(value);
    if (debounceRef.current !== null) {
      window.clearTimeout(debounceRef.current);
    }
    debounceRef.current = window.setTimeout(() => runSearch(value), 400);
  };

  const pick = (r: GeocodeResult) => {
    onSelect(r);
    setQuery("");
    setResults(null);
    setOpen(false);
  };

  useEffect(() => () => {
    if (debounceRef.current !== null) window.clearTimeout(debounceRef.current);
  }, []);

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-base-content/50">
          <IconSearch width={16} height={16} />
        </span>
        <input
          type="text"
          className={`input w-full pl-9 ${compact ? "input-sm" : "input-md"}`}
          placeholder={placeholder}
          value={query}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => results && results.length > 0 && setOpen(true)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && results && results.length > 0) {
              e.preventDefault();
              pick(results[0]!);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
          autoComplete="off"
          aria-label={placeholder}
        />
        {loading && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2">
            <span className="loading loading-spinner loading-xs text-primary" />
          </span>
        )}
      </div>

      {open && results && results.length > 0 && (
        <ul className="absolute left-0 right-0 top-full z-40 mt-1.5 max-h-72 overflow-y-auto rounded-box border border-base-300 bg-base-100 p-1.5 shadow-xl shadow-black/10">
          {results.map((r, i) => (
            <li key={`${r.latitude}-${r.longitude}-${i}`}>
              <button
                type="button"
                className="flex w-full items-start gap-2.5 rounded-box px-3 py-2 text-left transition-colors hover:bg-base-200"
                onMouseDown={(e) => {
                  e.preventDefault();
                  pick(r);
                }}
              >
                <span className="mt-0.5 text-base-content/40">
                  <IconMapPin width={15} height={15} />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">
                    {r.formattedAddress}
                  </span>
                  {r.city && (
                    <span className="block text-xs text-base-content/60">
                      {[r.city, r.state, r.postalCode].filter(Boolean).join(" · ")}
                    </span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {open && error && (
        <div className="absolute left-0 right-0 top-full z-40 mt-1.5 rounded-box border border-base-300 bg-base-100 px-3 py-2 text-xs text-base-content/70 shadow-xl shadow-black/10">
          {error}
        </div>
      )}
    </div>
  );
}