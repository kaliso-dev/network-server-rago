import Image from 'next/image';
import blockClutch from '../../../design/assets/block-clutch.jpg';
import { CategoryCard } from '@/components/CategoryCard';
import { DivisionLadder } from '@/components/DivisionLadder';
import { IpChip } from '@/components/IpChip';
import { McText, mc } from '@/components/McText';
import { PixelIcon } from '@/components/PixelIcon';
import { RankCard } from '@/components/RankCard';
import { ButtonLink, SectionHeading, ServerStatus, Tag } from '@/components/ui';
import { eventModes, formatEuros, formatNumber, game, playableModes, type Mode } from '@/lib/game';
import { settings } from '@/lib/settings';
import styles from './page.module.css';

/** The names of a category's modes, each family once: "Bed fight", not "Bed fight 1v1" and "2v2". */
function families(category: string): Mode[] {
  const seen = new Set<string>();
  const out: Mode[] = [];
  for (const mode of playableModes().filter((m) => m.category === category)) {
    const group = mode.group ? game.groups.find((g) => g.id === mode.group) : undefined;
    const name = group ? group.name : mode.name.replace(/ \d+v\d+$/, '');
    if (!seen.has(name)) {
      seen.add(name);
      out.push({ ...mode, name });
    }
  }
  return out;
}

const list = (names: string[]): string =>
  names.length <= 1 ? (names[0] ?? '') : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;

