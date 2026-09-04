// Tests the ChatWidget UI shell (#223) plus its backend wiring (#224): the launcher toggling the
// panel open/closed, sending a message via the button and via Enter, the empty-input guard, focus
// landing in the input on open, message accumulation across sends, that user vs. assistant
// messages render with a distinguishable role (#223 AC2), and — new for #224 — that sending a
// message actually posts to POST /api/v1/ai/chat, appends the assistant's reply once it resolves,
// omits conversationId on the first message of a session (#197 AC3), includes the conversation id
// returned by the most recent response on every message after it (#197 AC2), the "start a new
// conversation" header button clears that id from state/storage/the list so the next message is a
// genuine first message again (#224's own AC), and a second send fired before the first resolves
// is a no-op rather than racing it into two split conversations. #225 adds the loading/error visual
// states below: a visible "typing" indicator while a send is pending, a dismissible error Alert on
// failure, a screen-reader announcement of each new assistant message/error via a live region, and
// an automated accessibility (jest-axe) pass over the widget with a message, an error, and the
// typing indicator all present.
//
// ChatWidget now depends on useSendChatMessageMutation (services/AI.js), an RTK Query hook, so
// every render here needs a real Redux store with aiApi mounted — the same store shape
// services/AI.test.js uses — with `fetch` mocked the same way.
import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';
import { axe } from 'jest-axe';

import ChatWidget, { ChatMessage } from './ChatWidget';
import { aiApi } from '../../services/AI';
import { renderWithProviders } from '../../test-utils/render';

const STORAGE_KEY = 'lmdb_chat_conversation_id';

// createDynamicBaseQuery resolves the backend through apiUrl.js's async, health-checked waterfall
// (see apiUrl.test.js) — pinning a static override short-circuits that so the chat POST is always
// the only fetch call, matching services/AI.test.js's own setup.
const pinStaticApiUrl = () => localStorage.setItem('lmdb_api_url', 'http://localhost:8080');

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

