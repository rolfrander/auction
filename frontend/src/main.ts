// Public catalog viewer (plain DOM). Routes (hash-based, so any static host works):
//   #/                       catalog list, or the catalog itself when there is only one
//   #/k/<catalogId>          items in catalog
//   #/k/<catalogId>/<itemId> same, with item overlay open (back button closes it)
import { type Catalog, type Item, listVisibleCatalogItems, listVisibleCatalogs } from "./api";
import { formatDate, formatPrice, formatRange } from "./format";

const app = document.querySelector<HTMLElement>("#app")!;

// --- tiny DOM helper ---------------------------------------------------------

type Child = Node | string | null | undefined | false;
type Props = { [attr: string]: string | undefined | Listeners; on?: Listeners };
type Listeners = { [event: string]: (e: Event) => unknown };

function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Props = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  const { on, ...attrs } = props;
  for (const [k, v] of Object.entries(attrs)) if (typeof v === "string") el.setAttribute(k, v);
  for (const [ev, fn] of Object.entries(on ?? {})) el.addEventListener(ev, fn);
  for (const c of children) if (c) el.append(c);
  return el;
}

// --- data (cached for the session) -------------------------------------------

let catalogsP: Promise<Catalog[]> | null = null;
const itemsP = new Map<string, Promise<Item[]>>();

const catalogs = () => (catalogsP ??= listVisibleCatalogs());
function catalogItems(id: string): Promise<Item[]> {
  let p = itemsP.get(id);
  if (!p) itemsP.set(id, (p = listVisibleCatalogItems(id)));
  return p;
}

// --- routing -----------------------------------------------------------------

type Route = { view: "list" } | { view: "catalog"; catalogId: string; itemId?: string };

