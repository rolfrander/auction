const nok = new Intl.NumberFormat("nb-NO", { style: "currency", currency: "NOK", maximumFractionDigits: 0 });

export function formatPrice(n: number | null | undefined): string {
  return n == null ? "–" : nok.format(n);
}

export function formatRange(low: number | null, high: number | null): string {
  if (low == null && high == null) return "–";
  if (low == null || high == null || low === high) return formatPrice(low ?? high);
  return `${formatPrice(low)} – ${formatPrice(high)}`;
}

export function formatDate(iso: string | null): string {
  if (!iso) return "Ingen dato";
  return new Date(`${iso}T00:00:00`).toLocaleDateString("nb-NO", { day: "numeric", month: "long", year: "numeric" });
}
