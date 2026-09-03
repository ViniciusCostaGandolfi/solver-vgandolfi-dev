import { useEffect, useState } from "react";
import { apiHealthUrl } from "../lib/format";
import { IconExternal, IconMoon, IconRoute, IconSun } from "./icons";

function HealthDot() {
  const [state, setState] = useState<"checking" | "ok" | "down">("checking");

  useEffect(() => {
    let active = true;
    const check = async () => {
      try {
        const res = await fetch(apiHealthUrl());
        if (!active) return;
        setState(res.ok ? "ok" : "down");
      } catch {
        if (active) setState("down");
      }
    };
    void check();
    const timer = window.setInterval(() => void check(), 30000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const tip =
    state === "ok"
      ? "API online"
      : state === "down"
        ? "API indisponível"
        : "Verificando API…";

  return (
    <a
      href={apiHealthUrl()}
      className="tooltip tooltip-bottom flex items-center gap-2 rounded-field px-2 py-1.5 text-xs text-base-content/60 transition-colors hover:bg-base-200 hover:text-base-content"
      data-tip={tip}
      aria-label="Verificar saúde da API"
    >
      <span
        className={`status status-xs ${
          state === "ok"
            ? "status-success"
            : state === "down"
              ? "status-error"
              : "status-warning"
        }`}
      />
      <span className="hidden font-mono sm:inline">/health</span>
    </a>
  );
}

interface NavbarProps {
  theme: "light" | "dark";
  onToggleTheme: (checked: boolean) => void;
}

export function Navbar({ theme, onToggleTheme }: NavbarProps) {
  return (
    <div className="navbar sticky top-0 z-50 border-b border-base-300/60 bg-base-100/80 backdrop-blur-md">
      <div className="navbar-start">
        <a href="#top" className="flex items-center gap-2.5 px-2">
          <span className="grid h-9 w-9 place-items-center rounded-box bg-primary text-primary-content shadow-sm">
            <IconRoute width={19} height={19} />
          </span>
          <span className="font-display text-xl font-bold tracking-tight">opt</span>
          <span className="badge badge-primary badge-soft badge-sm">beta</span>
        </a>
      </div>

      <div className="navbar-center hidden lg:flex">
        <ul className="menu menu-horizontal menu-sm gap-1 px-1">
          <li>
            <a href="#como-funciona">Como funciona</a>
          </li>
          <li>
            <a href="#criar">Ferramenta</a>
          </li>
          <li>
            <a href="#api">
              API
              <IconExternal width={13} height={13} />
            </a>
          </li>
        </ul>
      </div>

      <div className="navbar-end gap-1">
        <HealthDot />
        <div className="hidden sm:block">
          <a href="#api" className="btn btn-ghost btn-sm">
            Docs da API
          </a>
        </div>
        <label
          className="swap swap-rotate grid h-9 w-9 cursor-pointer place-items-center rounded-full text-base-content/70 transition-colors hover:bg-base-200 hover:text-base-content"
          aria-label="Alternar tema claro/escuro"
          title={theme === "dark" ? "Usar tema claro" : "Usar tema escuro"}
        >
          <input
            type="checkbox"
            className="theme-controller"
            value="optdark"
            checked={theme === "dark"}
            onChange={(e) => onToggleTheme(e.target.checked)}
          />
          <IconSun className="swap-on" width={18} height={18} />
          <IconMoon className="swap-off" width={18} height={18} />
        </label>
      </div>
    </div>
  );
}
