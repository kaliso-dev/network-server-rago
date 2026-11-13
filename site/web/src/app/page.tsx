import Image from 'next/image';
import Link from 'next/link';
import { DivisionLadder } from '@/components/DivisionLadder';
import { IpChip } from '@/components/IpChip';
import { ItemMenu, type MenuItem } from '@/components/ItemMenu';
import { McItem } from '@/components/McItem';
import { McText, mc } from '@/components/McText';
import { ModeShowcase, type ShowcaseMode } from '@/components/ModeShowcase';
import { RankCard } from '@/components/RankCard';
import { SkinViewer } from '@/components/three/SkinViewer';
import { ButtonLink, SectionHeading, ServerStatus } from '@/components/ui';
import { FLAGSHIPS, flagshipColour, flagshipModes } from '@/lib/flagship';
import { categoryOf, formatEuros, formatNumber, game, playableModes, type Cosmetic } from '@/lib/game';
import { settings } from '@/lib/settings';
import styles from './page.module.css';

const list = (names: string[]): string =>
  names.length <= 1 ? (names[0] ?? '') : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;

const COSMETIC_KIND: Record<string, string> = { trail: 'Trail', kill: 'Kill effect', win: 'Victory dance' };

function cosmeticItem(cosmetic: Cosmetic): MenuItem {
  const kind = COSMETIC_KIND[cosmetic.type] ?? cosmetic.type;
  const how = cosmetic.premium
    ? 'Comes with PREMIUM+'
    : `${formatNumber(cosmetic.price)} Rago Points, from level ${cosmetic.level}`;
  return {
    id: cosmetic.id,
    icon: cosmetic.icon,
    name: cosmetic.name,
    colour: cosmetic.premium ? '#FF55FF' : '#55FF55',
    lore: [kind, how],
    detail: (
      <p className={styles.cosmeticDetail}>
        <strong>{cosmetic.name}</strong> · {kind}. {how}.
      </p>
    ),
  };
}

