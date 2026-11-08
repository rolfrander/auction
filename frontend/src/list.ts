// Printable catalog list (plain DOM). /list/#<catalogId>; without id, links to each catalog.
// (Hash, not query: the backend's /list/ -> /list/index.html redirect drops the query string.)
// Logged-in admins see all catalogs (incl. hidden), others only visible ones.
import * as api from "./api";
import { formatDate, formatPrice, formatRange } from "./format";

const app = document.querySelector<HTMLElement>("#app")!;

function el<K extends keyof HTMLElementTagNameMap>(tag: K, cls?: string, ...children: (Node | string)[]) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  e.append(...children);
  return e;
}

function table(items: api.Item[]): HTMLTableElement {
  const t = el("table");
  const headRow = el("tr");
  for (const [label, cls] of [
    ["Nr", "pos"],
    ["Tittel", "title"],
    ["Prisantydning", "price"],
    ["Startpris", "price"],
    ["Notater", "notes"],
  ]) {
    headRow.append(el("th", cls, label));
  }
  t.append(el("thead", undefined, headRow));
  const body = el("tbody");
  items.forEach((item, i) =>
    body.append(
      el(
        "tr",
        undefined,
        el("td", "pos", String(i + 1)),
        el("td", "title", item.title),
        el("td", "price", formatRange(item.priceLow, item.priceHigh)),
        el("td", "price", formatPrice(item.startingPrice)),
        el("td", "notes"),
      ),
    ),
  );
  t.append(body);
  return t;
}

async function main() {
  const admin = (await api.getSession()) !== null;
  const catalogs = await (admin ? api.listCatalogs() : api.listVisibleCatalogs());
  const id = (decodeURIComponent(location.hash.slice(1)) || null) ?? (catalogs.length === 1 ? catalogs[0].id : null);

  if (!id) {
    document.title = "Auksjonsliste";
    const ul = el("ul", "catalogs");
    for (const c of catalogs) {
      const a = el("a", undefined, c.name);
      a.href = `#${encodeURIComponent(c.id)}`;
      ul.append(el("li", undefined, a, c.date ? ` – ${formatDate(c.date)}` : ""));
    }
    app.replaceChildren(
      el("h1", undefined, "Kataloger"),
      catalogs.length ? ul : el("p", undefined, "Ingen kataloger."),
    );
    return;
  }

  const catalog = catalogs.find((c) => c.id === id);
  if (!catalog) {
    app.replaceChildren(el("p", "error", "Fant ikke katalogen."));
    return;
  }
  const items = await (admin ? api.listCatalogItems(id) : api.listVisibleCatalogItems(id));
  document.title = `${catalog.name} – liste`;
  app.replaceChildren(
    el("h1", undefined, catalog.name),
    catalog.date ? el("p", "date", formatDate(catalog.date)) : "",
    items.length ? table(items) : el("p", undefined, "Katalogen er tom."),
  );
}

function run() {
  main().catch((e: unknown) => {
    app.replaceChildren(
      el("p", "error", `Kunne ikke laste katalogen (${e instanceof Error ? e.message : String(e)}).`),
    );
  });
}
window.addEventListener("hashchange", run);
run();
