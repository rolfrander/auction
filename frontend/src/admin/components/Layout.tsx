import { NavLink, Outlet } from "react-router";
import { cx } from "./ui";

const navItems = [
  { to: "/catalogs", label: "Kataloger" },
  { to: "/items", label: "Objekter" },
];

export function Layout() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <span className="text-sm font-semibold tracking-tight">
            Auksjon <span className="font-normal text-zinc-400">admin</span>
          </span>
          <nav className="flex gap-1">
            {navItems.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) =>
                  cx(
                    "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                    isActive ? "bg-zinc-100 text-zinc-900" : "text-zinc-500 hover:text-zinc-900",
                  )
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <a href="/" className="ml-auto text-sm text-zinc-500 hover:text-zinc-900">
            Til katalogen ↗
          </a>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
