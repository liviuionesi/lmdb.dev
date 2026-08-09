// Tests useServiceStatus: unknown with no URL, checking->up, checking->down,
// and that a stale response after the url changes is ignored (the cancelled
// guard). Driven through a tiny probe component since this @testing-library/
// react version (v11, pinned for React 17) predates renderHook.
import React from 'react';
import { render, screen } from '@testing-library/react';

import useServiceStatus from './useServiceStatus';

const Probe = ({ url }) => <div>{useServiceStatus(url)}</div>;

describe('useServiceStatus', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('reports "unknown" when no url is given', () => {
    render(<Probe url={undefined} />);
    expect(screen.getByText('unknown')).toBeInTheDocument();
  });

  it('resolves to "up" when the probe succeeds', async () => {
    global.fetch = vi.fn().mockResolvedValue({});
    render(<Probe url="http://example.com" />);

    expect(screen.getByText('checking')).toBeInTheDocument();
    expect(await screen.findByText('up')).toBeInTheDocument();
    expect(global.fetch).toHaveBeenCalledWith('http://example.com', { mode: 'no-cors', cache: 'no-store' });
  });

  it('resolves to "down" when the probe rejects', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('network error'));
    render(<Probe url="http://example.com" />);

    expect(await screen.findByText('down')).toBeInTheDocument();
  });

  it('ignores a resolved probe after the url changes to falsy (cancelled guard)', async () => {
    let resolveFetch;
    global.fetch = vi.fn().mockReturnValue(new Promise((resolve) => { resolveFetch = resolve; }));

    const { rerender } = render(<Probe url="http://example.com" />);
    expect(screen.getByText('checking')).toBeInTheDocument();

    rerender(<Probe url={undefined} />);
    expect(screen.getByText('unknown')).toBeInTheDocument();

    resolveFetch();
    await Promise.resolve();

    expect(screen.getByText('unknown')).toBeInTheDocument();
  });
});
