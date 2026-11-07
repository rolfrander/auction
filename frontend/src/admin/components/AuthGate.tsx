import type { ReactNode } from "react";
import { LoginPage } from "../pages/LoginPage";
import { useSession } from "../queries";
import { ErrorMessage, Loading } from "./ui";

/** Renders children when logged in, otherwise the login page (keeping the current URL). */
export function AuthGate({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session.isPending) return <Loading />;
  if (session.isError)
    return (
      <div className="mx-auto max-w-md p-6">
        <ErrorMessage error={session.error} />
      </div>
    );
  return session.data ? children : <LoginPage />;
}
