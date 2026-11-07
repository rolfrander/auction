import { NavLink, Outlet } from "react-router";
import { useLogout, useSession } from "../queries";
import { Button, cx } from "./ui";

const navItems = [
  { to: "/catalogs", label: "Kataloger" },
  { to: "/items", label: "Objekter" },
];

export function Layout() {
  const session = useSession();
  const logout = useLogout();
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
          <a href="/" className="ml-auto hidden text-sm text-zinc-500 hover:text-zinc-900 sm:inline">
            Til katalogen ↗
          </a>
          <Button
            variant="ghost"
            size="sm"
            className="ml-auto sm:ml-0"
            title={session.data ? `Logget inn som ${session.data.username}` : undefined}
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
          >
            Logg ut
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
