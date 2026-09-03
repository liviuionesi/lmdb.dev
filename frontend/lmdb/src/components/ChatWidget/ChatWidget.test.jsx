// Tests the ChatWidget UI shell (#223): the launcher toggling the panel open/closed, sending a
// message via the button and via Enter, the empty-input guard, focus landing in the input on
// open, message accumulation across sends, and that user vs. assistant messages render with a
// distinguishable role (#223 AC2) — both the sighted (class-driven alignment/color) and assistive-
// tech (aria-label) channels.
// No backend call is exercised here — #223 is UI-shell only, wiring to the real endpoint is #224.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import ChatWidget, { ChatMessage } from './ChatWidget';
import { renderWithProviders } from '../../test-utils/render';

describe('ChatWidget launcher', () => {
  it('is closed by default, showing only the launcher Fab', () => {
    renderWithProviders(<ChatWidget />);

    expect(screen.getByRole('button', { name: 'Open chat assistant' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('opens the panel on click and focuses the message input', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ChatWidget />);

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));

    expect(screen.getByRole('dialog', { name: 'Chat assistant' })).toBeInTheDocument();
    // #223 AC4: a keyboard/screen-reader user can start typing immediately, without tabbing past
    // the header controls first.
    expect(screen.getByLabelText('Chat message')).toHaveFocus();
  });

  it('closes the panel from the launcher Fab (now labeled Close)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ChatWidget />);

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat assistant' }));

    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('also closes from the header close button, independently of the launcher', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ChatWidget />);

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat panel' }));

    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('returns focus to the launcher when closed via the header button (#223 AC4)', async () => {
    // The header close button unmounts along with the rest of the panel — without an explicit
    // hand-off, focus would fall back to document.body instead of a still-visible, focusable
    // element, stranding a keyboard/screen-reader user.
    const user = userEvent.setup();
    renderWithProviders(<ChatWidget />);

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat panel' }));

    expect(screen.getByRole('button', { name: 'Open chat assistant' })).toHaveFocus();
  });
});

describe('ChatWidget sending a message', () => {
  const openPanel = async (user) => {
    renderWithProviders(<ChatWidget />);
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('appends a typed message to the list and clears the draft, via the send button', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'What should I watch tonight?');
    await user.click(screen.getByRole('button', { name: 'Send message' }));

    expect(screen.getByText('What should I watch tonight?')).toBeInTheDocument();
    expect(screen.getByLabelText('Chat message')).toHaveValue('');
  });

  it('also sends on Enter, matching Search.jsx\'s own submit-on-Enter pattern (#223 AC4)', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Recommend a comedy{Enter}');

    expect(screen.getByText('Recommend a comedy')).toBeInTheDocument();
  });

  it('leaves Shift+Enter alone as a newline, not a send', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'first line{Shift>}{Enter}{/Shift}second line');

    expect(screen.queryByText('first line')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Chat message')).toHaveValue('first line\nsecond line');
  });

  it('disables the send button and ignores Enter on a blank or whitespace-only draft', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled();

    await user.type(screen.getByLabelText('Chat message'), '   {Enter}');

    // Asserts no message was added at all, not just that the (whitespace-normalized-away) text
    // isn't present — the list's only child should still be the scroll-sentinel div ChatWidget.jsx
    // always renders, proving handleSend's `if (!trimmed) return` guard actually ran rather than
    // pushing an empty-text bubble that a plain textContent check couldn't tell apart from "nothing
    // sent".
    expect(screen.getByTestId('chat-message-list').children).toHaveLength(1);
  });

  it('accumulates messages across multiple sends, in order, rather than replacing the list', async () => {
    // Guards two independent failure modes a single-message test can't: setMessages replacing
    // (rather than appending to) the previous list, and the nextIdRef id counter never
    // incrementing (every message keyed 0, so React would render/reconcile them as one).
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'First question{Enter}');
    await user.type(input, 'Second question{Enter}');

    const list = screen.getByTestId('chat-message-list');
    // Both messages plus the trailing scroll-sentinel div.
    expect(list.children).toHaveLength(3);
    const messageTexts = [...list.children].map((child) => child.textContent);
    expect(messageTexts).toEqual(['First question', 'Second question', '']);
  });
});

describe('ChatMessage', () => {
  it('renders a user message with the user (not assistant) style, labeled for assistive tech (#223 AC2)', () => {
    // jsx-a11y/aria-role reads any `role` prop as a DOM ARIA role, even on a custom (capitalized)
    // component — `role` here is ChatMessage's own sender prop ('user'/'assistant'), not one.
    // eslint-disable-next-line jsx-a11y/aria-role
    const { container } = renderWithProviders(<ChatMessage role="user" text="Hi there" />);

    const bubble = screen.getByLabelText('You');
    expect(bubble).toHaveTextContent('Hi there');
    // The sighted channel of AC2's "distinguishes visually": swapping styles.js's messageUser/
    // messageAssistant classes (or the isUser ternary picking between them) would leave the
    // aria-label assertion above green but must fail this one.
    expect(container.firstChild.className).toMatch(/messageUser/);
    expect(container.firstChild.className).not.toMatch(/messageAssistant/);
  });

  it('renders an assistant message with its own distinct role label and style', () => {
    // eslint-disable-next-line jsx-a11y/aria-role -- see the disable above; same false positive.
    const { container } = renderWithProviders(<ChatMessage role="assistant" text="How can I help?" />);

    const bubble = screen.getByLabelText('Assistant');
    expect(bubble).toHaveTextContent('How can I help?');
    expect(container.firstChild.className).toMatch(/messageAssistant/);
    expect(container.firstChild.className).not.toMatch(/messageUser/);
  });
});