const buildStore = () => configureStore({
  reducer: { [aiApi.reducerPath]: aiApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(aiApi.middleware),
});

const renderChatWidget = () => {
  const store = buildStore();
  // `container` is only consumed by the #225 axe test below; every other call site here just fires
  // this for its side effect (mounting into document.body) and ignores the return value.
  const { container } = renderWithProviders(<Provider store={store}><ChatWidget /></Provider>);
  return { store, container };
};

beforeEach(() => {
  pinStaticApiUrl();
  // A reply every send-focused test can rely on unless it overrides `fetch` itself — keeps the
  // #223-era UI-behavior tests (which don't care about the assistant's reply) from having to know
  // about the network call their click/Enter now triggers.
  global.fetch = vi.fn().mockResolvedValue(jsonResponse({
    conversationId: '11111111-1111-1111-1111-111111111111',
    reply: 'Assistant reply',
  }));
});

afterEach(() => {
  delete global.fetch;
  localStorage.clear();
});

describe('ChatWidget launcher', () => {
  it('is closed by default, showing only the launcher Fab', () => {
    renderChatWidget();

    expect(screen.getByRole('button', { name: 'Open chat assistant' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('opens the panel on click and focuses the message input', async () => {
    const user = userEvent.setup();
    renderChatWidget();

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));

    expect(screen.getByRole('dialog', { name: 'Chat assistant' })).toBeInTheDocument();
    // #223 AC4: a keyboard/screen-reader user can start typing immediately, without tabbing past
    // the header controls first.
    expect(screen.getByLabelText('Chat message')).toHaveFocus();
  });

  it('closes the panel from the launcher Fab (now labeled Close)', async () => {
    const user = userEvent.setup();
    renderChatWidget();

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat assistant' }));

    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('also closes from the header close button, independently of the launcher', async () => {
    const user = userEvent.setup();
    renderChatWidget();

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat panel' }));

    expect(screen.queryByRole('dialog', { name: 'Chat assistant' })).not.toBeInTheDocument();
  });

  it('returns focus to the launcher when closed via the header button (#223 AC4)', async () => {
    // The header close button unmounts along with the rest of the panel — without an explicit
    // hand-off, focus would fall back to document.body instead of a still-visible, focusable
    // element, stranding a keyboard/screen-reader user.
    const user = userEvent.setup();
    renderChatWidget();

    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    await user.click(screen.getByRole('button', { name: 'Close chat panel' }));

    expect(screen.getByRole('button', { name: 'Open chat assistant' })).toHaveFocus();
  });
});

describe('ChatWidget sending a message', () => {
  const openPanel = async (user) => {
    renderChatWidget();
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
    expect(global.fetch).not.toHaveBeenCalled();
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
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('accumulates messages across multiple sends, in order, rather than replacing the list', async () => {
    // Guards two independent failure modes a single-message test can't: setMessages replacing
    // (rather than appending to) the previous list, and the nextIdRef id counter never
    // incrementing (every message keyed 0, so React would render/reconcile them as one).
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'First question{Enter}');
    await waitFor(() => expect(screen.getByText('Assistant reply')).toBeInTheDocument());
    await user.type(input, 'Second question{Enter}');
    await waitFor(() => expect(screen.getAllByText('Assistant reply')).toHaveLength(2));

    const list = screen.getByTestId('chat-message-list');
    // Both questions, both replies, plus the trailing scroll-sentinel div.
    expect(list.children).toHaveLength(5);
    const messageTexts = [...list.children].map((child) => child.textContent);
    expect(messageTexts).toEqual(['First question', 'Assistant reply', 'Second question', 'Assistant reply', '']);
  });
});

describe('ChatWidget backend wiring (#224)', () => {
  const openPanel = async (user) => {
    renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('posts to POST /api/v1/ai/chat and appends the assistant reply once it resolves', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'What should I watch tonight?{Enter}');

    const reply = await screen.findByText('Assistant reply');
    expect(reply.closest('[aria-label="Assistant"]')).toBeInTheDocument();
    const request = global.fetch.mock.calls[0][0];
    expect(request.url).toBe('http://localhost:8080/api/v1/ai/chat');
    expect(request.method).toBe('POST');
  });

  it('omits conversationId on the first message of a brand-new session (#197 AC3)', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Hello{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

    const request = global.fetch.mock.calls[0][0];
    expect(JSON.parse(await request.text())).toEqual({ message: 'Hello' });
  });

  it('sends the conversationId the first reply returned on the very next message (#197 AC2)', async () => {
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'First message{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
    await user.type(input, 'Follow-up{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));

    const secondRequest = global.fetch.mock.calls[1][0];
    expect(JSON.parse(await secondRequest.text())).toEqual({
      conversationId: '11111111-1111-1111-1111-111111111111',
      message: 'Follow-up',
    });
  });

  it('persists the conversationId to localStorage so it survives a page reload (#197 AC2)', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Hello{Enter}');

    await waitFor(() => expect(localStorage.getItem(STORAGE_KEY)).toBe('11111111-1111-1111-1111-111111111111'));
  });

  it('reuses a conversationId already persisted from a previous session on the very first send', async () => {
    localStorage.setItem(STORAGE_KEY, '22222222-2222-2222-2222-222222222222');
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Continuing from before{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

    const request = global.fetch.mock.calls[0][0];
    expect(JSON.parse(await request.text())).toEqual({
      conversationId: '22222222-2222-2222-2222-222222222222',
      message: 'Continuing from before',
    });
  });

  it('keeps the typed message in the list when the backend call fails, instead of discarding it', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ message: 'assistant unavailable' }),
      text: async () => JSON.stringify({ message: 'assistant unavailable' }),
      clone() { return this; },
    });
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Still here?{Enter}');

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
    // #197 AC4's "without losing the user's typed message" half — the dedicated error bubble/
    // indicator for the failure itself is #225's, not this Task's.
    expect(screen.getByText('Still here?')).toBeInTheDocument();
  });

  it('always sends the id from the most recent response, not just the first one (#197 AC2)', async () => {
    // Distinct from the "sends the conversationId the first reply returned" test above: that one
    // would still pass if the widget hardcoded the first response's id forever. Two different ids
    // across three messages proves it actually tracks the latest one, not just "some" id.
    global.fetch = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ conversationId: 'aaaaaaaa-0000-0000-0000-000000000000', reply: 'first reply' }))
      .mockResolvedValueOnce(jsonResponse({ conversationId: 'bbbbbbbb-0000-0000-0000-000000000000', reply: 'second reply' }))
      .mockResolvedValueOnce(jsonResponse({ conversationId: 'bbbbbbbb-0000-0000-0000-000000000000', reply: 'third reply' }));
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'one{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
    await user.type(input, 'two{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));
    await user.type(input, 'three{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(3));

    const thirdRequest = global.fetch.mock.calls[2][0];
    expect(JSON.parse(await thirdRequest.text())).toEqual({
      conversationId: 'bbbbbbbb-0000-0000-0000-000000000000', // the second reply's id, not the first's
      message: 'three',
    });
  });

  it('ignores a second send fired before the first one has resolved (#224: no split conversations)', async () => {
    // A rapid Enter-mash (or a click landing while a previous send is still in flight) must not
    // fire a second no-conversationId request — two concurrent "first messages" would each start
    // their own backend conversation, and whichever reply lands second would silently overwrite
    // the other's id. Holds the first response pending with an unresolved promise to simulate the
    // in-flight window a real network round-trip leaves open.
    let resolveFirstResponse;
    const firstResponsePromise = new Promise((resolve) => { resolveFirstResponse = resolve; });
    global.fetch = vi.fn().mockReturnValueOnce(firstResponsePromise);
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'first{Enter}');
    expect(screen.getByText('first')).toBeInTheDocument();
    // The second attempt while the first is still pending must be a full no-op: no second bubble,
    // no second fetch call, and — since handleSend's guard returns before the draft-clearing step
    // — the typed text stays in the input rather than being silently swallowed.
    await user.type(input, 'second{Enter}');

    const messageList = screen.getByTestId('chat-message-list');
    expect(messageList).not.toHaveTextContent('second');
    expect(input).toHaveValue('second');
    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled();

    resolveFirstResponse(jsonResponse({
      conversationId: '11111111-1111-1111-1111-111111111111',
      reply: 'Assistant reply',
    }));
    await screen.findByText('Assistant reply');
    // Once the in-flight request resolves, sending is available again.
    expect(screen.getByLabelText('Chat message')).not.toBeDisabled();
  });
});

