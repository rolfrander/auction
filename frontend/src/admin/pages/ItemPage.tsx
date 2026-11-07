import { useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ApiError, type Item, type ItemInput } from "../../api";
import { ImageEditor } from "../components/ImageEditor";
import {
  Button,
  Card,
  CardHeader,
  ConfirmButton,
  ErrorMessage,
  Field,
  Input,
  Loading,
  PageHeader,
  Textarea,
  Toggle,
} from "../components/ui";
import { formatDate } from "../../format";
import {
  useAddToCatalog,
  useCatalogs,
  useCreateItem,
  useDeleteItem,
  useItem,
  useRemoveFromCatalog,
  useUpdateItem,
} from "../queries";
import { NotFound } from "./NotFound";

/** Handles both /items/new and /items/:id. */
export function ItemPage() {
  const { id } = useParams();
  return id ? <EditItem id={id} /> : <NewItem />;
}

function NewItem() {
  const navigate = useNavigate();
  const create = useCreateItem();
  return (
    <>
      <BackLink />
      <PageHeader title="Nytt objekt" />
      <ItemForm
        initial={emptyInput}
        submitLabel="Opprett"
        pending={create.isPending}
        error={create.error}
        onSubmit={(input) => create.mutate(input, { onSuccess: (item) => navigate(`/items/${item.id}`, { replace: true }) })}
        aside={
          <Card className="p-4 text-sm text-zinc-500 sm:p-6">Du kan legge objektet i kataloger etter at det er opprettet.</Card>
        }
      />
    </>
  );
}

function EditItem({ id }: { id: string }) {
  const navigate = useNavigate();
  const item = useItem(id);
  const update = useUpdateItem(id);
  const del = useDeleteItem();

  if (item.isPending) return <Loading />;
  if (item.error instanceof ApiError && item.error.status === 404) return <NotFound what="Objektet" />;
  if (item.error) return <ErrorMessage error={item.error} />;

  const it = item.data;
  return (
    <>
      <BackLink />
      <PageHeader
        title={it.title}
        subtitle={`Lagt til ${new Date(it.createdAt).toLocaleDateString("nb-NO")}`}
        actions={
          <ConfirmButton disabled={del.isPending} onConfirm={() => del.mutate(id, { onSuccess: () => navigate("/items") })}>
            Slett objekt
          </ConfirmButton>
        }
      />
      <ErrorMessage error={del.error} />
      <ItemForm
        key={it.id}
        initial={toInput(it)}
        submitLabel="Lagre"
        pending={update.isPending}
        error={update.error}
        onSubmit={(input) => update.mutate(input)}
        aside={<CatalogMembership item={it} />}
      />
    </>
  );
}

function BackLink() {
  return (
    <Link to="/items" className="mb-2 inline-block text-sm text-zinc-500 hover:text-zinc-900">
      ← Objekter
    </Link>
  );
}

// --- form --------------------------------------------------------------------

type FormState = {
  title: string;
  description: string;
  images: string[];
  priceLow: string;
  priceHigh: string;
  startingPrice: string;
};

const emptyInput: Required<ItemInput> = {
  title: "",
  description: "",
  images: [],
  priceLow: null,
  priceHigh: null,
  startingPrice: null,
};

function toInput(item: Item): Required<ItemInput> {
  const { title, description, images, priceLow, priceHigh, startingPrice } = item;
  return { title, description, images, priceLow, priceHigh, startingPrice };
}

const priceStr = (n: number | null) => (n == null ? "" : String(n));
const parsePrice = (s: string): number | null => (s.trim() === "" ? null : Number(s));

function toForm(input: Required<ItemInput>): FormState {
  return {
    ...input,
    priceLow: priceStr(input.priceLow),
    priceHigh: priceStr(input.priceHigh),
    startingPrice: priceStr(input.startingPrice),
  };
}

function fromForm(f: FormState): Required<ItemInput> {
  return {
    title: f.title.trim(),
    description: f.description,
    images: f.images,
    priceLow: parsePrice(f.priceLow),
    priceHigh: parsePrice(f.priceHigh),
    startingPrice: parsePrice(f.startingPrice),
  };
}

