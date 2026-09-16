export function Brand({ large = false }: { large?: boolean }) {
  return (
    <div className={`brand ${large ? "brand-large" : ""}`}>
      <img src="/favicon.svg" alt="" />
      <div>
        poscloud<span>ADMINISTRATION</span>
      </div>
    </div>
  );
}
