import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { IpChip } from '@/components/IpChip';
import { ItemMenu } from '@/components/ItemMenu';
import { McItem } from '@/components/McItem';
import { ItemViewer } from '@/components/three/ItemViewer';
import { SkinViewer } from '@/components/three/SkinViewer';
import { ButtonLink } from '@/components/ui';
import { readable } from '@/lib/colour';
import { FLAGSHIPS, flagshipColour, flagshipModes, flagshipOf, type Flagship } from '@/lib/flagship';
import { categoryOf, game } from '@/lib/game';
import { settings } from '@/lib/settings';
import styles from './flagship.module.css';

export const dynamicParams = false;

export function generateStaticParams() {
  return FLAGSHIPS.map((flagship) => ({ slug: flagship.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const flagship = flagshipOf((await params).slug);
  if (!flagship) {
    return {};
  }
  return {
    title: flagship.name,
    description: `${flagship.hook} ${flagship.summary}`,
    alternates: { canonical: `/modes/${flagship.slug}` },
  };
}

/** What only this mode has: the kits of Attack and Defense. */
function Extra({ flagship }: { flagship: Flagship }) {
  if (flagship.slug === 'attack-and-defense') {
    const kits = (side: 'attack' | 'defense') =>
      game.attackDefense[side].map((kit) => ({
        id: `${side}-${kit.name}`,
        icon: kit.icon,
        name: kit.name,
        colour: side === 'attack' ? '#FF5555' : '#55FF55',
        lore: [kit.description],
        detail: (
          <p className={styles.kit}>
            <strong>{kit.name}</strong>: {kit.description}
          </p>
        ),
      }));
    return (
      <section className="section" aria-labelledby="kits">
        <div className="container">
          <h2 id="kits" className={styles.heading}>
            The kits
          </h2>
          <p className={styles.lead}>Before each round, each player picks the kit of their side, in this menu.</p>
          <div className={styles.kits}>
            <ItemMenu title="Attack" items={kits('attack')} columns={3} initial={`attack-${game.attackDefense.attack[0]?.name}`} label="The attack kits" />
            <ItemMenu title="Defense" items={kits('defense')} columns={3} initial={`defense-${game.attackDefense.defense[0]?.name}`} label="The defense kits" />
          </div>
        </div>
      </section>
    );
  }
  return null;
}

export default async function FlagshipPage({ params }: { params: Promise<{ slug: string }> }) {
  const flagship = flagshipOf((await params).slug);
  if (!flagship) {
    notFound();
  }
  const colour = readable(flagshipColour(flagship), '#0c0d10');
  const category = categoryOf(flagship.category);
  const modes = flagshipModes(flagship);
  const others = FLAGSHIPS.filter((f) => f.slug !== flagship.slug);
  const address = settings.serverAddress;
  const soon = settings.state === 'soon';

  return (
    <div style={{ ['--accent' as string]: colour }}>
      <section className={styles.hero} aria-labelledby="mode-title">
        <div className={`container ${styles.heroGrid}`}>
          <div className={styles.heroText}>
            <nav aria-label="Breadcrumb" className={styles.crumbs}>
              <Link href="/modes">Game modes</Link> <span aria-hidden="true">/</span>{' '}
              <span aria-current="page">{flagship.name}</span>
            </nav>
            <p className={styles.kicker}>
              {category.name} · {[...new Set(modes.map((mode) => mode.format))].join(' · ')}
            </p>
            <h1 id="mode-title" className={`display ${styles.title}`}>
              {flagship.name}
            </h1>
            <p className={styles.hook}>{flagship.hook}</p>
            <p className="lead">{flagship.summary}</p>
            <dl className={styles.facts}>
              {flagship.facts.map((fact) => (
                <div key={fact.label}>
                  <dt>{fact.label}</dt>
                  <dd>{fact.value}</dd>
                </div>
              ))}
            </dl>
            <p className={styles.where}>
              <McItem material="COMPASS" size={24} /> {flagship.where}
            </p>
          </div>
          <div className={styles.heroArt}>
            {flagship.skin ? (
              <SkinViewer skin={flagship.skin} label={`The ${flagship.name} character of the lobby, in its skin`} className={styles.skin} angle={0.45} />
            ) : null}
            <ItemViewer
              material={flagship.icon}
              label={`The item of ${flagship.name} in the compass of the lobby`}
              className={flagship.skin ? styles.itemSmall : styles.item}
            />
          </div>
        </div>
      </section>

      <section className="section" aria-labelledby="how">
        <div className="container">
          <h2 id="how" className={styles.heading}>
            How a game goes
          </h2>
          <ol className={styles.steps}>
            {flagship.steps.map((step, index) => (
              <li key={step.title}>
                <span className={styles.number} aria-hidden="true">
                  {index + 1}
                </span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <Extra flagship={flagship} />

      <section className={`section ${styles.band}`} aria-labelledby="in-game">
        <div className="container">
          <h2 id="in-game" className={styles.heading}>
            In the compass
          </h2>
          <ul className={styles.modes}>
            {modes.map((mode) => (
              <li key={mode.id}>
                <span className="slot" aria-hidden="true">
                  <McItem material={mode.icon ?? flagship.icon} size={32} />
                </span>
                <div>
                  <h3 style={{ color: mode.colour ? readable(mode.colour, '#121418') : undefined }}>{mode.name}</h3>
                  <p className={styles.modeMeta}>
                    {mode.format} · {categoryOf(mode.category).name}
                  </p>
                  <p>{mode.description}</p>
                </div>
              </li>
            ))}
          </ul>
          <div className={styles.play}>
            {address ? <IpChip address={address} /> : null}
            {soon ? (
              <ButtonLink href={settings.discordUrl} variant="primary">
                Join the Discord
              </ButtonLink>
            ) : (
              <ButtonLink href="/#join" variant="primary">
                Play now
              </ButtonLink>
            )}
          </div>
        </div>
      </section>

      <section className="section" aria-labelledby="others">
        <div className="container">
          <h2 id="others" className={styles.heading}>
            The other five
          </h2>
          <ul className={styles.others}>
            {others.map((other) => (
              <li key={other.slug}>
                <Link href={`/modes/${other.slug}`} style={{ ['--tab' as string]: readable(flagshipColour(other), '#121418') }}>
                  <span className="slot" aria-hidden="true">
                    <McItem material={other.icon} size={32} />
                  </span>
                  <span>
                    <strong>{other.name}</strong>
                    <span>{other.hook}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  );
}
