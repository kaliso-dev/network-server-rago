import type { Rank } from '@/lib/game';
import { formatEuros } from '@/lib/game';
import { McText } from './McText';
import styles from './RankCard.module.css';

/**
 * A rank of the web store, for display only: its tag and name as the game shows them, its price
 * and the perks of config.yml (rangs:), word for word. No buy button until the web store exists.
 */
export function RankCard({ rank, shown = 5, featured = false }: { rank: Rank; shown?: number; featured?: boolean }) {
  const perks = rank.perks.slice(0, shown);
  const more = rank.perks.length - perks.length;
  const premium = rank.id === 'premium';
  return (
    <article className={`card rank${featured ? ' rank--featured' : ''}`} aria-labelledby={`rank-${rank.id}`}>
      <p className="eyebrow" style={{ color: rank.colour ?? undefined }}>
        Rank · one-time payment
      </p>
      <h3 className="rank__name" id={`rank-${rank.id}`}>
        {rank.name}
      </h3>
      <p className={`mc ${styles.preview}`} aria-label={`How your name looks with ${rank.name}`}>
        <McText segments={rank.prefix} ground="#181b20" />{' '}
        {premium ? (
          <>
            <span className="mc-name--premium">YourName</span> <span className="mc-pink">✦</span>
          </>
        ) : (
          <span style={{ color: rank.colour ?? undefined }}>YourName</span>
        )}
      </p>
      <p className="rank__price">
        <span className="rank__amount">{formatEuros(rank.priceAmount)}</span>
        <span className="muted">once, not a subscription</span>
      </p>
      <ul className="rank__perks">
        {perks.map((perk) => (
          <li key={perk}>{perk}</li>
        ))}
      </ul>
      {more > 0 ? <p className={styles.more}>And {more} more perks, listed in the game&apos;s shop.</p> : null}
    </article>
  );
}
