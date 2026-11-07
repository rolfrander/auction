import { useState, type ComponentProps } from "react";
import { Button, cx, Input } from "./ui";

/**
 * Edits the image list and picks the main image. Images are URLs for now;
 * drag-and-drop upload arrives with the backend upload endpoint.
 */
export function ImageEditor({
  images,
  mainImage,
  onChange,
}: {
  images: string[];
  mainImage: string | null;
  onChange: (images: string[], mainImage: string | null) => void;
}) {
  const [url, setUrl] = useState("");
  const main = mainImage && images.includes(mainImage) ? mainImage : (images[0] ?? null);

  // Not a <form>: this component lives inside the item form.
  const add = () => {
    const u = url.trim();
    if (!u || images.includes(u)) return;
    onChange([...images, u], main ?? u);
    setUrl("");
  };

  const remove = (img: string) => {
    const next = images.filter((i) => i !== img);
    onChange(next, img === main ? (next[0] ?? null) : main);
  };

  const move = (img: string, delta: number) => {
    const from = images.indexOf(img);
    const to = from + delta;
    if (to < 0 || to >= images.length) return;
    const next = [...images];
    next.splice(to, 0, ...next.splice(from, 1));
    onChange(next, main);
  };

  return (
    <div className="flex flex-col gap-4">
      {images.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {images.map((img, i) => (
            <li
              key={img}
              className={cx(
                "group relative overflow-hidden rounded-lg bg-zinc-100 ring-2",
                img === main ? "ring-indigo-600" : "ring-transparent",
              )}
            >
              <img src={img} alt="" className="aspect-square w-full object-cover" />
              {img === main && (
                <span className="absolute top-2 left-2 rounded-md bg-indigo-600 px-1.5 py-0.5 text-xs font-medium text-white">
                  Hovedbilde
                </span>
              )}
              <div className="absolute inset-x-0 bottom-0 flex items-center gap-1 bg-gradient-to-t from-black/60 to-transparent p-2 pt-6 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100">
                <IconButton label="Flytt til venstre" disabled={i === 0} onClick={() => move(img, -1)}>
                  ←
                </IconButton>
                <IconButton label="Flytt til høyre" disabled={i === images.length - 1} onClick={() => move(img, 1)}>
                  →
                </IconButton>
                {img !== main && (
                  <IconButton label="Bruk som hovedbilde" onClick={() => onChange(images, img)}>
                    ★
                  </IconButton>
                )}
                <IconButton label="Fjern bilde" className="ml-auto" onClick={() => remove(img)}>
                  ✕
                </IconButton>
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <Input
          type="url"
          placeholder="https://…/bilde.jpg"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
        />
        <Button variant="secondary" disabled={!url.trim()} onClick={add}>
          Legg til
        </Button>
      </div>
    </div>
  );
}

function IconButton({
  label,
  className,
  ...props
}: { label: string } & ComponentProps<"button">) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      className={cx(
        "flex size-7 items-center justify-center rounded-md bg-white/90 text-sm text-zinc-800 shadow-sm hover:bg-white disabled:opacity-40",
        className,
      )}
      {...props}
    />
  );
}
