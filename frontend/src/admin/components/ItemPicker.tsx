import { useMemo, useState } from "react";
import type { Item } from "../../api";
import { formatRange } from "../../format";
import { Button, Input, Thumb } from "./ui";

/** Searchable list of items not already in the catalog. */
export function ItemPicker({
  items,
  excludeIds,
  onAdd,
  pending,
}: {
  items: Item[];
  excludeIds: Set<string>;
  onAdd: (item: Item) => void;
  pending?: boolean;
}) {
  const [query, setQuery] = useState("");
  const candidates = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items.filter(
      (i) => !excludeIds.has(i.id) && (!q || i.title.toLowerCase().includes(q) || i.description.toLowerCase().includes(q)),
    );
  }, [items, excludeIds, query]);

  return (
    <div className="flex flex-col gap-3">
      <Input type="search" placeholder="Søk i objekter…" value={query} onChange={(e) => setQuery(e.target.value)} />
      {candidates.length === 0 ? (
        <p className="py-6 text-center text-sm text-zinc-500">
          {query.trim() ? "Ingen treff." : "Alle objekter er allerede i katalogen."}
        </p>
      ) : (
        <ul className="-mx-2 max-h-96 divide-y divide-zinc-100 overflow-y-auto">
          {candidates.map((item) => (
            <li key={item.id} className="flex items-center gap-3 px-2 py-2">
              <Thumb src={item.images[0]} className="size-10 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-zinc-900">{item.title}</p>
                <p className="truncate text-xs text-zinc-500">{formatRange(item.priceLow, item.priceHigh)}</p>
              </div>
              <Button variant="secondary" size="sm" disabled={pending} onClick={() => onAdd(item)}>
                Legg til
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