function ItemForm({
  initial,
  submitLabel,
  pending,
  error,
  onSubmit,
  aside,
}: {
  initial: Required<ItemInput>;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  onSubmit: (input: Required<ItemInput>) => void;
  aside?: ReactNode;
}) {
  const [form, setForm] = useState(() => toForm(initial));
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const value = fromForm(form);
  const dirty = JSON.stringify(value) !== JSON.stringify(initial);
  const rangeError =
    value.priceLow != null && value.priceHigh != null && value.priceLow > value.priceHigh
      ? "Nedre estimat kan ikke være høyere enn øvre."
      : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!rangeError) onSubmit(value);
  };

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <form onSubmit={submit} className="flex flex-col gap-6 lg:col-span-2">
        <Card>
          <CardHeader title="Beskrivelse" />
          <div className="flex flex-col gap-4 p-4 sm:p-6">
            <Field label="Tittel">
              <Input required value={form.title} onChange={(e) => set("title", e.target.value)} />
            </Field>
            <Field label="Beskrivelse">
              <Textarea rows={6} value={form.description} onChange={(e) => set("description", e.target.value)} />
            </Field>
          </div>
        </Card>

        <Card>
          <CardHeader title="Pris" />
          <div className="grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
            <PriceField label="Estimat fra" value={form.priceLow} onChange={(v) => set("priceLow", v)} />
            <PriceField label="Estimat til" value={form.priceHigh} onChange={(v) => set("priceHigh", v)} error={rangeError} />
            <PriceField label="Startpris" value={form.startingPrice} onChange={(v) => set("startingPrice", v)} />
          </div>
        </Card>

        <Card>
          <CardHeader title="Bilder" />
          <div className="p-4 sm:p-6">
            <ImageEditor
              images={form.images}
              onChange={(update) => setForm((f) => ({ ...f, images: update(f.images) }))}
            />
          </div>
        </Card>

        <ErrorMessage error={error} />
        <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-zinc-200 bg-zinc-50/90 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
          {dirty && (
            <Button variant="ghost" onClick={() => setForm(toForm(initial))}>
              Tilbakestill
            </Button>
          )}
          <Button type="submit" disabled={!dirty || !value.title || !!rangeError || pending}>
            {pending ? "Lagrer…" : submitLabel}
          </Button>
        </div>
      </form>
      <div className="flex flex-col gap-6">{aside}</div>
    </div>
  );
}

function PriceField({
  label,
  value,
  onChange,
  error,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  error?: string | null;
}) {
  return (
    <Field label={label} error={error}>
      <div className="relative">
        <Input
          type="number"
          inputMode="numeric"
          min={0}
          step={1}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="pr-10"
        />
        <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-zinc-400">kr</span>
      </div>
    </Field>
  );
}

// --- catalog membership ------------------------------------------------------

function CatalogMembership({ item }: { item: Item }) {
  const catalogs = useCatalogs();
  const add = useAddToCatalog();
  const remove = useRemoveFromCatalog();
  const member = new Set(item.catalogs);
  const busy = add.isPending || remove.isPending;

  return (
    <Card>
      <CardHeader title="Kataloger" />
      <div className="p-4 sm:p-6">
        <ErrorMessage error={catalogs.error ?? add.error ?? remove.error} />
        {catalogs.isPending ? (
          <Loading />
        ) : catalogs.data?.length === 0 ? (
          <p className="text-sm text-zinc-500">
            Ingen kataloger.{" "}
            <Link to="/catalogs" className="text-indigo-600 hover:underline">
              Opprett en
            </Link>
          </p>
        ) : (
          <ul className="flex flex-col gap-4">
            {catalogs.data?.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-3">
                <Toggle
                  checked={member.has(c.id)}
                  label={c.name}
                  onChange={(on) =>
                    !busy && (on ? add : remove).mutate({ catalogId: c.id, itemId: item.id })
                  }
                />
                <Link to={`/catalogs/${c.id}`} className="shrink-0 text-xs text-zinc-400 hover:text-zinc-700">
                  {formatDate(c.date)}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
