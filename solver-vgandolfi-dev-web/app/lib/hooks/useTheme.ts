import { useEffect, useState } from "react";

export type Theme = "light" | "dark";

export interface UseTheme {
  theme: Theme;
  toggleTheme: (checked: boolean) => void;
}

/**
 * Estado do tema claro/escuro, sincronizado com o atributo data-theme do <html>
 * (inicializado em root.tsx por um script inline) e persistido em localStorage.
 */
export function useTheme(): UseTheme {
  const [theme, setTheme] = useState<Theme>("light");

  useEffect(() => {
    const d = document.documentElement.dataset.theme;
    setTheme(d === "optdark" ? "dark" : "light");
  }, []);

  const toggleTheme = (checked: boolean) => {
    const next: Theme = checked ? "dark" : "light";
    setTheme(next);
    document.documentElement.dataset.theme = next === "dark" ? "optdark" : "optlight";
    try {
      localStorage.setItem("solver:theme", next);
    } catch {
      /* storage indisponível */
    }
  };

  return { theme, toggleTheme };
}