export default function Home() {
  const soon = settings.state === 'soon';
  const address = settings.serverAddress;
  const version = settings.minecraftVersion;
  const divisions = game.ranked.divisions;
  const first = divisions[0]!;
  const last = divisions[divisions.length - 1]!;
  const modeCount = playableModes().length;
  const trainings = playableModes().filter((mode) => mode.category === 'entrainement').length;
  const practice = categoryOf('entrainement');
  const rankedFormats = game.modes.filter((mode) => mode.ranked).map((mode) => mode.format);
  const pack = game.starterPack;
  const queue = game.ranked.queue;

  const showcase: ShowcaseMode[] = FLAGSHIPS.map((flagship) => ({
    slug: flagship.slug,
    name: flagship.name,
    icon: flagship.icon,
    colour: flagshipColour(flagship),
    category: categoryOf(flagship.category).name,
    formats: [...new Set(flagshipModes(flagship).map((mode) => mode.format))].join(' · '),
    hook: flagship.hook,
    summary: flagship.summary,
    steps: flagship.steps,
    where: flagship.where,
  }));

  // what search engines read about the site and its organisation (schema.org)
  const structuredData = [
    {
      '@context': 'https://schema.org',
      '@type': 'Organization',
      name: 'Rago Practice',
      url: settings.siteUrl.toString(),
      logo: new URL('/brand/icon-512.png', settings.siteUrl).toString(),
      sameAs: [settings.discordUrl],
    },
    {
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: 'Rago Practice',
      url: settings.siteUrl.toString(),
    },
  ];

  const discord = (primary: boolean) => (
    <ButtonLink href={settings.discordUrl} variant={primary ? 'primary' : 'secondary'}>
      Join the Discord
    </ButtonLink>
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, '\\u003c') }}
      />

      {/* 1. Enter: the lobby */}
      <section className={styles.hero} aria-labelledby="hero-title">
        <div className={`container ${styles.heroGrid}`}>
          <div className={styles.heroText}>
            <h1 id="hero-title" className={styles.title}>
              <span className={styles.brand}>Rago Practice</span>
              <span className={`display ${styles.tagline}`}>Master your skills.</span>
            </h1>
            <p className={`lead ${styles.lead}`}>
              Practice modes built from the moments that decide a real game, Hypixel-like knockback we measured
              ourselves, and a server in Europe, close to its players.
            </p>
            <div className={styles.actions}>
              {soon ? (
                discord(true)
              ) : (
                <ButtonLink href="#join" variant="primary">
                  Play now
                </ButtonLink>
              )}
              <ButtonLink href="/modes">Explore game modes</ButtonLink>
            </div>
            <div className={styles.meta}>
              <ServerStatus state={settings.state} />
              <span className="tag">Java {version}</span>
              <span className="tag">Europe</span>
              <span className="tag">Every mode free</span>
            </div>
            {address ? <IpChip address={address} /> : <p className={styles.note}>The server address comes with the opening.</p>}
          </div>
          <div className={styles.heroArt}>
            <SkinViewer
              skin={practice.skin ?? 'practice'}
              label={`The ${practice.name} character of the lobby, in its skin`}
              className={styles.npc}
              nametag={
                <>
                  <span>
                    <McText segments={mc('&b&lPractice')} ground="#000000" />
                  </span>
                  <span>
                    <McText segments={practice.slogan} ground="#000000" />
                  </span>
                </>
              }
            />
            <aside className={`scoreboard ${styles.scoreboard}`} aria-label="Rago Practice in numbers">
              <p className="scoreboard__title">
                <McText segments={mc('&c&lRAGO PRACTICE')} ground="#000000" />
              </p>
              <ul>
                <li>
                  <span className="mc-white">Modes: </span>
                  <span className="mc-green">{modeCount}</span>
                </li>
                <li>
                  <span className="mc-white">Trainings: </span>
                  <span className="mc-aqua">{trainings}</span>
                </li>
                <li>
                  <span className="mc-white">Divisions: </span>
                  <span className="mc-gold">{divisions.length}</span>
                </li>
                <li>
                  <span className="mc-white">Version: </span>
                  <span className="mc-grey">{version}</span>
                </li>
                <li>
                  <span className="mc-white">Server: </span>
                  {soon ? <span className="mc-yellow">Opening soon</span> : <span className="mc-green">Open</span>}
                </li>
              </ul>
            </aside>
          </div>
        </div>
      </section>

      {/* 2. Discover: the six modes first */}
      <section className={`section ${styles.modes}`} aria-labelledby="modes">
        <div className="container">
          <SectionHeading
            id="modes"
            eyebrow="The modes we built"
            title="Practise the moments that decide a game"
            lead="Six modes that put you in one precise situation of a real game, again and again, until you win it."
          />
          <ModeShowcase modes={showcase} />
        </div>
      </section>

      {/* 3. Everything else, by the four NPCs of the lobby */}
      <section className="section" aria-labelledby="play">
        <div className="container">
          <SectionHeading
            id="play"
            eyebrow={`${modeCount} modes, ${game.categories.length} ways to play`}
            title="And every other way to play"
            lead="In the lobby, four characters open the modes, as the compass does. Every mode is free."
          />
          <ul className={styles.npcs}>
            {game.categories.map((category) => (
              <li key={category.id}>
                <Link href={`/modes#${category.id}`} className={styles.npcCard} style={{ ['--accent' as string]: category.colour ?? '#fff' }}>
                  <span className={styles.npcName}>
                    <McText segments={[{ text: category.name, colour: category.colour, bold: true }]} ground="#000000" />
                  </span>
                  <Image
                    src={`/skins/${category.skin}.png`}
                    alt=""
                    width={96}
                    height={192}
                    unoptimized
                    className={styles.npcSkin}
                  />
                  <span className={styles.npcSlogan}>
                    <McText segments={category.slogan} ground="#121418" />
                  </span>
                  <span className={styles.npcCount}>
                    {category.modes} modes
                    <span aria-hidden="true"> →</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* 4. Combat: the knockback and the server in Europe */}
      <section className={`section ${styles.band}`} aria-labelledby="combat">
        <div className="container">
          <SectionHeading
            id="combat"
            eyebrow="Combat"
            title="Hypixel-like knockback, measured by us"
            lead="The feel of a fight comes from numbers we recorded ourselves, hit by hit, then rebuilt on our server."
          />
          <div className={styles.combat}>
            <ol className={styles.method}>
              <li>
                <McItem material="BOOK" size={40} />
                <h3>Recorded</h3>
                <p>
                  Two players fought on Hypixel with our recorder mod, each logging every hit they took: in Housing,
                  and in a Bed Wars 1v1.
                </p>
              </li>
              <li>
                <McItem material="DIAMOND_SWORD" size={40} />
                <h3>Rebuilt</h3>
                <p>
                  Our combat engine starts from the exact knockback of Minecraft 1.8.8 and adds what the recordings
                  show: a little more vertical push, and 9 ticks between two hits instead of 10.
                </p>
              </li>
              <li>
                <McItem material="COMPASS" size={40} />
                <h3>Checked</h3>
                <p>
                  A fight on our server gives back the measured values, and every change is tested with bots and with
                  a real {version} client.
                </p>
              </li>
            </ol>
            <figure className={styles.ticks}>
              <figcaption>
                <h3>One second of a fight</h3>
                <p>20 ticks. A square is one tick; a hit can land on the red ones.</p>
              </figcaption>
              {[
                { name: 'Minecraft 1.8.8', every: 10 },
                { name: 'Rago Practice', every: 9 },
              ].map((row) => (
                <div key={row.name} className={styles.tickRow}>
                  <span className={styles.tickName}>{row.name}</span>
                  <ol className={styles.tickCells} aria-label={`${row.name}: a hit every ${row.every} ticks`}>
                    {Array.from({ length: 20 }, (_, tick) => (
                      <li key={tick} data-hit={tick % row.every === 0 || undefined} />
                    ))}
                  </ol>
                  <span className={styles.tickCount}>every {row.every} ticks</span>
                </div>
              ))}
            </figure>
          </div>
          <div className={styles.europe} aria-labelledby="europe">
            <div>
              <h3 id="europe">A server in Europe</h3>
              <p>
                Rago Practice runs on a machine in Europe, so that European players fight close to the server. We will
                publish ping figures once they are measured, not before.
              </p>
            </div>
            <figure className={styles.sidebar}>
              <div className="scoreboard">
                <p className="scoreboard__title">
                  <McText segments={mc('&c&lRAGO PRACTICE')} ground="#000000" />
                </p>
                <ul>
                  <li>
                    <McText segments={mc('&fMode: &7Sumo duel')} ground="#000000" />
                  </li>
                  <li>
                    <McText segments={mc('&fOpponent: &cRival')} ground="#000000" />
                  </li>
                  <li>
                    <McText segments={mc('&fYour ping: &a42 ms')} ground="#000000" />
                  </li>
                  <li>
                    <McText segments={mc('&fTheir ping: &e96 ms')} ground="#000000" />
                  </li>
                </ul>
              </div>
              <figcaption className="notice">
                In a game, the sidebar shows your ping, and in a 1v1 your opponent&apos;s: green under 80 ms, yellow
                under 150, red above. Example values.
              </figcaption>
            </figure>
          </div>
          <p className="notice">
            An approximation from our own measurements, for melee hits between players. Rago Practice is not affiliated
            with Hypixel.
          </p>
        </div>
      </section>

      {/* 5. Ranked, for later */}
      <section className="section" aria-labelledby="ranked">
        <div className={`container ${styles.ranked}`}>
          <SectionHeading
            id="ranked"
            eyebrow={`Ranked Bedwars · ${game.season.name}`}
            title={`When you are ready: climb from ${first.name} to ${last.name}`}
            lead={`Ranked Bedwars in ${list(rankedFormats)}, free like every mode. Everyone starts at 0 ELO; the matchmaking looks for a gap of ${queue.startGap} ELO, wider the longer you wait, and no rank buys a place in the queue.`}
          />
          <div className={styles.ladder}>
            <DivisionLadder divisions={divisions} ground="#0c0d10" />
          </div>
        </div>
      </section>

      {/* 6. Cosmetics: shown, not told */}
      <section className={`section ${styles.band}`} aria-labelledby="cosmetics">
        <div className={`container ${styles.cosmetics}`}>
          <SectionHeading
            id="cosmetics"
            eyebrow={`${game.cosmetics.length} cosmetics`}
            title="Look the part"
            lead="Trails behind you, effects on your kills, a dance for your wins. Most are bought with the Rago Points you earn by playing; the rarest come with PREMIUM+."
          />
          <div className={styles.menus}>
            {(['trail', 'kill', 'win'] as const).map((type) => {
              const items = game.cosmetics.filter((c) => c.type === type);
              return (
                <ItemMenu
                  key={type}
                  title={{ trail: 'Trails', kill: 'Kill effects', win: 'Victory dances' }[type]}
                  items={items.map(cosmeticItem)}
                  columns={Math.min(items.length, 4)}
                  initial={items[0]?.id}
                  label={`The ${COSMETIC_KIND[type]?.toLowerCase()}s: pick one`}
                />
              );
            })}
          </div>
          <p className="notice">
            Rago Points are earned in the game ({game.ragoPoints.win} a win, {game.ragoPoints.kill} a kill,{' '}
            {game.ragoPoints.daily} a day) and have no value outside it. Videos of each effect are on their way.
          </p>
        </div>
      </section>

      {/* 7. Support the server */}
      <section className="section" aria-labelledby="support">
        <div className="container">
          <SectionHeading
            id="support"
            eyebrow="Support Rago Practice"
            title="Help us keep the server running and improving"
          />
          <blockquote className={styles.quote}>
            <p>
              Rago Practice is built around fair and competitive gameplay. Purchasing a rank is an optional way to
              support the server, help cover hosting and development costs, and contribute to its future.
            </p>
          </blockquote>
          <ul className={styles.fair} aria-label="What a rank never gives">
            <li>No combat advantage</li>
            <li>No knockback or ping advantage</li>
            <li>No place in Ranked</li>
            <li>Paid once, not a subscription</li>
          </ul>
          <div className={styles.store}>
            {game.ranks.map((rank) => (
              <RankCard key={rank.id} rank={rank} featured={rank.id === 'premium'} />
            ))}
            <article className={`card ${styles.pack}`} aria-labelledby="starter-pack">
              <McItem material="EMERALD" size={48} />
              <p className="eyebrow" style={{ color: 'var(--points)' }}>
                Once per player
              </p>
              <h3 id="starter-pack" className="rank__name">
                {pack.name}
              </h3>
              <p className="rank__price">
                <span className="rank__amount">{formatEuros(pack.price)}</span>
              </p>
              <ul className="rank__perks">
                <li>{formatNumber(pack.ragoPoints)} Rago Points</li>
                <li>{pack.xpBoosters} XP boosters: x3 Network XP for 30 minutes each</li>
                <li>{pack.pointsBoosters} points boosters: x3 Rago Points for 30 minutes each</li>
                <li>
                  {pack.cosmetics.length} cosmetics:{' '}
                  {list(pack.cosmetics.map((c) => `the ${c.name} ${c.type === 'win' ? 'victory' : c.type}`))}
                </li>
              </ul>
            </article>
          </div>
          <p className={styles.storeNote}>
            {soon ? 'The web store opens with the server.' : 'The web store opens soon.'} Prices are in euros, as the
            game&apos;s shop shows them.
          </p>
        </div>
      </section>

      {/* 8. Community */}
      <section className={`section ${styles.band}`} aria-labelledby="community">
        <div className="container">
          <SectionHeading id="community" eyebrow="Community" title="Play with people, not only against them" />
          <div className={styles.community}>
            <article className={`card ${styles.discord}`} aria-labelledby="community-discord">
              <McItem material="PLAYER_HEAD" size={48} />
              <h3 id="community-discord">The Discord</h3>
              <p>The news of the server first, and people to play with.</p>
              {discord(false)}
            </article>
            <article className="card" aria-labelledby="community-friends">
              <McItem material="NAME_TAG" size={48} />
              <h3 id="community-friends">Parties and friends</h3>
              <p>
                Add your friends with <code className="mc">/friend add</code>, and play together in a{' '}
                <code className="mc">/party</code>: only the leader queues, for the whole party.
              </p>
            </article>
            <article className="card" aria-labelledby="community-rules">
              <McItem material="BOOK" size={48} />
              <h3 id="community-rules">The rules</h3>
              <ol className={styles.rules}>
                {game.rules.map((rule) => (
                  <li key={rule}>{rule}</li>
                ))}
              </ol>
            </article>
          </div>
        </div>
      </section>

      {/* 9. Join */}
      <section className={styles.finale} aria-labelledby="join">
        <div className="container">
          <h2 id="join" className={`display ${styles.finaleTitle}`}>
            Ready to play?
          </h2>
          <ol className={styles.steps}>
            <li>
              <span className={styles.stepNumber}>1</span>
              <h3>Open Minecraft {version}</h3>
              <p>In the launcher, pick Minecraft: Java Edition, release {version}. No mod is needed.</p>
            </li>
            <li>
              <span className={styles.stepNumber}>2</span>
              <h3>Add the server</h3>
              <p>Multiplayer, then Add Server, and paste the address.</p>
            </li>
            <li>
              <span className={styles.stepNumber}>3</span>
              <h3>Right-click the compass</h3>
              <p>
                It opens every way to play.
                {game.modes.find((mode) => mode.startHere)
                  ? ` New here? ${game.modes.find((mode) => mode.startHere)?.name} is marked ✦ Start here.`
                  : ''}
              </p>
            </li>
          </ol>
          <div className={styles.finaleActions}>
            {address ? <IpChip address={address} /> : <p className={styles.note}>The address comes with the opening.</p>}
            {soon ? discord(true) : discord(false)}
          </div>
          <p className={styles.finaleLead}>
            {soon ? 'The server opens soon. The Discord hears it first.' : `Minecraft ${version}, Multiplayer, and the address.`}
          </p>
        </div>
      </section>
    </>
  );
}
