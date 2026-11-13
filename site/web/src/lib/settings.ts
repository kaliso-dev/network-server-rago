/**
 * The settings of the site, read when it is built. Each one has a safe default, so that the site
 * never shows an address or a state that is not real:
 *
 * - RAGO_SITE_STATE: "soon" (the default) before the server opens: Discord first, the store
 *   closed, no player count; "online" once it is open.
 * - RAGO_SERVER_ADDRESS: the address players type in Minecraft. None until the domain is bought:
 *   the page then says the address comes with the opening.
 * - RAGO_DISCORD_URL: the invite of the Discord server.
 * - RAGO_SITE_URL: the public address of the site, for search engines and shared links. On
 *   Netlify, the address of the deploy (URL) when it is not set.
 * - RAGO_SITE_INDEX: "yes" lets search engines index the site. Off by default, so that a
 *   temporary copy (a preview for friends, a test deploy) never shows up in a search.
 */

export type SiteState = 'soon' | 'online';

export interface Settings {
  state: SiteState;
  serverAddress: string | null;
  discordUrl: string;
  siteUrl: URL;
  /** Whether search engines may index the site. */
  index: boolean;
  /** The version of Minecraft to play with (docs/compatibility.md: 1.8.9, protocol 47). */
  minecraftVersion: string;
}

/** The invite given by the owner on 13 November 2026. */
export const DEFAULT_DISCORD_URL = 'https://discord.gg/FXcvN7mWX';

const ADDRESS = /^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}(?::\d{1,5})?$/i;
const DISCORD = /^https:\/\/(discord\.gg\/[A-Za-z0-9-]+|discord\.com\/invite\/[A-Za-z0-9-]+)$/;

export function readSettings(env: Record<string, string | undefined>): Settings {
  const rawState = env.RAGO_SITE_STATE?.trim() || 'soon';
  if (rawState !== 'soon' && rawState !== 'online') {
    throw new Error(`RAGO_SITE_STATE must be "soon" or "online", not "${rawState}"`);
  }
  const serverAddress = env.RAGO_SERVER_ADDRESS?.trim() || null;
  if (serverAddress && !ADDRESS.test(serverAddress)) {
    throw new Error(`RAGO_SERVER_ADDRESS is not a domain name: "${serverAddress}"`);
  }
  if (rawState === 'online' && !serverAddress) {
    throw new Error('RAGO_SITE_STATE=online needs RAGO_SERVER_ADDRESS: players need the address to join');
  }
  const discordUrl = env.RAGO_DISCORD_URL?.trim() || DEFAULT_DISCORD_URL;
  if (!DISCORD.test(discordUrl)) {
    throw new Error(`RAGO_DISCORD_URL is not a Discord invite: "${discordUrl}"`);
  }
  const siteUrl = new URL(env.RAGO_SITE_URL?.trim() || env.URL?.trim() || 'http://localhost:3000');
  const rawIndex = env.RAGO_SITE_INDEX?.trim() || 'no';
  if (rawIndex !== 'yes' && rawIndex !== 'no') {
    throw new Error(`RAGO_SITE_INDEX must be "yes" or "no", not "${rawIndex}"`);
  }
  return {
    state: rawState,
    serverAddress,
    discordUrl,
    siteUrl,
    index: rawIndex === 'yes',
    minecraftVersion: '1.8.9',
  };
}

export const settings: Settings = readSettings(process.env);
