// Typed wrappers around the backend JSON API. Interfaces mirror auksjon.schema.

export interface Health {
  status: string;
}

export interface Item {
  id: string;
  title: string;
  description: string;
  /** first image is the main image */
  images: string[];
  priceLow: number | null;
  priceHigh: number | null;
  startingPrice: number | null;
  createdAt: string;
  /** ids of catalogs containing this item */
  catalogs: string[];
}

export interface ItemInput {
  title: string;
  description?: string;
  images?: string[];
  priceLow?: number | null;
  priceHigh?: number | null;
  startingPrice?: number | null;
}

export type ItemPatch = Partial<ItemInput>;

export interface Catalog {
  id: string;
  name: string;
  /** ISO date, YYYY-MM-DD */
  date: string | null;
  visible: boolean;
}

export interface CatalogInput {
  name: string;
  date?: string | null;
  visible?: boolean;
}

export type CatalogPatch = Partial<CatalogInput>;

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const json = body !== undefined && !(body instanceof FormData);
  const res = await fetch(`/api${path}`, {
    method,
    headers: json ? { "Content-Type": "application/json" } : {},
    body: body === undefined ? undefined : json ? JSON.stringify(body) : (body as FormData),
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const err = (await res.json()) as { error?: string };
      if (err.error) msg = err.error;
    } catch {
      // non-JSON error body
    }
    throw new ApiError(res.status, msg);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const getHealth = () => request<Health>("GET", "/health");

// --- session (HttpOnly cookie set by /login, sent automatically) ---
export interface Session {
  username: string;
}
export const login = (username: string, password: string) =>
  request<Session>("POST", "/login", { username, password });
export const logout = () => request<void>("POST", "/logout");
/** Current session, or null when not logged in. */
export async function getSession(): Promise<Session | null> {
  try {
    return await request<Session>("GET", "/admin/session");
  } catch (e) {
    if (isUnauthorized(e)) return null;
    throw e;
  }
}
export const isUnauthorized = (e: unknown): boolean => e instanceof ApiError && e.status === 401;

// --- public ---
export const listVisibleCatalogs = () => request<Catalog[]>("GET", "/catalogs");
export const listVisibleCatalogItems = (catalogId: string) =>
  request<Item[]>("GET", `/catalogs/${catalogId}/items`);

// --- admin: catalogs ---
export const listCatalogs = () => request<Catalog[]>("GET", "/admin/catalogs");
export const getCatalog = (id: string) => request<Catalog>("GET", `/admin/catalogs/${id}`);
export const createCatalog = (c: CatalogInput) => request<Catalog>("POST", "/admin/catalogs", c);
export const updateCatalog = (id: string, patch: CatalogPatch) =>
  request<Catalog>("PATCH", `/admin/catalogs/${id}`, patch);
export const deleteCatalog = (id: string) => request<void>("DELETE", `/admin/catalogs/${id}`);

export const listCatalogItems = (id: string) =>
  request<Item[]>("GET", `/admin/catalogs/${id}/items`);
/** Replace catalog contents with the given item ids, in order. */
export const setCatalogItems = (id: string, itemIds: string[]) =>
  request<Item[]>("PUT", `/admin/catalogs/${id}/items`, itemIds);
/** Add (or move) item; position is 0-based, default last. */
export const addItemToCatalog = (id: string, itemId: string, position?: number) =>
  request<Item[]>(
    "PUT",
    `/admin/catalogs/${id}/items/${itemId}${position === undefined ? "" : `?position=${position}`}`,
  );
export const removeItemFromCatalog = (id: string, itemId: string) =>
  request<void>("DELETE", `/admin/catalogs/${id}/items/${itemId}`);

// --- admin: items ---
export const listItems = () => request<Item[]>("GET", "/admin/items");
export const getItem = (id: string) => request<Item>("GET", `/admin/items/${id}`);
export const createItem = (item: ItemInput) => request<Item>("POST", "/admin/items", item);
export const updateItem = (id: string, patch: ItemPatch) =>
  request<Item>("PATCH", `/admin/items/${id}`, patch);
export const deleteItem = (id: string) => request<void>("DELETE", `/admin/items/${id}`);
export const deleteAllItems = () => request<{ deleted: number }>("DELETE", "/admin/items");

// --- admin: images ---
export interface UploadedImage {
  /** URL to store in Item.images */
  url: string;
}
export function uploadImage(file: File): Promise<UploadedImage> {
  const form = new FormData();
  form.append("file", file);
  return request<UploadedImage>("POST", "/admin/images", form);
}
