import { useEffect, useRef, useState, type ComponentProps, type DragEvent } from "react";
import { useUploadImage } from "../queries";
import { cx } from "./ui";

type Upload = { id: number; name: string; preview: string; error?: string };

let nextUploadId = 0;

/**
 * Edits the ordered image list; the first image is the main image.
 * Files dropped or picked are uploaded concurrently and appended as they finish.
 * `onChange` receives an updater so concurrent uploads don't clobber each other.
 */
export function ImageEditor({
  images,
  onChange,
}: {
  images: string[];
  onChange: (update: (images: string[]) => string[]) => void;
}) {
  const upload = useUploadImage();
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  // Revoke preview object URLs on unmount.
  const previews = useRef(new Set<string>());
  useEffect(() => () => previews.current.forEach((u) => URL.revokeObjectURL(u)), []);

  const dropUpload = (u: Upload) => {
    URL.revokeObjectURL(u.preview);
    previews.current.delete(u.preview);
    setUploads((us) => us.filter((x) => x.id !== u.id));
  };

  const addFiles = (files: Iterable<File>) => {
    for (const file of files) {
      if (!file.type.startsWith("image/")) continue;
      const u: Upload = { id: nextUploadId++, name: file.name, preview: URL.createObjectURL(file) };
      previews.current.add(u.preview);
      setUploads((us) => [...us, u]);
      upload.mutateAsync(file).then(
        ({ url }) => {
          onChange((imgs) => [...imgs, url]);
          dropUpload(u);
        },
        (err: unknown) => {
          const error = err instanceof Error ? err.message : String(err);
          setUploads((us) => us.map((x) => (x.id === u.id ? { ...x, error } : x)));
        },
      );
    }
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    addFiles(e.dataTransfer.files);
  };

  const remove = (img: string) => onChange((imgs) => imgs.filter((i) => i !== img));

  const moveTo = (from: number, to: number) => {
    if (to < 0 || to >= images.length) return;
    onChange((imgs) => {
      const next = [...imgs];
      next.splice(to, 0, ...next.splice(from, 1));
      return next;
    });
  };

  return (
    <div className="flex flex-col gap-4">
      {(images.length > 0 || uploads.length > 0) && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {images.map((img, i) => (
            <li
              key={img}
              className={cx(
                "group relative overflow-hidden rounded-lg bg-zinc-100 ring-2",
                i === 0 ? "ring-indigo-600" : "ring-transparent",
              )}
            >
              <img src={img} alt="" className="aspect-square w-full object-cover" />
              {i === 0 && (
                <span className="absolute top-2 left-2 rounded-md bg-indigo-600 px-1.5 py-0.5 text-xs font-medium text-white">
                  Hovedbilde
                </span>
              )}
              <div className="absolute inset-x-0 bottom-0 flex items-center gap-1 bg-gradient-to-t from-black/60 to-transparent p-2 pt-6 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100">
                <IconButton label="Flytt til venstre" disabled={i === 0} onClick={() => moveTo(i, i - 1)}>
                  ←
                </IconButton>
                <IconButton label="Flytt til høyre" disabled={i === images.length - 1} onClick={() => moveTo(i, i + 1)}>
                  →
                </IconButton>
                {i !== 0 && (
                  <IconButton label="Bruk som hovedbilde" onClick={() => moveTo(i, 0)}>
                    ★
                  </IconButton>
                )}
                <IconButton label="Fjern bilde" className="ml-auto" onClick={() => remove(img)}>
                  ✕
                </IconButton>
              </div>
            </li>
          ))}
          {uploads.map((u) => (
            <li key={`upload-${u.id}`} className="relative overflow-hidden rounded-lg bg-zinc-100" title={u.name}>
              <img src={u.preview} alt="" className="aspect-square w-full object-cover opacity-50" />
              {u.error ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-red-50/80 p-2 text-center text-xs text-red-700">
                  <span>Opplasting feilet: {u.error}</span>
                  <IconButton label="Fjern" onClick={() => dropUpload(u)}>
                    ✕
                  </IconButton>
                </div>
              ) : (
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="size-8 animate-spin rounded-full border-4 border-white/70 border-t-indigo-600" />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <div
        role="button"
        tabIndex={0}
        onClick={() => fileInput.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            fileInput.current?.click();
          }
        }}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        className={cx(
          "flex cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed px-4 py-8 text-center text-sm transition-colors",
          dragOver ? "border-indigo-600 bg-indigo-50 text-indigo-700" : "border-zinc-300 text-zinc-500 hover:border-zinc-400",
        )}
      >
        <span className="font-medium text-zinc-700">
          <span className="hidden sm:inline">Slipp bilder her eller </span>
          <span className="text-indigo-600">velg filer</span>
        </span>
        <span className="text-xs">JPEG, PNG, WebP, GIF, HEIC – flere om gangen</span>
        <input
          ref={fileInput}
          type="file"
          accept="image/*"
          multiple
          hidden
          onChange={(e) => {
            if (e.target.files) addFiles(e.target.files);
            e.target.value = "";
          }}
        />
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
