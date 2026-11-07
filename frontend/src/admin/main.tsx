import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { isUnauthorized } from "../api";
import { AuthGate } from "./components/AuthGate";
import { Layout } from "./components/Layout";
import { CatalogPage } from "./pages/CatalogPage";
import { CatalogsPage } from "./pages/CatalogsPage";
import { ItemPage } from "./pages/ItemPage";
import { ItemsPage } from "./pages/ItemsPage";
import { NotFound } from "./pages/NotFound";
import { markLoggedOut } from "./queries";
import "./index.css";

// Any 401 (e.g. expired session) drops back to the login page.
const onError = (e: unknown) => {
  if (isUnauthorized(e)) markLoggedOut(queryClient);
};
const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: {
    queries: { staleTime: 10_000, retry: (n, e) => !isUnauthorized(e) && n < 1 },
  },
});

const router = createBrowserRouter(
  [
    {
      element: (
        <AuthGate>
          <Layout />
        </AuthGate>
      ),
      children: [
        { index: true, element: <Navigate to="/catalogs" replace /> },
        { path: "catalogs", element: <CatalogsPage /> },
        { path: "catalogs/:id", element: <CatalogPage /> },
        { path: "items", element: <ItemsPage /> },
        { path: "items/new", element: <ItemPage /> },
        { path: "items/:id", element: <ItemPage /> },
        { path: "*", element: <NotFound /> },
      ],
    },
  ],
  { basename: "/admin" },
);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