describe('ChatWidget "start a new conversation" (#224)', () => {
  const openPanel = async (user) => {
    renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('is disabled until at least one message has been sent', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    expect(screen.getByRole('button', { name: 'Start a new conversation' })).toBeDisabled();
  });

  it('clears the message list, the conversation id, and localStorage, so the next message omits it again', async () => {
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    // 1. Have an ongoing, persisted conversation.
    await user.type(input, 'First conversation{Enter}');
    await waitFor(() => expect(localStorage.getItem(STORAGE_KEY)).toBe('11111111-1111-1111-1111-111111111111'));

    // 2. Start a new one.
    await user.click(screen.getByRole('button', { name: 'Start a new conversation' }));

    expect(screen.queryByText('First conversation')).not.toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(screen.getByRole('button', { name: 'Start a new conversation' })).toBeDisabled();

    // 3. The next message must behave exactly like a brand-new session's first message (#197
    // AC3) — proves the reset isn't just cosmetic (clearing the list) but actually drops the id
    // from both component state and storage.
    await user.type(input, 'New conversation{Enter}');
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));
    const secondRequest = global.fetch.mock.calls[1][0];
    expect(JSON.parse(await secondRequest.text())).toEqual({ message: 'New conversation' });
  });

  it('moves focus back to the message input', async () => {
    const user = userEvent.setup();
    await openPanel(user);
    await user.type(screen.getByLabelText('Chat message'), 'Hello{Enter}');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Start a new conversation' })).not.toBeDisabled());

    await user.click(screen.getByRole('button', { name: 'Start a new conversation' }));

    expect(screen.getByLabelText('Chat message')).toHaveFocus();
  });
});

