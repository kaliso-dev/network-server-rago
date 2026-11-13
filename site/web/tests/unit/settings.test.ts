import { describe, expect, it } from 'vitest';
import { DEFAULT_DISCORD_URL, readSettings } from '@/lib/settings';

describe('the settings of the site', () => {
  it('opens in the "soon" state with no address by default', () => {
    const settings = readSettings({});
    expect(settings.state).toBe('soon');
    expect(settings.serverAddress).toBeNull();
    expect(settings.discordUrl).toBe(DEFAULT_DISCORD_URL);
    expect(settings.siteUrl.href).toBe('http://localhost:3000/');
    expect(settings.index).toBe(false);
  });

  it('takes the address of the Netlify deploy when no site address is set', () => {
    expect(readSettings({ URL: 'https://rago.netlify.app' }).siteUrl.href).toBe('https://rago.netlify.app/');
    expect(readSettings({ URL: 'https://rago.netlify.app', RAGO_SITE_URL: 'https://ragopractice.com' }).siteUrl.href).toBe(
      'https://ragopractice.com/',
    );
  });

  it('lets search engines in only when asked', () => {
    expect(readSettings({ RAGO_SITE_INDEX: 'yes' }).index).toBe(true);
    expect(() => readSettings({ RAGO_SITE_INDEX: 'true' })).toThrow(/RAGO_SITE_INDEX/);
  });

  it('refuses the "online" state without an address', () => {
    expect(() => readSettings({ RAGO_SITE_STATE: 'online' })).toThrow(/RAGO_SERVER_ADDRESS/);
  });

  it('accepts the "online" state with an address', () => {
    const settings = readSettings({ RAGO_SITE_STATE: 'online', RAGO_SERVER_ADDRESS: 'play.example.com' });
    expect(settings.state).toBe('online');
    expect(settings.serverAddress).toBe('play.example.com');
  });

  it('refuses an unknown state', () => {
    expect(() => readSettings({ RAGO_SITE_STATE: 'open' })).toThrow(/RAGO_SITE_STATE/);
  });

  it('refuses an address that is not a domain name', () => {
    expect(() => readSettings({ RAGO_SERVER_ADDRESS: 'http://play.example.com' })).toThrow();
    expect(() => readSettings({ RAGO_SERVER_ADDRESS: 'play example' })).toThrow();
  });

  it('refuses a Discord link that is not an invite', () => {
    expect(() => readSettings({ RAGO_DISCORD_URL: 'https://example.com/discord' })).toThrow(/RAGO_DISCORD_URL/);
    expect(() => readSettings({ RAGO_DISCORD_URL: 'http://discord.gg/abc' })).toThrow(/RAGO_DISCORD_URL/);
  });
});
