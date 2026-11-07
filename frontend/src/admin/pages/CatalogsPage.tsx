import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { Badge, Button, Card, EmptyState, ErrorMessage, Input, Loading, PageHeader } from "../components/ui";
import { formatDate } from "../../format";
import { useCatalogs, useCreateCatalog } from "../queries";

export function CatalogsPage() {
  const { data: catalogs, isPending, error } = useCatalogs();
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title="Kataloger"
        subtitle="Synlige kataloger vises på den offentlige siden."
        actions={!creating && <Button onClick={() => setCreating(true)}>Ny katalog</Button>}
      />

      {creating && <NewCatalogForm onCancel={() => setCreating(false)} />}

      <ErrorMessage error={error} />
      {isPending ? (
        <Loading />
      ) : catalogs?.length === 0 ? (
        <EmptyState title="Ingen kataloger ennå">Opprett en katalog for å komme i gang.</EmptyState>
      ) : (
        <Card>
          <ul className="divide-y divide-zinc-100">
            {catalogs?.map((c) => (
              <li key={c.id}>
                <Link
                  to={`/catalogs/${c.id}`}
                  className="flex items-center gap-4 px-4 py-4 transition-colors hover:bg-zinc-50 sm:px-6"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-zinc-900">{c.name}</p>
                    <p className="mt-0.5 text-sm text-zinc-500">{formatDate(c.date)}</p>
                  </div>
                  {c.visible ? <Badge tone="green">Synlig</Badge> : <Badge>Skjult</Badge>}
                  <span className="text-zinc-300" aria-hidden>
                    ›
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}

function NewCatalogForm({ onCancel }: { onCancel: () => void }) {
  const navigate = useNavigate();
  const create = useCreateCatalog();
  const [name, setName] = useState("");
  const [date, setDate] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate(
      { name: name.trim(), date: date || null },
      { onSuccess: (c) => navigate(`/catalogs/${c.id}`) },
    );
  };

  return (
    <Card className="mb-6 p-4 sm:p-6">
      <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <label className="flex-1">
          <span className="mb-1.5 block text-sm font-medium text-zinc-700">Navn</span>
          <Input autoFocus required value={name} onChange={(e) => setName(e.target.value)} placeholder="Høstauksjon 2026" />
        </label>
        <label>
          <span className="mb-1.5 block text-sm font-medium text-zinc-700">Dato</span>
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <div className="flex gap-2">
          <Button type="submit" disabled={!name.trim() || create.isPending}>
            Opprett
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Avbryt
          </Button>
        </div>
      </form>
      {create.error && (
        <div className="mt-3">
          <ErrorMessage error={create.error} />
        </div>
      )}
    </Card>
  );
}
