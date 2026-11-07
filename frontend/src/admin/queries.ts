// TanStack Query hooks around src/api.ts. Items carry their catalog ids and
// catalogs carry their items, so membership changes invalidate both.
import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import * as api from "../api";

export const keys = {
  catalogs: ["catalogs"] as const,
  catalog: (id: string) => ["catalogs", id] as const,
  catalogItems: (id: string) => ["catalogs", id, "items"] as const,
  items: ["items"] as const,
  item: (id: string) => ["items", id] as const,
};

const invalidateAll = (qc: QueryClient) =>
  Promise.all([
    qc.invalidateQueries({ queryKey: keys.catalogs }),
    qc.invalidateQueries({ queryKey: keys.items }),
  ]);

// --- queries ---
export const useCatalogs = () => useQuery({ queryKey: keys.catalogs, queryFn: api.listCatalogs });
export const useCatalog = (id: string) => useQuery({ queryKey: keys.catalog(id), queryFn: () => api.getCatalog(id) });
export const useCatalogItems = (id: string) =>
  useQuery({ queryKey: keys.catalogItems(id), queryFn: () => api.listCatalogItems(id) });
export const useItems = () => useQuery({ queryKey: keys.items, queryFn: api.listItems });
export const useItem = (id: string) => useQuery({ queryKey: keys.item(id), queryFn: () => api.getItem(id) });

// --- catalog mutations ---
export function useCreateCatalog() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: api.createCatalog, onSuccess: () => invalidateAll(qc) });
}

export function useUpdateCatalog(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (patch: api.CatalogPatch) => api.updateCatalog(id, patch),
    onSuccess: (catalog) => {
      qc.setQueryData(keys.catalog(id), catalog);
      return qc.invalidateQueries({ queryKey: keys.catalogs, exact: true });
    },
  });
}

export function useDeleteCatalog() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.deleteCatalog,
    onSuccess: (_, id) => {
      qc.removeQueries({ queryKey: keys.catalog(id) });
      return invalidateAll(qc);
    },
  });
}

/** Reorder / replace catalog contents. Optimistic so drag-and-drop feels instant. */
export function useSetCatalogItems(catalogId: string) {
  const qc = useQueryClient();
  const key = keys.catalogItems(catalogId);
  return useMutation({
    mutationFn: (items: api.Item[]) => api.setCatalogItems(catalogId, items.map((i) => i.id)),
    onMutate: async (items) => {
      await qc.cancelQueries({ queryKey: key });
      const previous = qc.getQueryData<api.Item[]>(key);
      qc.setQueryData(key, items);
      return { previous };
    },
    onError: (_err, _items, ctx) => qc.setQueryData(key, ctx?.previous),
    onSettled: () => invalidateAll(qc),
  });
}

export function useAddToCatalog() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ catalogId, itemId }: { catalogId: string; itemId: string }) =>
      api.addItemToCatalog(catalogId, itemId),
    onSuccess: () => invalidateAll(qc),
  });
}

export function useRemoveFromCatalog() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ catalogId, itemId }: { catalogId: string; itemId: string }) =>
      api.removeItemFromCatalog(catalogId, itemId),
    onSuccess: () => invalidateAll(qc),
  });
}

// --- item mutations ---
export function useCreateItem() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: api.createItem, onSuccess: () => invalidateAll(qc) });
}

export function useUpdateItem(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (patch: api.ItemPatch) => api.updateItem(id, patch),
    onSuccess: (item) => {
      qc.setQueryData(keys.item(id), item);
      return invalidateAll(qc);
    },
  });
}

export function useDeleteItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.deleteItem,
    onSuccess: (_, id) => {
      qc.removeQueries({ queryKey: keys.item(id) });
      return invalidateAll(qc);
    },
  });
}

export function useDeleteAllItems() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: api.deleteAllItems, onSuccess: () => invalidateAll(qc) });
}
