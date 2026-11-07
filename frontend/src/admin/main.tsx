import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { Layout } from "./components/Layout";
import { CatalogPage } from "./pages/CatalogPage";
import { CatalogsPage } from "./pages/CatalogsPage";
import { ItemPage } from "./pages/ItemPage";
import { ItemsPage } from "./pages/ItemsPage";
import { NotFound } from "./pages/NotFound";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 10_000, retry: 1 } },
});

const router = createBrowserRouter(
  [
    {
      element: <Layout />,
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
