import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Link } from "react-router";
import type { Item } from "../../api";
import { formatPrice, formatRange } from "../../format";
import { Button, cx, Thumb } from "./ui";

export function SortableItemList({
  items,
  onReorder,
  onRemove,
}: {
  items: Item[];
  onReorder: (items: Item[]) => void;
  onRemove: (item: Item) => void;
}) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const from = items.findIndex((i) => i.id === active.id);
    const to = items.findIndex((i) => i.id === over.id);
    onReorder(arrayMove(items, from, to));
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
      <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
        <ol className="divide-y divide-zinc-100">
          {items.map((item, index) => (
            <SortableRow key={item.id} item={item} index={index} onRemove={() => onRemove(item)} />
          ))}
        </ol>
      </SortableContext>
    </DndContext>
  );
}

function SortableRow({ item, index, onRemove }: { item: Item; index: number; onRemove: () => void }) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
  });

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cx(
        "relative flex items-center gap-3 bg-white px-2 py-2.5 sm:px-4",
        isDragging && "z-10 rounded-lg shadow-lg ring-1 ring-zinc-200",
      )}
    >
      <button
        ref={setActivatorNodeRef}
        {...attributes}
        {...listeners}
        className="cursor-grab touch-none rounded p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 active:cursor-grabbing"
        aria-label={`Flytt ${item.title}`}
      >
        <svg viewBox="0 0 20 20" fill="currentColor" className="size-4">
          <circle cx="7" cy="5" r="1.5" />
          <circle cx="13" cy="5" r="1.5" />
          <circle cx="7" cy="10" r="1.5" />
          <circle cx="13" cy="10" r="1.5" />
          <circle cx="7" cy="15" r="1.5" />
          <circle cx="13" cy="15" r="1.5" />
        </svg>
      </button>
      <span className="w-6 text-right text-sm tabular-nums text-zinc-400">{index + 1}</span>
      <Thumb src={item.images[0]} className="size-12 shrink-0" />
      <div className="min-w-0 flex-1">
        <Link to={`/items/${item.id}`} className="block truncate font-medium text-zinc-900 hover:text-indigo-600">
          {item.title}
        </Link>
        <p className="truncate text-sm text-zinc-500">
          {formatRange(item.priceLow, item.priceHigh)}
          <span className="hidden sm:inline"> · Start {formatPrice(item.startingPrice)}</span>
        </p>
      </div>
      <Button variant="ghost" size="sm" onClick={onRemove} aria-label={`Fjern ${item.title} fra katalogen`}>
        Fjern
      </Button>
    </li>
  );
}
