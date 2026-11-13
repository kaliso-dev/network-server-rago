import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { LEGAL_PAGES } from '@/lib/legal';
import styles from './legal.module.css';

export const dynamicParams = false;

export function generateStaticParams() {
  return LEGAL_PAGES.map((page) => ({ slug: page.slug }));
}

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const page = LEGAL_PAGES.find((p) => p.slug === slug);
  return page ? { title: page.title, robots: { index: false } } : {};
}

/**
 * A legal page, not written yet. No legal text is invented: the page says what it will hold and
 * that it is reviewed before the store opens.
 */
export default async function LegalPage({ params }: Props) {
  const { slug } = await params;
  const page = LEGAL_PAGES.find((p) => p.slug === slug);
  if (!page) {
    notFound();
  }
  return (
    <section className={`section ${styles.page}`} aria-labelledby="legal-title">
      <div className="container">
        <p className="eyebrow">Legal</p>
        <h1 id="legal-title" className={styles.title}>
          {page.title}
        </h1>
        <p className={styles.status}>
          <span className="tag tag--red">Being reviewed</span>
        </p>
        <p className="lead">
          This page will say {page.about}. Its text is being written and will be checked before the store opens.
        </p>
        <p className="muted">Questions until then: ask the staff on the Discord of the server.</p>
      </div>
    </section>
  );
}
