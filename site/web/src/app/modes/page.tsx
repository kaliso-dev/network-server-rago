import type { Metadata } from 'next';
import Link from 'next/link';
import { ItemMenu, type MenuItem } from '@/components/ItemMenu';
import { McItem } from '@/components/McItem';
import { McText } from '@/components/McText';
import { SectionHeading } from '@/components/ui';
import { readable } from '@/lib/colour';
import { FLAGSHIPS, flagshipColour, flagshipModes } from '@/lib/flagship';
import { categoryOf, eventModes, game, playableModes, type Mode } from '@/lib/game';
import styles from './modes.module.css';

export const metadata: Metadata = {
  title: 'Game modes',
  description: `The ${playableModes().length} modes of Rago Practice, from the six built around the moments that decide a game to the duels, arenas and Ranked Bedwars.`,
  alternates: { canonical: '/modes' },
};


function modeItem(mode: Mode): MenuItem {
  const flagship = FLAGSHIPS.find((f) => f.modes.includes(mode.id));
  const tags = [mode.format, mode.ranked ? 'Ranked: ELO' : null, mode.startHere ? '✦ Start here' : null].filter(
    (tag): tag is string => Boolean(tag),
  );
  return {
    id: mode.id,
    icon: mode.icon ?? 'BOOK',
    name: mode.name,
    colour: mode.colour,
    lore: [tags.join(' · '), mode.description],
    detail: (
      <div className={styles.detail}>
        <span className="slot" aria-hidden="true">
          <McItem material={mode.icon ?? 'BOOK'} size={32} />
        </span>
        <div>
          <h3 style={{ color: mode.colour ? readable(mode.colour, '#121418') : undefined }}>{mode.name}</h3>
          <p className={styles.tags}>{tags.join(' · ')}</p>
          <p>{mode.description}</p>
          {flagship ? (
            <Link href={`/modes/${flagship.slug}`} className={styles.more}>
              All about {flagship.name} <span aria-hidden="true">→</span>
            </Link>
          ) : null}
        </div>
      </div>
    ),
  };
}

export default function ModesPage() {
  const events = eventModes();
  return (
    <>
      <section className={styles.hero} aria-labelledby="modes-title">
        <div className="container">
          <p className="eyebrow">
            {playableModes().length} modes · {game.categories.length} ways to play · all free
          </p>
          <h1 id="modes-title" className="display">
            Game modes
          </h1>
          <p className="lead">
            Six modes built around the moments that decide a game come first. Then every other way to play, as the
            compass of the lobby shows them: hover an item for its tooltip, pick it to read more.
          </p>
          <nav className={styles.jump} aria-label="On this page">
            <a href="#flagships">The six</a>
            {game.categories.map((category) => (
              <a key={category.id} href={`#${category.id}`} style={{ color: category.colour ? readable(category.colour) : undefined }}>
                {category.name}
              </a>
            ))}
            {events.length ? <a href="#events">Events</a> : null}
          </nav>
        </div>
      </section>

      <section className="section" aria-labelledby="flagships">
        <div className="container">
          <SectionHeading
            id="flagships"
            eyebrow="The modes we built"
            title="Six moments of a real game, on repeat"
          />
          <ol className={styles.flagships}>
            {FLAGSHIPS.map((flagship, index) => {
              const colour = readable(flagshipColour(flagship), '#0c0d10');
              const formats = [...new Set(flagshipModes(flagship).map((mode) => mode.format))].join(' · ');
              return (
                <li key={flagship.slug} className={styles.flagship} data-flip={index % 2 === 1 || undefined} style={{ ['--accent' as string]: colour }}>
                  <div className={styles.art} aria-hidden="true">
                    <McItem material={flagship.icon} size={160} className={styles.bigItem} />
                  </div>
                  <div className={styles.copy}>
                    <p className={styles.kicker}>
                      {categoryOf(flagship.category).name} · {formats}
                    </p>
                    <h3 className={styles.name}>{flagship.name}</h3>
                    <p className={styles.hook}>{flagship.hook}</p>
                    <p className={styles.summary}>{flagship.summary}</p>
                    <dl className={styles.facts}>
                      {flagship.facts.map((fact) => (
                        <div key={fact.label}>
                          <dt>{fact.label}</dt>
                          <dd>{fact.value}</dd>
                        </div>
                      ))}
                    </dl>
                    <Link href={`/modes/${flagship.slug}`} className="btn btn--secondary btn--sm">
                      How {flagship.name} plays
                    </Link>
                  </div>
                </li>
              );
            })}
          </ol>
        </div>
      </section>

      {game.categories.map((category) => {
        const modes = playableModes().filter((mode) => mode.category === category.id);
        return (
          <section key={category.id} className={`section ${styles.category}`} aria-labelledby={category.id}>
            <div className="container">
              <SectionHeading
                id={category.id}
                eyebrow={`${modes.length} modes`}
                title={<McText segments={[{ text: category.name, colour: category.colour }]} />}
                lead={category.description}
              />
              <ItemMenu
                title={category.name}
                items={modes.map(modeItem)}
                columns={9}
                initial={modes[0]?.id}
                label={`The modes of ${category.name}: pick one to read about it`}
                side
              />
            </div>
          </section>
        );
      })}

      {events.length ? (
        <section className="section" aria-labelledby="events">
          <div className="container">
            <SectionHeading
              id="events"
              eyebrow="Events"
              title="Everyone at once"
              lead={`Events have no queue: the server starts one when ${game.events.lobbyPlayers} players are free in the lobby and none ran for ${game.events.everyMinutes} minutes, and PREMIUM+ players host one with /host. Up to ${game.events.maxPlayers} players.`}
            />
            <ul className={styles.events}>
              {events.map((mode) => (
                <li key={mode.id} className="card">
                  <McItem material={mode.icon ?? 'BOOK'} size={40} />
                  <div>
                    <h3>{mode.name}</h3>
                    <p>{mode.description}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>
      ) : null}
    </>
  );
}
