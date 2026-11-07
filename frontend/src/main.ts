import { getHealth } from "./api";

const statusEl = document.querySelector<HTMLSpanElement>("#status")!;

getHealth()
  .then((h) => {
    statusEl.textContent = h.status;
  })
  .catch((err: unknown) => {
    statusEl.textContent = `unreachable (${String(err)})`;
  });
