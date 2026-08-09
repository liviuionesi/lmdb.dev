// Tests StatusCard: status-dot label, disabled Open link with no url, and
// the optional secondary link.
import React from 'react';
import { screen } from '@testing-library/react';

import StatusCard from './StatusCard';
import { renderWithProviders } from '../../test-utils/render';
import useServiceStatus from './useServiceStatus';

vi.mock('./useServiceStatus');

describe('StatusCard', () => {
  it('shows the title, description, and an enabled Open link when a url is given', () => {
    useServiceStatus.mockReturnValue('up');
    renderWithProviders(
      <StatusCard title="Discovery (Eureka)" description="Service registry." url="http://localhost:8761" />,
    );

    expect(screen.getByText('Discovery (Eureka)')).toBeInTheDocument();
    expect(screen.getByText('Service registry.')).toBeInTheDocument();
    // MUI's Tooltip exposes its `title` text via aria-label on the status dot.
    expect(screen.getByLabelText('Reachable')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /open/i })).toHaveAttribute('href', 'http://localhost:8761');
  });

  it('disables the Open link when there is no url ("not configured")', () => {
    useServiceStatus.mockReturnValue('unknown');
    renderWithProviders(<StatusCard title="Metrics" description="No env var set." url={undefined} />);

    expect(screen.getByLabelText('Not configured')).toBeInTheDocument();
    // Anchors have no native `disabled` attribute; MUI's disabled Button
    // renders it with role="button" (not "link") and aria-disabled instead.
    expect(screen.getByRole('button', { name: /open/i })).toHaveAttribute('aria-disabled', 'true');
  });

  it('renders the secondary link when provided', () => {
    useServiceStatus.mockReturnValue('down');
    renderWithProviders(
      <StatusCard
        title="API Gateway"
        description="Gateway."
        url="http://localhost:8080/actuator/health"
        secondaryUrl="http://localhost:8080/actuator/gateway/routes"
        secondaryLabel="Routes"
      />,
    );

    expect(screen.getByLabelText('Unreachable')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Routes' })).toHaveAttribute('href', 'http://localhost:8080/actuator/gateway/routes');
  });
});