export default function Home() {
  const soon = settings.state === 'soon';
  const address = settings.serverAddress;
  const version = settings.minecraftVersion;
  const divisions = game.ranked.divisions;
  const first = divisions[0]!;
  const last = divisions[divisions.length - 1]!;
  const trainings = playableModes().filter((mode) => mode.category === 'entrainement');
  const rankedFormats = game.modes.filter((mode) => mode.ranked).map((mode) => mode.format);
  const startHere = game.modes.find((mode) => mode.startHere);
  const events = eventModes().map((mode) => mode.name.replace(/ event$/, ''));
  const queue = game.ranked.queue;
  const rewarded = divisions.filter((division) => division.seasonReward > 0);
  const pack = game.starterPack;
  const modeCount = playableModes().length;
  const firstRewarded = rewarded[0];

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

  const discordButton = (primary: boolean) => (
    <ButtonLink href={settings.discordUrl} variant={primary ? 'primary' : 'secondary'} cut={primary}>
      Join the Discord
    </ButtonLink>
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, '\\u003c') }}
      />
      {/* 1. Hero */}
      <section className={styles.hero} aria-labelledby="hero-title">
        <div className={`container ${styles.heroGrid}`}>
          <div className={styles.heroText}>
            <p className="eyebrow">
              {game.season.name} · Minecraft {version}
            </p>
            <h1 id="hero-title" className="display">
              Master Ranked Bedwars.
            </h1>
            <p className="lead">
              Train the skills of Ranked Bedwars alone, duel anyone, then climb {divisions.length} ranked divisions,
              from {first.name} to {last.name}. Every mode is free, ranked included.
            </p>
            <div className={styles.actions}>
              {soon ? (
                discordButton(true)
              ) : (
                <ButtonLink href="#join" variant="primary" cut>
                  Play now
                </ButtonLink>
              )}
              <ButtonLink href="#modes">See the modes</ButtonLink>
            </div>
            <div className={styles.meta}>
              <ServerStatus state={settings.state} />
              <Tag>Java {version}</Tag>
              {address ? <IpChip address={address} /> : null}
            </div>
            {!address ? <p className={styles.note}>The server address comes with the opening.</p> : null}
          </div>
          <div className={styles.heroArt} aria-hidden="true">
            <Image src="/brand/rago-logo.svg" alt="" width={340} height={391} priority className={styles.heroLogo} />
          </div>
        </div>
      </section>

      {/* 2. Join in three steps */}
      <section className="section" aria-labelledby="join">
        <div className="container">
          <SectionHeading id="join" eyebrow="Join in 3 steps" title="From the launcher to your first game" />
          <ol className={styles.steps}>
            <li className={`card ${styles.step}`}>
              <span className={styles.stepNumber}>1</span>
              <h3>Open Minecraft {version}</h3>
              <p>In the launcher, pick Minecraft: Java Edition, release {version}. No mod is needed.</p>
            </li>
            <li className={`card ${styles.step}`}>
              <span className={styles.stepNumber}>2</span>
              <h3>Add the server</h3>
              <p>Multiplayer, then Add Server, and paste the address.</p>
              {address ? <IpChip address={address} /> : <p className={styles.note}>The address comes with the opening.</p>}
            </li>
            <li className={`card ${styles.step}`}>
              <span className={styles.stepNumber}>3</span>
              <h3>Pick a game</h3>
              <p>
                The compass of your hotbar opens the ways to play.
                {startHere ? <> New here? {startHere.name} is marked ✦ Start here.</> : null}
              </p>
            </li>
          </ol>
        </div>
      </section>

      {/* 3. Train, clutch, climb */}
      <section className="section" aria-labelledby="train">
        <div className="container">
          <SectionHeading
            id="train"
            eyebrow={game.server.slogan}
            title="Every skill of Ranked Bedwars, one at a time"
            lead="Practise alone until it is automatic, then use it where it counts."
          />
          <div className={styles.pillars}>
            <article className={`card ${styles.pillar}`} aria-labelledby="pillar-train">
              <h3 id="pillar-train">Train</h3>
              <p>
                {trainings.length} trainings, alone and at your own pace, with your records and the boards of the best.
              </p>
              <ul className={styles.chips} aria-label="The trainings">
                {trainings.map((mode) => (
                  <li key={mode.id}>{mode.name}</li>
                ))}
              </ul>
            </article>
            <article className={`card ${styles.pillar}`} aria-labelledby="pillar-clutch">
              <div className={styles.capture}>
                <Image
                  src={blockClutch}
                  alt="The Block clutch training on the server: wool placed after a hit, and the board of the biggest clutches of the week"
                  sizes="(max-width: 900px) 100vw, 380px"
                  placeholder="blur"
                />
              </div>
              <h3 id="pillar-clutch">Clutch</h3>
              <p>
                A hit throws you off: place your wool to save yourself. Every clutch is measured, and the biggest of
                the week go on the board.
              </p>
              <figure className={styles.chat}>
                <div className="mc-chat">
                  <p>
                    <McText segments={mc('&aClutch! &7Streak: &f3&8 | &e4.2 blocks')} ground="#000000" />
                  </p>
                  <p>
                    <McText segments={mc('&6&lBiggest clutch of the week! &f6.3 blocks')} ground="#000000" />
                  </p>
                </div>
                <figcaption className="notice">The training&apos;s messages in the chat, with example values.</figcaption>
              </figure>
            </article>
            <article className={`card ${styles.pillar}`} aria-labelledby="pillar-climb">
              <h3 id="pillar-climb">Climb</h3>
              <p>
                {divisions.length} divisions, from {first.name} to {last.name}. A win gives {first.win} ELO in{' '}
                {first.name}, {last.win} in {last.name}: much to win at the bottom, much to lose at the top.
              </p>
              <div className={styles.miniLadder}>
                <DivisionLadder divisions={divisions} ground="#181b20" />
              </div>
            </article>
          </div>
        </div>
      </section>

      {/* 4. The modes */}
      <section className="section" aria-labelledby="modes">
        <div className="container">
          <SectionHeading
            id="modes"
            eyebrow={`${modeCount} modes, ${game.categories.length} ways to play`}
            title="Pick how you want to play"
            lead="Each way to play has its character in the lobby, and the compass opens them all."
          />
          <div className={styles.categories}>
            {game.categories.map((category) => (
              <CategoryCard key={category.id} category={category} modes={families(category.id)} />
            ))}
          </div>
          {events.length > 0 ? (
            <p className={styles.events}>
              <strong>Events:</strong> {list(events)}
              {game.events.maxPlayers ? `, up to ${game.events.maxPlayers} players` : ''}.{' '}
              {game.events.automatic && game.events.lobbyPlayers && game.events.everyMinutes
                ? `When ${game.events.lobbyPlayers} players are free in the lobby and no event ran for ${game.events.everyMinutes} minutes, the server starts one. `
                : ''}
              PREMIUM+ players can host one with <code className="mc">/host</code>.
            </p>
          ) : null}
        </div>
      </section>

      {/* 5. Ranked */}
      <section className={`section ${styles.band}`} aria-labelledby="ranked">
        <div className="container">
          <SectionHeading
            id="ranked"
            eyebrow={`Ranked Bedwars · ${game.season.name}`}
            title={`Climb from ${first.name} to ${last.name}`}
            lead={`Ranked Bedwars in ${list(rankedFormats)}. Everyone starts at 0 ELO, and every game moves it: what a win gives and a loss takes depends on your division.`}
          />
          <DivisionLadder divisions={divisions} showGains />
          <p className={styles.legend}>
            <span className="division__win">+ ELO for a win</span> · <span className="division__loss">− ELO for a loss</span>, by
            division.
          </p>
          <dl className={styles.facts}>
            <div>
              <dt>Matchmaking</dt>
              <dd>
                By ELO: the gap accepted starts at {queue.startGap} and grows by {queue.gapStep} every{' '}
                {queue.gapEverySeconds} seconds, up to {queue.maxGap}.
              </dd>
            </div>
            <div>
              <dt>First in line</dt>
              <dd>Whoever waited longest. No rank buys a place in a ranked queue.</dd>
            </div>
            <div>
              <dt>MVP</dt>
              <dd>The player with the most kills gets {game.ranked.mvpBonus} more ELO, won or lost.</dd>
            </div>
            <div>
              <dt>Leaving</dt>
              <dd>
                Leave a ranked game you still play, and you wait {queue.leavePenaltyMinutes} minutes before the next
                ranked queue.
              </dd>
            </div>
            {firstRewarded ? (
              <div>
                <dt>Season rewards</dt>
                <dd>
                  From {formatNumber(firstRewarded.seasonReward)} Rago Points in {firstRewarded.name} to{' '}
                  {formatNumber(last.seasonReward)} in {last.name}, at the end of the season.
                </dd>
              </div>
            ) : null}
          </dl>
        </div>
      </section>

      {/* 6. Measured knockback */}
      <section className="section" aria-labelledby="knockback">
        <div className="container">
          <SectionHeading
            id="knockback"
            eyebrow="Measured knockback"
            title="Knockback measured, not guessed"
            lead="The feel of a fight comes from numbers we recorded ourselves."
          />
          <ol className={styles.method}>
            <li>
              <h3>Recorded</h3>
              <p>
                Two players fought on Hypixel with our recorder mod, each logging every hit they took: in Housing, and
                in a Bed Wars 1v1.
              </p>
            </li>
            <li>
              <h3>Rebuilt</h3>
              <p>
                Our combat engine starts from the exact knockback of Minecraft 1.8.8 and adds what the recordings show:
                a little more vertical push, and 9 ticks between two hits instead of 10.
              </p>
            </li>
            <li>
              <h3>Checked</h3>
              <p>
                A fight on our server gives back the measured values, and every change is tested with bots and with a
                real {version} client.
              </p>
            </li>
          </ol>
          <p className="notice">
            An approximation from our own measurements, for melee hits between players. Rago Practice is not affiliated
            with Hypixel.
          </p>
        </div>
      </section>

      {/* 7. The tools */}
      <section className="section" aria-labelledby="tools">
        <div className="container">
          <SectionHeading id="tools" eyebrow="Built for practice" title="See what happened, then fix it" />
          <ul className={styles.tools}>
            {game.replays.active ? (
              <li className="card">
                <h3>Replays</h3>
                <p>
                  <code className="mc">/replay</code> shows your last games again, and any game by its code: send it to
                  a friend. Kept {game.replays.days} days.
                </p>
              </li>
            ) : null}
            <li className="card">
              <h3>Spectate</h3>
              <p>
                <code className="mc">/spectate</code> a friend&apos;s game, unseen, flying through it.
              </p>
            </li>
            <li className="card">
              <h3>Duel recap</h3>
              <p>At the end of a duel, [View] in the recap shows what each player had left.</p>
            </li>
            <li className="card">
              <h3>Kit editor</h3>
              <p>Arrange each kit once, from the book of your lobby hotbar: your layout is kept.</p>
            </li>
            <li className="card">
              <h3>Records</h3>
              <p>
                <code className="mc">/records</code>: your best and your place on each training board, and the top 10.
              </p>
            </li>
            <li className="card">
              <h3>Daily challenge</h3>
              <p>
                <code className="mc">/challenge</code>: one training for everyone each day, and the top 3 win XP.
              </p>
            </li>
          </ul>
        </div>
      </section>

      {/* 8. The store, in brief */}
      <section className={`section ${styles.band}`} aria-labelledby="store">
        <div className="container">
          <SectionHeading
            id="store"
            eyebrow="The store, in brief"
            title="Look the part. Never buy the win."
            lead="Ranks change how you look and how the lobby feels. No rank gives combat stats, and none buys a place in Ranked."
          />
          <div className={styles.store}>
            {game.ranks.map((rank) => (
              <RankCard key={rank.id} rank={rank} featured={rank.id === 'premium'} />
            ))}
            <article className={`card ${styles.pack}`} aria-labelledby="starter-pack">
              <PixelIcon icon="chest" className="product__icon" size={56} />
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

      {/* 9. Community */}
      <section className="section" aria-labelledby="community">
        <div className="container">
          <SectionHeading id="community" eyebrow="Community" title="Play with people, not only against them" />
          <div className={styles.community}>
            <article className={`card ${styles.discord}`} aria-labelledby="community-discord">
              <h3 id="community-discord">The Discord</h3>
              <p>The news of the server, and people to play with.</p>
              {discordButton(false)}
            </article>
            <article className="card" aria-labelledby="community-friends">
              <h3 id="community-friends">Parties and friends</h3>
              <p>
                Add your friends with <code className="mc">/friend add</code>, and play together in a{' '}
                <code className="mc">/party</code>: only the leader queues, for the whole party.
              </p>
            </article>
            <article className="card" aria-labelledby="community-rules">
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

      {/* 10. Last call */}
      <section className={styles.finale} aria-labelledby="finale">
        <div className={`container ${styles.finaleInner}`}>
          <h2 id="finale">Ready to climb?</h2>
          <p className="lead">
            {soon ? 'The server opens soon. The Discord hears it first.' : `Minecraft ${version}, Multiplayer, and the address.`}
          </p>
          <div className={styles.actions}>
            {address && !soon ? <IpChip address={address} /> : null}
            {discordButton(soon)}
          </div>
        </div>
      </section>
    </>
  );
}
