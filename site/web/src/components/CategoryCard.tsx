import Image from 'next/image';
import type { Category, Mode } from '@/lib/game';
import { McText } from './McText';
import { CATEGORY_TAG, Tag } from './ui';
import styles from './CategoryCard.module.css';

/**
 * A category of the compass (Practice, RBW Modes, Duels, Arenas), carried by the NPC that opens it
 * in the lobby: its skin, its slogan and description from config.yml, the real number of modes.
 * The skin is served as rendered (unoptimized), so its pixels stay sharp.
 */
export function CategoryCard({ category, modes }: { category: Category; modes: Mode[] }) {
  return (
    <article className={`card ${styles.card}`} aria-labelledby={`category-${category.id}`}>
      {category.skin ? (
        <div className={styles.npc} style={{ ['--accent' as string]: category.colour ?? '#ffffff' }}>
          <Image
            src={`/skins/${category.skin}.png`}
            alt={`The ${category.name} character of the lobby`}
            width={96}
            height={192}
            unoptimized
            className={styles.skin}
          />
        </div>
      ) : null}
      <div className={styles.body}>
        <div className={styles.top}>
          <Tag variant={CATEGORY_TAG[category.id]}>{category.name}</Tag>
          <span className={styles.count}>
            {category.modes} {category.modes === 1 ? 'mode' : 'modes'}
          </span>
        </div>
        <h3 id={`category-${category.id}`} className={styles.slogan}>
          <McText segments={category.slogan} ground="#181b20" />
        </h3>
        <p className={styles.description}>{category.description}</p>
        <ul className={styles.modes} aria-label={`Some modes of ${category.name}`}>
          {modes.map((mode) => (
            <li key={mode.id}>{mode.name}</li>
          ))}
        </ul>
      </div>
    </article>
  );
}
