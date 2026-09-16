import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
export function Dialog({
  open,
  title,
  children,
  onClose,
  drawer = false,
  fullPage = false,
  busy = false,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  drawer?: boolean;
  fullPage?: boolean;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (open && !ref.current?.open) ref.current?.showModal();
    else if (!open && ref.current?.open) ref.current.close();
  }, [open]);
  const body = (
    <div className="dialog-body">
      <div className="dialog-heading">
        <h2>{title}</h2>
        <button
          className="icon-button"
          aria-label={fullPage ? "Close full-screen operation" : "Close dialog"}
          disabled={busy}
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </div>
  );
  if (fullPage)
    return (
      <section className="full-page-action" aria-label={title} hidden={!open}>
        {body}
      </section>
    );
  return (
    <dialog
      ref={ref}
      className={`dialog${drawer ? " drawer" : ""}`}
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      {body}
    </dialog>
  );
}
