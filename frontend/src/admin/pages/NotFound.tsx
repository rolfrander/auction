import { Link } from "react-router";
import { buttonClass, EmptyState } from "../components/ui";

export function NotFound({ what = "Siden" }: { what?: string }) {
  return (
    <EmptyState title={`${what} finnes ikke`}>
      <Link to="/catalogs" className={buttonClass("secondary", "sm", "mt-2")}>
        Til kataloger
      </Link>
    </EmptyState>
  );
}
