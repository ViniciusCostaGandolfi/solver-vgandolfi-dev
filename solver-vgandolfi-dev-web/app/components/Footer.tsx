import { IconRoute } from "./icons";

export function Footer() {
  return (
    <footer className="footer footer-center border-t border-base-300/60 bg-base-100/60 px-4 py-10">
      <aside className="flex flex-col items-center gap-2 text-center">
        <a href="#top" className="flex items-center gap-2">
          <span className="grid h-7 w-7 place-items-center rounded-box bg-primary text-primary-content">
            <IconRoute width={15} height={15} />
          </span>
          <span className="font-display text-lg font-bold">solver</span>
        </a>
        <p className="text-xs text-base-content/50">
          Ferramenta gratuita de otimização de rotas — feito por Vinicius
          Gandolfi
        </p>
      </aside>
    </footer>
  );
}
