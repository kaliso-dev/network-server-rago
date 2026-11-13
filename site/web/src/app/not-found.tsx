import { ButtonLink } from '@/components/ui';

export default function NotFound() {
  return (
    <section className="section" aria-labelledby="not-found-title">
      <div className="container" style={{ display: 'grid', gap: 'var(--space-4)', justifyItems: 'start' }}>
        <p className="eyebrow">Error 404</p>
        <h1 id="not-found-title" style={{ fontSize: 'var(--text-h1)' }}>
          This page does not exist
        </h1>
        <p className="lead" style={{ margin: 0 }}>
          The link may be old, or mistyped.
        </p>
        <ButtonLink href="/" variant="primary">
          Back to the home page
        </ButtonLink>
      </div>
    </section>
  );
}
