export function Brand({ large = false }: { large?: boolean }) {
  return (
    <div className={`brand ${large ? "brand-large" : ""}`}>
      <img src="/favicon.svg" alt="" />
      <div>
        cashword<span>ADMINISTRATION</span>
      </div>
    </div>
  );
}