function parseRoute(): Route {
  const [, kind, catalogId, itemId] = location.hash.replace(/^#/, "").split("/");
  return kind === "k" && catalogId ? { view: "catalog", catalogId, itemId } : { view: "list" };
}

const catalogHref = (id: string) => `#/k/${id}`;
const itemHref = (cid: string, iid: string) => `#/k/${cid}/${iid}`;

let renderedCatalog: string | null = null;
let renderSeq = 0;

async function render() {
  const route = parseRoute();
  const seq = ++renderSeq;
  try {
    if (route.view === "list") {
      const cs = await catalogs();
      if (seq !== renderSeq) return;
      if (cs.length === 1) {
        location.replace(catalogHref(cs[0].id));
        return;
      }
      closeOverlay();
      renderedCatalog = null;
      showList(cs);
      return;
    }
    const [cs, items] = await Promise.all([catalogs(), catalogItems(route.catalogId)]);
    if (seq !== renderSeq) return;
    const catalog = cs.find((c) => c.id === route.catalogId);
    if (!catalog) return showMessage("Fant ikke katalogen.", true);
    if (renderedCatalog !== catalog.id) {
      showCatalog(catalog, items, cs.length > 1);
      renderedCatalog = catalog.id;
    }
    const item = route.itemId ? items.find((i) => i.id === route.itemId) : undefined;
    if (item) openOverlay(catalog, item);
    else closeOverlay();
  } catch (e) {
    if (seq !== renderSeq) return;
    catalogsP = null;
    itemsP.clear();
    renderedCatalog = null;
    showMessage(`Kunne ikke laste katalogen (${e instanceof Error ? e.message : String(e)}).`, true);
  }
}

// --- views -------------------------------------------------------------------

function showMessage(text: string, retry = false) {
  closeOverlay();
  app.replaceChildren(
    h(
      "p",
      { class: "message" },
      text,
      retry && " ",
      retry && h("button", { type: "button", class: "link-button", on: { click: () => void render() } }, "Prøv igjen"),
    ),
  );
  document.title = "Auksjon";
}

function showList(cs: Catalog[]) {
  document.title = "Auksjon";
  if (cs.length === 0) return app.replaceChildren(h("p", { class: "message" }, "Ingen kataloger er publisert ennå."));
  app.replaceChildren(
    h("h1", {}, "Kataloger"),
    h(
      "ul",
      { class: "catalog-list" },
      ...cs.map((c) =>
        h(
          "li",
          {},
          h("a", { href: catalogHref(c.id) }, h("span", { class: "catalog-name" }, c.name), c.date && h("span", { class: "muted" }, formatDate(c.date))),
        ),
      ),
    ),
  );
  window.scrollTo(0, 0);
}

function showCatalog(catalog: Catalog, items: Item[], hasOthers: boolean) {
  document.title = `${catalog.name} – Auksjon`;
  app.replaceChildren(
    hasOthers ? h("a", { href: "#/", class: "back" }, "← Alle kataloger") : "",
    h("h1", {}, catalog.name),
    catalog.date ? h("p", { class: "muted catalog-date" }, formatDate(catalog.date)) : "",
    items.length === 0
      ? h("p", { class: "message" }, "Katalogen er tom.")
      : h("ol", { class: "items" }, ...items.map((item, i) => itemCard(catalog, item, i + 1))),
  );
  window.scrollTo(0, 0);
}

function itemCard(catalog: Catalog, item: Item, n: number) {
  const img = item.images[0];
  return h(
    "li",
    { class: "item", id: `item-${item.id}` },
    h(
      "a",
      { href: itemHref(catalog.id, item.id), class: "item-link" },
      h(
        "div",
        { class: "thumb" },
        img ? h("img", { src: img, alt: "", loading: "lazy", decoding: "async" }) : h("span", { class: "no-image" }, "Uten bilde"),
        item.images.length > 1 ? h("span", { class: "image-count" }, `${item.images.length} bilder`) : "",
      ),
      h(
        "div",
        { class: "item-body" },
        h("h2", {}, h("span", { class: "lot" }, `${n}.`), " ", item.title),
        item.description ? h("p", { class: "description clamp" }, item.description) : "",
        prices(item),
      ),
    ),
  );
}

function prices(item: Item) {
  return h(
    "dl",
    { class: "prices" },
    h("div", {}, h("dt", {}, "Estimat"), h("dd", {}, formatRange(item.priceLow, item.priceHigh))),
    h("div", {}, h("dt", {}, "Startpris"), h("dd", {}, formatPrice(item.startingPrice))),
  );
}

// --- overlay -----------------------------------------------------------------

let overlay: { el: HTMLElement; itemId: string; onKey: (e: KeyboardEvent) => void } | null = null;

function closeOverlay() {
  if (!overlay) return;
  const { el, itemId, onKey } = overlay;
  overlay = null;
  el.remove();
  document.removeEventListener("keydown", onKey);
  document.body.classList.remove("no-scroll");
  document.querySelector<HTMLElement>(`#item-${itemId} a`)?.focus({ preventScroll: true });
}

/** Close via history so the back button and the close button behave the same. */
function requestClose(catalog: Catalog) {
  if (history.state === "overlay") history.back();
  else location.replace(catalogHref(catalog.id));
}

function openOverlay(catalog: Catalog, item: Item) {
  if (overlay?.itemId === item.id) return;
  closeOverlay();

  let index = 0;
  const n = item.images.length;

  const slides = h(
    "div",
    { class: "slides" },
    ...item.images.map((src, i) =>
      h("div", { class: "slide" }, h("img", { src, alt: `${item.title} – bilde ${i + 1} av ${n}`, loading: i === 0 ? "eager" : "lazy" })),
    ),
  );
  const counter = h("span", { class: "counter" });
  const thumbs = h(
    "div",
    { class: "thumbs" },
    ...item.images.map((src, i) =>
      h(
        "button",
        { type: "button", "aria-label": `Bilde ${i + 1}`, on: { click: () => go(i) } },
        h("img", { src, alt: "", loading: "lazy" }),
      ),
    ),
  );
  const prev = h("button", { type: "button", class: "nav prev", "aria-label": "Forrige bilde", on: { click: () => go(index - 1) } }, "‹");
  const next = h("button", { type: "button", class: "nav next", "aria-label": "Neste bilde", on: { click: () => go(index + 1) } }, "›");

  function update(i: number) {
    index = i;
    counter.textContent = `${i + 1} / ${n}`;
    prev.disabled = i === 0;
    next.disabled = i === n - 1;
    thumbs.querySelectorAll("button").forEach((b, j) => b.classList.toggle("active", j === i));
  }
  function go(i: number) {
    if (i < 0 || i >= n) return;
    slides.scrollTo({ left: i * slides.clientWidth, behavior: "smooth" });
    update(i);
  }
  // Swipe/scroll-snap updates the index too.
  slides.addEventListener("scroll", () => {
    const i = Math.round(slides.scrollLeft / Math.max(1, slides.clientWidth));
    if (i !== index && i >= 0 && i < n) update(i);
  });

  const gallery =
    n === 0
      ? h("div", { class: "gallery empty" }, h("span", { class: "no-image" }, "Uten bilde"))
      : h("div", { class: "gallery" }, slides, n > 1 ? prev : "", n > 1 ? next : "", n > 1 ? counter : "");

  const closeBtn = h("button", { type: "button", class: "close", "aria-label": "Lukk", on: { click: () => requestClose(catalog) } }, "×");

  const dialog = h(
    "div",
    { class: "dialog", role: "dialog", "aria-modal": "true", "aria-labelledby": "dialog-title" },
    closeBtn,
    gallery,
    n > 1 ? thumbs : "",
    h(
      "div",
      { class: "dialog-body" },
      h("h2", { id: "dialog-title" }, item.title),
      prices(item),
      item.description ? h("p", { class: "description" }, item.description) : "",
    ),
  );

  const el = h("div", { class: "overlay" }, dialog);
  el.addEventListener("click", (e) => {
    if (e.target === el) requestClose(catalog);
  });

  const onKey = (e: KeyboardEvent) => {
    if (e.key === "Escape") requestClose(catalog);
    else if (e.key === "ArrowLeft") go(index - 1);
    else if (e.key === "ArrowRight") go(index + 1);
    else if (e.key === "Tab") trapFocus(e, dialog);
  };

  overlay = { el, itemId: item.id, onKey };
  document.addEventListener("keydown", onKey);
  document.body.classList.add("no-scroll");
  document.body.append(el);
  if (n > 0) update(0);
  closeBtn.focus({ preventScroll: true });
}

function trapFocus(e: KeyboardEvent, root: HTMLElement) {
  const focusable = [...root.querySelectorAll<HTMLElement>("button:not([disabled]), a[href]")];
  if (focusable.length === 0) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault();
    last.focus();
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault();
    first.focus();
  }
}

// Mark history entries that open the overlay from the catalog, so closing can use history.back().
document.addEventListener("click", (e) => {
  const a = (e.target as Element | null)?.closest<HTMLAnchorElement>("a.item-link");
  if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  e.preventDefault();
  history.pushState("overlay", "", a.getAttribute("href"));
  void render();
});

window.addEventListener("hashchange", () => void render());
window.addEventListener("popstate", () => void render());
void render();
