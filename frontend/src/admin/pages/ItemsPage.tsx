import { useMemo, useState } from "react";
import { Link } from "react-router";
import {
  Badge,
  buttonClass,
  Card,
  ConfirmButton,
  EmptyState,
  ErrorMessage,
  Input,
  Loading,
  PageHeader,
  Thumb,
} from "../components/ui";
import { formatPrice, formatRange } from "../../format";
import { useCatalogs, useDeleteAllItems, useItems } from "../queries";

export function ItemsPage() {
  const items = useItems();
  const catalogs = useCatalogs();
  const deleteAll = useDeleteAllItems();
  const [query, setQuery] = useState("");

  const catalogNames = useMemo(() => new Map(catalogs.data?.map((c) => [c.id, c.name])), [catalogs.data]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items.data ?? [];
    return (items.data ?? []).filter(
      (i) => i.title.toLowerCase().includes(q) || i.description.toLowerCase().includes(q),
    );
  }, [items.data, query]);

  return (
    <>
      <PageHeader
        title="Objekter"
        subtitle={items.data && `${items.data.length} objekter totalt`}
        actions={
          <>
            {!!items.data?.length && (
              <ConfirmButton
                disabled={deleteAll.isPending}
                confirmLabel="Slett ALLE objekter?"
                onConfirm={() => deleteAll.mutate()}
              >
                Slett alle
              </ConfirmButton>
            )}
            <Link to="/items/new" className={buttonClass()}>
              Nytt objekt
            </Link>
          </>
        }
      />

      <ErrorMessage error={items.error ?? deleteAll.error} />
      {items.isPending ? (
        <Loading />
      ) : items.data?.length === 0 ? (
        <EmptyState title="Ingen objekter ennå">
          <Link to="/items/new" className={buttonClass("primary", "sm", "mt-2")}>
            Opprett det første
          </Link>
        </EmptyState>
      ) : (
        <>
          <Input
            type="search"
            placeholder="Søk i tittel og beskrivelse…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="mb-4 max-w-sm"
          />
          <Card>
            {filtered.length === 0 ? (
              <p className="py-10 text-center text-sm text-zinc-500">Ingen treff.</p>
            ) : (
              <ul className="divide-y divide-zinc-100">
                {filtered.map((item) => (
                  <li key={item.id}>
                    <Link
                      to={`/items/${item.id}`}
                      className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-zinc-50 sm:px-6"
                    >
                      <Thumb src={item.images[0]} className="size-14 shrink-0" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-zinc-900">{item.title}</p>
                        <p className="truncate text-sm text-zinc-500">{item.description || "Ingen beskrivelse"}</p>
                        <div className="mt-1 flex flex-wrap gap-1 sm:hidden">
                          <span className="text-xs text-zinc-600">{formatRange(item.priceLow, item.priceHigh)}</span>
                        </div>
                      </div>
                      <div className="hidden w-44 text-right text-sm sm:block">
                        <p className="text-zinc-900">{formatRange(item.priceLow, item.priceHigh)}</p>
                        <p className="text-zinc-500">Start {formatPrice(item.startingPrice)}</p>
                      </div>
                      <div className="hidden w-48 flex-wrap justify-end gap-1 md:flex">
                        {item.catalogs.length === 0 ? (
                          <Badge tone="amber">Ikke i katalog</Badge>
                        ) : (
                          item.catalogs.map((cid) => <Badge key={cid}>{catalogNames.get(cid) ?? "…"}</Badge>)
                        )}
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}
    </>
  );
}
