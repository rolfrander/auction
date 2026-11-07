import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router";
import type { Catalog } from "../../api";
import { ApiError } from "../../api";
import { ItemPicker } from "../components/ItemPicker";
import { SortableItemList } from "../components/SortableItemList";
import {
  Badge,
  Button,
  Card,
  CardHeader,
  ConfirmButton,
  EmptyState,
  ErrorMessage,
  Field,
  Input,
  Loading,
  PageHeader,
  Toggle,
} from "../components/ui";
import {
  useAddToCatalog,
  useCatalog,
  useCatalogItems,
  useDeleteCatalog,
  useItems,
  useRemoveFromCatalog,
  useSetCatalogItems,
  useUpdateCatalog,
} from "../queries";
import { NotFound } from "./NotFound";

export function CatalogPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const catalog = useCatalog(id);
  const del = useDeleteCatalog();

  if (catalog.isPending) return <Loading />;
  if (catalog.error instanceof ApiError && catalog.error.status === 404) return <NotFound what="Katalogen" />;
  if (catalog.error) return <ErrorMessage error={catalog.error} />;

  const c = catalog.data;
  return (
    <>
      <Link to="/catalogs" className="mb-2 inline-block text-sm text-zinc-500 hover:text-zinc-900">
        ← Kataloger
      </Link>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {c.name} {c.visible ? <Badge tone="green">Synlig</Badge> : <Badge>Skjult</Badge>}
          </span>
        }
        actions={
          <ConfirmButton
            disabled={del.isPending}
            onConfirm={() => del.mutate(c.id, { onSuccess: () => navigate("/catalogs") })}
          >
            Slett katalog
          </ConfirmButton>
        }
      />
      <ErrorMessage error={del.error} />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <CatalogContents catalogId={c.id} />
        </div>
        <div className="flex flex-col gap-6">
          {/* keyed so the form resets when the saved catalog changes */}
          <CatalogDetails key={`${c.id}:${c.name}:${c.date}:${c.visible}`} catalog={c} />
        </div>
      </div>
    </>
  );
}

function CatalogDetails({ catalog }: { catalog: Catalog }) {
  const update = useUpdateCatalog(catalog.id);
  const [name, setName] = useState(catalog.name);
  const [date, setDate] = useState(catalog.date ?? "");
  const [visible, setVisible] = useState(catalog.visible);
  const dirty = name !== catalog.name || date !== (catalog.date ?? "") || visible !== catalog.visible;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    update.mutate({ name: name.trim(), date: date || null, visible });
  };

  return (
    <Card>
      <CardHeader title="Detaljer" />
      <form onSubmit={submit} className="flex flex-col gap-4 p-4 sm:p-6">
        <Field label="Navn">
          <Input required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Dato">
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Toggle checked={visible} onChange={setVisible} label="Synlig for publikum" />
        <ErrorMessage error={update.error} />
        <div className="flex justify-end">
          <Button type="submit" disabled={!dirty || !name.trim() || update.isPending}>
            {update.isPending ? "Lagrer…" : "Lagre"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function CatalogContents({ catalogId }: { catalogId: string }) {
  const items = useCatalogItems(catalogId);
  const allItems = useItems();
  const reorder = useSetCatalogItems(catalogId);
  const add = useAddToCatalog();
  const remove = useRemoveFromCatalog();
  const [adding, setAdding] = useState(false);

  const inCatalog = useMemo(() => new Set(items.data?.map((i) => i.id)), [items.data]);
  const error = items.error ?? reorder.error ?? add.error ?? remove.error;

  return (
    <>
      <Card>
        <CardHeader
          title={
            <>
              Objekter <span className="font-normal text-zinc-400">{items.data?.length ?? ""}</span>
            </>
          }
          actions={
            <Button variant={adding ? "secondary" : "primary"} size="sm" onClick={() => setAdding((a) => !a)}>
              {adding ? "Ferdig" : "Legg til objekter"}
            </Button>
          }
        />
        {error && (
          <div className="p-4">
            <ErrorMessage error={error} />
          </div>
        )}
        {items.isPending ? (
          <Loading />
        ) : items.data?.length === 0 ? (
          <div className="p-4 sm:p-6">
            <EmptyState title="Katalogen er tom">Legg til objekter for å bygge katalogen.</EmptyState>
          </div>
        ) : (
          <>
            <p className="px-4 pt-3 text-xs text-zinc-500 sm:px-6">Dra i håndtaket for å endre rekkefølgen.</p>
            <SortableItemList
              items={items.data ?? []}
              onReorder={(next) => reorder.mutate(next)}
              onRemove={(item) => remove.mutate({ catalogId, itemId: item.id })}
            />
          </>
        )}
      </Card>

      {adding && (
        <Card>
          <CardHeader title="Legg til objekter" />
          <div className="p-4 sm:p-6">
            {allItems.isPending ? (
              <Loading />
            ) : (
              <ItemPicker
                items={allItems.data ?? []}
                excludeIds={inCatalog}
                pending={add.isPending}
                onAdd={(item) => add.mutate({ catalogId, itemId: item.id })}
              />
            )}
          </div>
        </Card>
      )}
    </>
  );
}