describe('ChatWidget loading indicator (#225 AC2)', () => {
  const openPanel = async (user) => {
    renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('shows a visible "assistant is typing" indicator while a send is pending, and hides it once the reply arrives', async () => {
    // Holds the response pending, same trick as the "ignores a second send" test above, so there's
    // a real window in which isSending is true and the indicator's mount/unmount can be observed.
    let resolveResponse;
    const responsePromise = new Promise((resolve) => { resolveResponse = resolve; });
    global.fetch = vi.fn().mockReturnValueOnce(responsePromise);
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'What should I watch?{Enter}');

    expect(await screen.findByTestId('chat-typing-indicator')).toBeInTheDocument();

    resolveResponse(jsonResponse({
      conversationId: '11111111-1111-1111-1111-111111111111',
      reply: 'Assistant reply',
    }));
    await screen.findByText('Assistant reply');
    expect(screen.queryByTestId('chat-typing-indicator')).not.toBeInTheDocument();
  });
});

describe('ChatWidget error state (#225 AC1)', () => {
  const openPanel = async (user) => {
    renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('shows a dismissible error alert on a failed send, without discarding the typed message', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('network down'));
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Still here?{Enter}');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong sending your message. Please try again.');
    // #197 AC4's other half, re-asserted alongside the new error Alert this Task adds: the failure
    // must not have removed the message the user already sent.
    expect(screen.getByText('Still here?')).toBeInTheDocument();
  });

  it('dismisses the error alert via its own close button', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('network down'));
    const user = userEvent.setup();
    await openPanel(user);
    await user.type(screen.getByLabelText('Chat message'), 'Still here?{Enter}');
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clears the previous error once a retry succeeds', async () => {
    global.fetch = vi.fn()
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce(jsonResponse({
        conversationId: '11111111-1111-1111-1111-111111111111',
        reply: 'Assistant reply',
      }));
    const user = userEvent.setup();
    await openPanel(user);
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'First try{Enter}');
    await screen.findByRole('alert');
    await user.type(input, 'Second try{Enter}');

    await screen.findByText('Assistant reply');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('ChatWidget screen-reader announcements (#225 AC4)', () => {
  const openPanel = async (user) => {
    renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
  };

  it('announces a new assistant reply through a live region, distinct from the visible bubble', async () => {
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Hello{Enter}');
    await screen.findByText('Assistant reply');

    // role="status" carries an implicit aria-live="polite", the same mechanism a screen reader
    // uses to pick up the change — this asserts the announcement text itself, not just that some
    // element with that role exists.
    expect(screen.getByRole('status')).toHaveTextContent('Assistant: Assistant reply');
  });

  it('announces a failed send through the same live region', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('network down'));
    const user = userEvent.setup();
    await openPanel(user);

    await user.type(screen.getByLabelText('Chat message'), 'Still here?{Enter}');
    await screen.findByRole('alert');

    expect(screen.getByRole('status')).toHaveTextContent('Something went wrong sending your message. Please try again.');
  });
});

describe('ChatWidget accessibility (#225 AC3)', () => {
  it('has no axe violations with a sent message, an assistant reply, and a failed-send error all present', async () => {
    const user = userEvent.setup();
    global.fetch = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        conversationId: '11111111-1111-1111-1111-111111111111',
        reply: 'Assistant reply',
      }))
      .mockRejectedValueOnce(new Error('network down'));
    const { container } = renderChatWidget();
    await user.click(screen.getByRole('button', { name: 'Open chat assistant' }));
    const input = screen.getByLabelText('Chat message');

    await user.type(input, 'First message{Enter}');
    await screen.findByText('Assistant reply');
    await user.type(input, 'Second message{Enter}');
    await screen.findByRole('alert');

    expect(await axe(container)).toHaveNoViolations();
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
