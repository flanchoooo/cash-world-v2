import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  CheckCircle2,
  HandCoins,
  Phone,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { Details } from "../components/Operations";
import { api } from "../lib/session";
import type { Row } from "../lib/operations";

export function CashOut() {
  const client = useQueryClient();
  const key = useRef(crypto.randomUUID());
  const [recipientMobile, setRecipientMobile] = useState("");
  const [collectionCode, setCollectionCode] = useState("");
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Row | null>(null);
  const [preview, setPreview] = useState<Row | null>(null);

  async function prepareReview() {
    setBusy(true);
    setError("");
    try {
      const response = await api<Row>("/api/remittances/cash-out/preview", {
        method: "POST",
        body: JSON.stringify({
          recipientMobile: recipientMobile.trim(),
          cashOutCode: collectionCode,
        }),
      });
      setPreview(response);
      setReview(true);
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "The remittance details could not be verified.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const response = await api<Row>("/api/remittances/cash-out", {
        method: "POST",
        headers: { "Idempotency-Key": key.current },
        body: JSON.stringify({
          recipientMobile: recipientMobile.trim(),
          cashOutCode: collectionCode,
        }),
      });
      setResult(response);
      await client.invalidateQueries();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "The cash payout could not be completed.",
      );
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    key.current = crypto.randomUUID();
    setRecipientMobile("");
    setCollectionCode("");
    setReview(false);
    setError("");
    setResult(null);
    setPreview(null);
  }

  return (
    <div className="cashout-page">
      <div className="page-heading">
        <div className="breadcrumb">Payments / Cash out</div>
        <h1>Cash out</h1>
        <p>Verify the recipient and release an available remittance in cash.</p>
      </div>

      <section className="cashout-panel">
        <div className="cashout-panel-heading">
          <span className="cashout-icon">
            <HandCoins size={25} />
          </span>
          <div>
            <span className="eyebrow">SECURE CASH PAYOUT</span>
            <h2>Recipient verification</h2>
            <p>Both details must match the remittance before payout.</p>
          </div>
          <ShieldCheck size={22} className="cashout-shield" />
        </div>

        {result ? (
          <div className="cashout-result">
            <div className="operation-success">
              <CheckCircle2 size={22} />
              <p>Cash payout completed successfully.</p>
            </div>
            {preview && <CashOutSummary preview={preview} />}
            <Details row={result} />
            <div className="operation-footer">
              <button className="button primary" onClick={reset}>
                New cash out
              </button>
            </div>
          </div>
        ) : (
          <form
            className="cashout-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (review) void submit();
              else void prepareReview();
            }}
          >
            {error && (
              <p className="operation-error" role="alert">
                {error}
              </p>
            )}
            {review ? (
              <div className="cashout-review">
                <p>Confirm the exchange details before releasing cash.</p>
                {preview && (
                  <>
                    <RecipientConfirmation preview={preview} />
                    <CashOutSummary preview={preview} />
                  </>
                )}
              </div>
            ) : (
              <div className="cashout-fields">
                <label>
                  <span>Recipient mobile</span>
                  <input
                    required
                    inputMode="tel"
                    autoComplete="off"
                    value={recipientMobile}
                    onChange={(event) => setRecipientMobile(event.target.value)}
                  />
                </label>
                <label>
                  <span>6-digit cash-out code</span>
                  <input
                    required
                    inputMode="numeric"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    autoComplete="one-time-code"
                    value={collectionCode}
                    onChange={(event) =>
                      setCollectionCode(
                        event.target.value.replace(/\D/g, "").slice(0, 6),
                      )
                    }
                  />
                  <small>
                    Enter the cash-out code supplied to the customer.
                  </small>
                </label>
              </div>
            )}
            <div className="operation-footer">
              {review && (
                <button
                  type="button"
                  className="button secondary"
                  disabled={busy}
                  onClick={() => setReview(false)}
                >
                  Edit details
                </button>
              )}
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={reset}
              >
                Clear
              </button>
              <button className="button primary" disabled={busy}>
                {busy ? "Processing…" : review ? "Confirm cash out" : "Review"}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}

function RecipientConfirmation({ preview }: { preview: Row }) {
  return (
    <section className="cashout-recipient-confirmation">
      <div className="cashout-recipient-heading">
        <span className="cashout-recipient-icon">
          <UserRound size={21} />
        </span>
        <div>
          <span>Recipient details</span>
          <strong>{String(preview.recipientName ?? "-")}</strong>
        </div>
      </div>
      <div className="cashout-recipient-mobile">
        <Phone size={17} />
        <div>
          <span>Mobile number</span>
          <strong>{String(preview.recipientMobile ?? "-")}</strong>
        </div>
      </div>
      <div className="cashout-recipient-id">
        <span>National ID</span>
        <strong>{String(preview.recipientNationalId ?? "-")}</strong>
      </div>
    </section>
  );
}

function CashOutSummary({ preview }: { preview: Row }) {
  return (
    <section className="cashout-fx-summary">
      <div>
        <span>Sender paid</span>
        <strong>
          {String(preview.senderCurrency)} {String(preview.senderAmount)}
        </strong>
        <small>
          Fee {String(preview.senderFee)} · Total{" "}
          {String(preview.totalCollected)}
        </small>
      </div>
      <div className="cashout-rate">
        <span>Exchange rate</span>
        <strong>{String(preview.exchangeRate)}</strong>
      </div>
      <div className="cashout-payout">
        <span>Pay recipient</span>
        <strong>
          {String(preview.recipientCurrency)} {String(preview.amountToPayOut)}
        </strong>
        <small>
          Converted fee equivalent {String(preview.recipientCurrency)}{" "}
          {String(preview.convertedFee)}
        </small>
      </div>
    </section>
  );
}
