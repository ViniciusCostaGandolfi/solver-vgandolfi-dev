import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import type { Route } from "./+types/root";
import "./app.css";

export const links: Route.LinksFunction = () => [
  { rel: "icon", type: "image/svg+xml", href: "/favicon.svg" },
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&family=JetBrains+Mono:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap",
  },
];

const themeInitScript = `(function () {
  try {
    var stored = localStorage.getItem("solver:theme");
    var dark = stored
      ? stored === "dark"
      : window.matchMedia &&
        window.matchMedia("(prefers-color-scheme: dark)").matches;
    document.documentElement.dataset.theme = dark ? "optdark" : "optlight";
  } catch (e) {
    document.documentElement.dataset.theme = "optlight";
  }
})();`;

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="theme-color" content="#181a22" />
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
        <Meta />
        <Links />
      </head>
      <body className="min-h-dvh bg-base-100 text-base-content antialiased">
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let message = "Algo deu errado.";
  let details = "Ocorreu um erro inesperado.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    message = error.status === 404 ? "Página não encontrada" : "Erro";
    details =
      error.status === 404
        ? "A página solicitada não existe."
        : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="grid min-h-dvh place-items-center p-6">
      <div className="card card-border w-full max-w-md">
        <div className="card-body items-center text-center">
          <h1 className="font-display text-3xl font-bold">{message}</h1>
          <p className="text-sm text-base-content/70">{details}</p>
          {stack && (
            <pre className="w-full overflow-x-auto rounded-box bg-base-200 p-4 text-xs">
              <code>{stack}</code>
            </pre>
          )}
          <a href="/" className="btn btn-primary mt-2">
            Voltar ao início
          </a>
        </div>
      </div>
    </main>
  );
}