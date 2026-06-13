import { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { IconButton } from '@alfalab/core-components/icon-button';
import { Popover } from '@alfalab/core-components/popover';
import { ChevronDownMIcon } from '@alfalab/icons-glyph/ChevronDownMIcon';
import { DotsThreeVerticalSIcon } from '@alfalab/icons-glyph/DotsThreeVerticalSIcon';
import { PencilSIcon } from '@alfalab/icons-glyph/PencilSIcon';
import { PlusCircleMIcon } from '@alfalab/icons-glyph/PlusCircleMIcon';
import { PushpinMIcon } from '@alfalab/icons-glyph/PushpinMIcon';
import { SendMIcon } from '@alfalab/icons-glyph/SendMIcon';
import { TrashCanSIcon } from '@alfalab/icons-glyph/TrashCanSIcon';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  deleteChat,
  fetchChatMessages,
  fetchChats,
  getApiErrorMessage,
  sendMessage,
  toggleChatPin,
  updateChatTitle,
  type ChatResponse,
  type MessageResponse,
} from '../../api/client';
import styles from './ChatPage.module.css';

type ChatRole = 'user' | 'assistant';
type ThreadPeriod = 'today' | 'week' | 'month';

type ChatMessage = {
  id: string;
  backendId?: number;
  role: ChatRole;
  text: string;
};

type ChatThread = {
  id: string;
  backendId: number | null;
  title: string;
  period: ThreadPeriod;
  pinned?: boolean;
  createdAt?: string;
  messages: ChatMessage[];
};

const EMPTY_THREAD_TITLE = 'Новый чат';

const ROLE_OPTIONS = ['Руководитель', 'Аналитик', 'Разработчик'] as const;

const SUGGESTIONS = ['Дайджест за неделю', 'Саммари встречи', 'Создать задачу', 'Найти в confluence / jira'];

const THREAD_GROUPS: { id: ThreadPeriod; label: string }[] = [
  { id: 'today', label: 'Сегодня' },
  { id: 'week', label: '7 дней' },
  { id: 'month', label: '30 дней' },
];

const createId = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 10)}`;

const createEmptyThread = (): ChatThread => ({
  id: createId('chat'),
  backendId: null,
  title: EMPTY_THREAD_TITLE,
  period: 'today',
  messages: [],
});

const createThreadTitle = (prompt: string) => {
  const normalizedPrompt = prompt.replace(/\s+/g, ' ').trim();

  if (normalizedPrompt.length <= 42) {
    return normalizedPrompt;
  }

  return `${normalizedPrompt.slice(0, 39)}...`;
};

const DAY_IN_MS = 24 * 60 * 60 * 1000;

function resolveThreadPeriod(createdAt: string): ThreadPeriod {
  const createdTime = new Date(createdAt).getTime();

  if (Number.isNaN(createdTime)) {
    return 'month';
  }

  const age = Date.now() - createdTime;

  if (age < DAY_IN_MS) {
    return 'today';
  }

  if (age < DAY_IN_MS * 7) {
    return 'week';
  }

  return 'month';
}

function mapChat(chat: ChatResponse, messages: ChatMessage[] = []): ChatThread {
  return {
    id: String(chat.id),
    backendId: chat.id,
    title: chat.title || EMPTY_THREAD_TITLE,
    period: resolveThreadPeriod(chat.createdAt),
    pinned: chat.pinned,
    createdAt: chat.createdAt,
    messages,
  };
}

function mapMessage(message: MessageResponse): ChatMessage {
  return {
    id: String(message.id),
    backendId: message.id,
    role: message.sender === 'USER' ? 'user' : 'assistant',
    text: message.text,
  };
}

function mergeMessageResponses(currentMessages: MessageResponse[] | undefined, nextMessages: MessageResponse[]) {
  const messagesById = new Map<number, MessageResponse>();

  [...(currentMessages ?? []), ...nextMessages].forEach((message) => {
    messagesById.set(message.id, message);
  });

  return [...messagesById.values()];
}

export function ChatPage() {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const fallbackThread = useMemo(() => createEmptyThread(), []);
  const [localThreads, setLocalThreads] = useState<ChatThread[]>([]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [pendingMessagesByThreadId, setPendingMessagesByThreadId] = useState<Record<string, ChatMessage[]>>({});
  const [draft, setDraft] = useState('Напиши мне еженедельный отчёт');
  const [selectedRole, setSelectedRole] = useState<(typeof ROLE_OPTIONS)[number]>(ROLE_OPTIONS[0]);
  const [roleMenuOpen, setRoleMenuOpen] = useState(false);
  const [openMenuThreadId, setOpenMenuThreadId] = useState<string | null>(null);
  const [menuAnchorElement, setMenuAnchorElement] = useState<HTMLElement | null>(null);
  const [threadIdPendingDelete, setThreadIdPendingDelete] = useState<string | null>(null);
  const [renamingThreadId, setRenamingThreadId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [respondingThreadId, setRespondingThreadId] = useState<string | null>(null);
  const [chatError, setChatError] = useState<string | null>(null);
  const chatsQuery = useQuery({ queryKey: ['chats'], queryFn: fetchChats });
  const baseServerThreads = useMemo(() => (chatsQuery.data ?? []).map((chat) => mapChat(chat)), [chatsQuery.data]);
  const baseThreads = useMemo(() => [...localThreads, ...baseServerThreads], [baseServerThreads, localThreads]);
  const activeBaseThread = useMemo(
    () => baseThreads.find((thread) => thread.id === activeThreadId) ?? baseThreads[0] ?? fallbackThread,
    [activeThreadId, baseThreads, fallbackThread],
  );
  const activeBackendChatId = activeBaseThread.backendId;
  const messagesQuery = useQuery({
    queryKey: ['chat-messages', activeBackendChatId],
    queryFn: () => fetchChatMessages(activeBackendChatId as number),
    enabled: activeBackendChatId !== null,
  });
  const serverThreads = useMemo(
    () =>
      (chatsQuery.data ?? []).map((chat) => {
        const cachedMessages =
          chat.id === activeBackendChatId
            ? (messagesQuery.data ?? queryClient.getQueryData<MessageResponse[]>(['chat-messages', chat.id]))
            : queryClient.getQueryData<MessageResponse[]>(['chat-messages', chat.id]);
        const pendingMessages = pendingMessagesByThreadId[String(chat.id)] ?? [];

        return mapChat(chat, [...(cachedMessages ?? []).map(mapMessage), ...pendingMessages]);
      }),
    [activeBackendChatId, chatsQuery.data, messagesQuery.data, pendingMessagesByThreadId, queryClient],
  );
  const threads = useMemo(() => {
    const nextThreads = [...localThreads, ...serverThreads];

    return nextThreads.length > 0 ? nextThreads : [fallbackThread];
  }, [fallbackThread, localThreads, serverThreads]);
  const activeThread = useMemo(
    () => threads.find((thread) => thread.id === activeThreadId) ?? threads[0],
    [activeThreadId, threads],
  );
  const sendMessageMutation = useMutation({
    mutationFn: ({
      chatId,
      text,
    }: {
      targetThreadId: string;
      tempMessageId: string;
      chatId: number | null;
      text: string;
    }) => sendMessage({ chatId, text }),
    onSuccess: (response, variables) => {
      const nextThreadId = String(response.chatId);

      setLocalThreads((currentThreads) => currentThreads.filter((thread) => thread.id !== variables.targetThreadId));
      setPendingMessagesByThreadId((currentMessages) => {
        const nextMessages = { ...currentMessages };

        delete nextMessages[variables.targetThreadId];
        return nextMessages;
      });
      setActiveThreadId(nextThreadId);
      queryClient.setQueryData<ChatResponse[]>(['chats'], (currentChats) => {
        if (currentChats?.some((chat) => chat.id === response.chatId)) {
          return currentChats;
        }

        return [
          {
            id: response.chatId,
            title: createThreadTitle(variables.text),
            pinned: false,
            createdAt: new Date().toISOString(),
            caseType: 'GENERAL',
          },
          ...(currentChats ?? []),
        ];
      });
      queryClient.invalidateQueries({ queryKey: ['chats'] });
      queryClient.setQueryData<MessageResponse[]>(['chat-messages', response.chatId], (currentMessages) =>
        mergeMessageResponses(currentMessages, [response.userMessage, response.aiMessage]),
      );
    },
    onError: (error, variables) => {
      setLocalThreads((currentThreads) =>
        currentThreads.map((thread) =>
          thread.id === variables.targetThreadId
            ? { ...thread, messages: thread.messages.filter((message) => message.id !== variables.tempMessageId) }
            : thread,
        ),
      );
      setPendingMessagesByThreadId((currentMessages) => ({
        ...currentMessages,
        [variables.targetThreadId]: (currentMessages[variables.targetThreadId] ?? []).filter(
          (message) => message.id !== variables.tempMessageId,
        ),
      }));
      setChatError(getApiErrorMessage(error, 'Не удалось отправить сообщение'));
    },
    onSettled: () => setRespondingThreadId(null),
  });
  const renameMutation = useMutation({
    mutationFn: ({ chatId, title }: { threadId: string; chatId: number; title: string }) => updateChatTitle(chatId, title),
    onSuccess: (updatedChat, variables) => {
      queryClient.setQueryData<ChatResponse[]>(['chats'], (currentChats) =>
        currentChats?.map((chat) =>
          chat.id === updatedChat.id ? { ...chat, title: updatedChat.title || variables.title } : chat,
        ),
      );
      queryClient.invalidateQueries({ queryKey: ['chats'] });
    },
    onError: (error) => setChatError(getApiErrorMessage(error, 'Не удалось переименовать чат')),
  });
  const pinMutation = useMutation({
    mutationFn: ({ chatId }: { threadId: string; chatId: number }) => toggleChatPin(chatId),
    onSuccess: (updatedChat, variables) => {
      queryClient.setQueryData<ChatResponse[]>(['chats'], (currentChats) =>
        currentChats?.map((chat) => (chat.id === updatedChat.id ? { ...chat, pinned: updatedChat.pinned } : chat)),
      );
      setOpenMenuThreadId((currentThreadId) => (currentThreadId === variables.threadId ? null : currentThreadId));
      queryClient.invalidateQueries({ queryKey: ['chats'] });
    },
    onError: (error) => setChatError(getApiErrorMessage(error, 'Не удалось закрепить чат')),
  });
  const deleteMutation = useMutation({
    mutationFn: ({ chatId }: { threadId: string; chatId: number }) => deleteChat(chatId),
    onSuccess: (_, variables) => {
      removeThread(variables.threadId);
      queryClient.setQueryData<ChatResponse[]>(['chats'], (currentChats) =>
        currentChats?.filter((chat) => chat.id !== variables.chatId),
      );
      queryClient.invalidateQueries({ queryKey: ['chats'] });
    },
    onError: (error) => setChatError(getApiErrorMessage(error, 'Не удалось удалить чат')),
  });

  const groupedThreads = useMemo(
    () =>
      THREAD_GROUPS.map((group) => ({
        ...group,
        threads: threads
          .filter((thread) => thread.period === group.id)
          .toSorted((firstThread, secondThread) => Number(secondThread.pinned) - Number(firstThread.pinned)),
      })).filter((group) => group.threads.length > 0),
    [threads],
  );

  const activeThreadResponding = respondingThreadId === activeThread?.id;
  const activeMessagesLoading = messagesQuery.isLoading && activeBackendChatId !== null;
  const hasMessages = Boolean(activeThread?.messages.length || activeMessagesLoading || chatError);
  const menuThread = threads.find((thread) => thread.id === openMenuThreadId);

  useEffect(() => {
    if (!openMenuThreadId) {
      return undefined;
    }

    const handleDocumentMouseDown = (event: MouseEvent) => {
      const target = event.target;

      if (target instanceof Element && target.closest('[data-chat-actions-menu]')) {
        return;
      }

      setOpenMenuThreadId(null);
      setMenuAnchorElement(null);
    };

    document.addEventListener('mousedown', handleDocumentMouseDown);

    return () => document.removeEventListener('mousedown', handleDocumentMouseDown);
  }, [openMenuThreadId]);

  const handleNewChat = () => {
    const newThread = createEmptyThread();

    setLocalThreads((currentThreads) => [newThread, ...currentThreads]);
    setActiveThreadId(newThread.id);
    setDraft('');
    setOpenMenuThreadId(null);
    setMenuAnchorElement(null);
    window.setTimeout(() => inputRef.current?.focus(), 0);
  };

  const handleSendPrompt = (prompt: string) => {
    const trimmedPrompt = prompt.trim();

    if (!activeThread || !trimmedPrompt || respondingThreadId || sendMessageMutation.isPending) {
      return;
    }

    const targetThreadId = activeThread.id;
    const tempMessageId = createId('user-message');
    const userMessage: ChatMessage = {
      id: tempMessageId,
      role: 'user',
      text: trimmedPrompt,
    };

    setChatError(null);
    if (activeThread.backendId === null) {
      setLocalThreads((currentThreads) => {
        const targetExists = currentThreads.some((thread) => thread.id === targetThreadId);
        const nextThread = {
          ...activeThread,
          title: activeThread.messages.length === 0 ? createThreadTitle(trimmedPrompt) : activeThread.title,
          messages: [...activeThread.messages, userMessage],
        };

        return targetExists
          ? currentThreads.map((thread) => (thread.id === targetThreadId ? nextThread : thread))
          : [nextThread, ...currentThreads];
      });
    } else {
      setPendingMessagesByThreadId((currentMessages) => ({
        ...currentMessages,
        [targetThreadId]: [...(currentMessages[targetThreadId] ?? []), userMessage],
      }));
    }
    setDraft('');
    setRespondingThreadId(targetThreadId);
    sendMessageMutation.mutate({
      targetThreadId,
      tempMessageId,
      chatId: activeThread.backendId,
      text: trimmedPrompt,
    });
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    handleSendPrompt(draft);
  };

  const handleSuggestionClick = (suggestion: string) => {
    setDraft(suggestion);
    inputRef.current?.focus();
  };

  const handleStartRename = (thread: ChatThread) => {
    setRenameValue(thread.title);
    setRenamingThreadId(thread.id);
    setOpenMenuThreadId(null);
    setMenuAnchorElement(null);
  };

  const handleFinishRename = () => {
    const nextTitle = renameValue.trim();

    if (!renamingThreadId || !nextTitle) {
      setRenamingThreadId(null);
      return;
    }

    const renamedThread = threads.find((thread) => thread.id === renamingThreadId);

    if (!renamedThread) {
      setRenamingThreadId(null);
      return;
    }

    if (renamedThread.backendId === null) {
      setLocalThreads((currentThreads) =>
        currentThreads.map((thread) => (thread.id === renamingThreadId ? { ...thread, title: nextTitle } : thread)),
      );
    } else if (renamedThread.title !== nextTitle) {
      renameMutation.mutate({ threadId: renamedThread.id, chatId: renamedThread.backendId, title: nextTitle });
    }

    setRenamingThreadId(null);
  };

  const handleRenameKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      handleFinishRename();
    }

    if (event.key === 'Escape') {
      setRenamingThreadId(null);
    }
  };

  const handleTogglePinned = (threadId: string) => {
    const thread = threads.find((currentThread) => currentThread.id === threadId);

    if (!thread) {
      return;
    }

    if (thread.backendId === null) {
      setLocalThreads((currentThreads) =>
        currentThreads.map((currentThread) =>
          currentThread.id === threadId ? { ...currentThread, pinned: !currentThread.pinned } : currentThread,
        ),
      );
    } else {
      pinMutation.mutate({ threadId, chatId: thread.backendId });
    }

    setOpenMenuThreadId(null);
    setMenuAnchorElement(null);
  };

  const handleRequestDelete = (threadId: string) => {
    setThreadIdPendingDelete(threadId);
    setOpenMenuThreadId(null);
    setMenuAnchorElement(null);
  };

  function removeThread(threadId: string) {
    const nextLocalThreads = localThreads.filter((thread) => thread.id !== threadId);

    if (activeThreadId === threadId) {
      const nextActiveThread =
        [...nextLocalThreads, ...serverThreads.filter((thread) => thread.id !== threadId)][0] ?? fallbackThread;
      setActiveThreadId(nextActiveThread.id);
    }

    setLocalThreads(nextLocalThreads);
  }

  const handleConfirmDelete = () => {
    if (!threadIdPendingDelete) {
      return;
    }

    const thread = threads.find((currentThread) => currentThread.id === threadIdPendingDelete);

    if (!thread) {
      setThreadIdPendingDelete(null);
      return;
    }

    if (respondingThreadId === threadIdPendingDelete) {
      setRespondingThreadId(null);
    }

    if (thread.backendId === null) {
      removeThread(threadIdPendingDelete);
    } else {
      deleteMutation.mutate({ threadId: threadIdPendingDelete, chatId: thread.backendId });
    }

    setThreadIdPendingDelete(null);
  };

  const threadPendingDelete = threads.find((thread) => thread.id === threadIdPendingDelete);

  return (
    <section className={styles.page} aria-label="Чат с ИИ">
      <div className={styles.workspace}>
        <div className={styles.roleSwitcher}>
          <button
            aria-expanded={roleMenuOpen}
            aria-haspopup="listbox"
            className={styles.roleButton}
            type="button"
            onClick={() => setRoleMenuOpen((currentValue) => !currentValue)}
          >
            <span>{selectedRole}</span>
            <ChevronDownMIcon className={clsx(styles.roleIcon, roleMenuOpen && styles.roleIconOpen)} />
          </button>

          {roleMenuOpen && (
            <div className={styles.roleMenu} role="listbox">
              {ROLE_OPTIONS.map((role) => (
                <button
                  key={role}
                  aria-selected={role === selectedRole}
                  className={clsx(styles.roleOption, role === selectedRole && styles.roleOptionActive)}
                  role="option"
                  type="button"
                  onClick={() => {
                    setSelectedRole(role);
                    setRoleMenuOpen(false);
                  }}
                >
                  {role}
                </button>
              ))}
            </div>
          )}
        </div>

        {hasMessages ? (
          <div className={styles.conversation}>
            <div className={styles.messages} aria-live="polite">
              {activeMessagesLoading && (
                <article className={clsx(styles.message, styles.aiMessage)}>
                  <span className={styles.messageAuthor}>VPIKe AI</span>
                  <p className={styles.messageText}>Загружаю историю...</p>
                </article>
              )}

              {activeThread?.messages.map((message) => (
                <article
                  key={message.id}
                  className={clsx(styles.message, message.role === 'user' ? styles.userMessage : styles.aiMessage)}
                >
                  <span className={styles.messageAuthor}>{message.role === 'user' ? 'Вы' : 'VPIKe AI'}</span>
                  <p className={styles.messageText}>{message.text}</p>
                </article>
              ))}

              {activeThreadResponding && (
                <article className={clsx(styles.message, styles.aiMessage)}>
                  <span className={styles.messageAuthor}>VPIKe AI</span>
                  <p className={styles.messageText}>Печатает ответ...</p>
                </article>
              )}

              {chatError && (
                <article className={clsx(styles.message, styles.errorMessage)} role="alert">
                  <span className={styles.messageAuthor}>Ошибка</span>
                  <p className={styles.messageText}>{chatError}</p>
                </article>
              )}
            </div>

            <ChatComposer
              draft={draft}
              inputRef={inputRef}
              responding={Boolean(respondingThreadId) || sendMessageMutation.isPending}
              onDraftChange={setDraft}
              onSubmit={handleSubmit}
            />
          </div>
        ) : (
          <div className={styles.emptyState}>
            <div className={styles.emptyContent}>
              <h1 className={styles.greeting}>
                <span>Привет!</span>
                <span>Чем помочь сегодня?</span>
              </h1>

              <ChatComposer
                draft={draft}
                inputRef={inputRef}
                responding={Boolean(respondingThreadId) || sendMessageMutation.isPending}
                onDraftChange={setDraft}
                onSubmit={handleSubmit}
              />

              <div className={styles.suggestions} aria-label="Быстрые запросы">
                {SUGGESTIONS.map((suggestion) => (
                  <Button
                    key={suggestion}
                    className={styles.suggestionButton}
                    size={40}
                    view="secondary"
                    onClick={() => handleSuggestionClick(suggestion)}
                  >
                    {suggestion}
                  </Button>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>

      <aside className={styles.historyPanel} aria-label="История чатов">
        <Button
          className={styles.newChatButton}
          leftAddons={<PlusCircleMIcon className={styles.newChatIcon} />}
          size={40}
          view="secondary"
          onClick={handleNewChat}
        >
          Новый чат
        </Button>

        {chatsQuery.isLoading && <p className={styles.panelState}>Загружаем чаты...</p>}
        {chatsQuery.isError && (
          <p className={styles.panelError} role="alert">
            {getApiErrorMessage(chatsQuery.error, 'Не удалось загрузить чаты')}
          </p>
        )}

        <div className={styles.historyGroups}>
          {groupedThreads.map((group) => (
            <section key={group.id} className={styles.historyGroup} aria-labelledby={`chat-group-${group.id}`}>
              <h2 id={`chat-group-${group.id}`} className={styles.historyGroupTitle}>
                {group.label}
              </h2>

              <div className={styles.threadList}>
                {group.threads.map((thread) => {
                  const active = thread.id === activeThread?.id;
                  const menuOpen = openMenuThreadId === thread.id;
                  const renaming = renamingThreadId === thread.id;

                  return (
                    <div key={thread.id} className={clsx(styles.threadRow, active && styles.threadRowActive)}>
                      <button
                        className={clsx(styles.threadButton, active && styles.threadButtonActive)}
                        type="button"
                        onClick={() => setActiveThreadId(thread.id)}
                      >
                        {renaming ? (
                          <input
                            autoFocus
                            className={styles.renameInput}
                            value={renameValue}
                            onBlur={handleFinishRename}
                            onChange={(event) => setRenameValue(event.target.value)}
                            onClick={(event) => event.stopPropagation()}
                            onKeyDown={handleRenameKeyDown}
                          />
                        ) : (
                          <span className={styles.threadTitle}>{thread.title}</span>
                        )}
                      </button>

                      <div
                        data-chat-actions-menu
                        className={clsx(styles.threadActions, menuOpen && styles.threadActionsOpen)}
                        onMouseEnter={() => setOpenMenuThreadId(thread.id)}
                      >
                        <button
                          aria-expanded={menuOpen}
                          aria-label={`Действия с чатом ${thread.title}`}
                          className={styles.threadMenuButton}
                          type="button"
                          onClick={(event) => {
                            const nextThreadId = menuOpen ? null : thread.id;

                            setOpenMenuThreadId(nextThreadId);
                            setMenuAnchorElement(nextThreadId ? event.currentTarget : null);
                          }}
                          onFocus={(event) => {
                            setMenuAnchorElement(event.currentTarget);
                          }}
                          onKeyDown={(event) => {
                            if (event.key !== 'Enter' && event.key !== ' ') {
                              return;
                            }

                            event.preventDefault();
                            event.stopPropagation();

                            const nextThreadId = menuOpen ? null : thread.id;

                            setOpenMenuThreadId(nextThreadId);
                            setMenuAnchorElement(nextThreadId ? event.currentTarget : null);
                          }}
                          onMouseEnter={(event) => {
                            setOpenMenuThreadId(thread.id);
                            setMenuAnchorElement(event.currentTarget);
                          }}
                        >
                          <DotsThreeVerticalSIcon />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      </aside>

      <Popover
        anchorElement={menuAnchorElement}
        fallbackPlacements={['top-end', 'left-end', 'left-start']}
        getPortalContainer={() => document.body}
        offset={[0, 8]}
        open={Boolean(menuThread && menuAnchorElement && !threadPendingDelete)}
        popperClassName={styles.threadMenuPopover}
        position="bottom-end"
        withTransition={false}
        zIndex={40}
      >
        {menuThread && (
          <div className={styles.threadMenu} data-chat-actions-menu role="menu">
            <button
              className={styles.threadMenuItem}
              role="menuitem"
              type="button"
              onClick={() => handleStartRename(menuThread)}
            >
              <PencilSIcon className={styles.threadMenuIcon} />
              Переименовать
            </button>
            <button
              className={styles.threadMenuItem}
              role="menuitem"
              type="button"
              onClick={() => handleTogglePinned(menuThread.id)}
            >
              <PushpinMIcon className={styles.threadMenuIcon} />
              {menuThread.pinned ? 'Открепить' : 'Закрепить'}
            </button>
            <button
              className={clsx(styles.threadMenuItem, styles.threadMenuItemDanger)}
              role="menuitem"
              type="button"
              onClick={() => handleRequestDelete(menuThread.id)}
            >
              <TrashCanSIcon className={styles.threadMenuIcon} />
              Удалить
            </button>
          </div>
        )}
      </Popover>

      {threadPendingDelete && (
        <div className={styles.modalOverlay} role="presentation">
          <div
            aria-describedby="delete-chat-description"
            aria-labelledby="delete-chat-title"
            aria-modal="true"
            className={styles.deleteDialog}
            role="dialog"
          >
            <div className={styles.dialogText}>
              <h2 id="delete-chat-title" className={styles.dialogTitle}>
                Удалить чат?
              </h2>
              <p id="delete-chat-description" className={styles.dialogDescription}>
                После удаления чат невозможно будет восстановить
              </p>
            </div>
            <div className={styles.dialogActions}>
              <Button className={styles.cancelButton} size={40} view="secondary" onClick={() => setThreadIdPendingDelete(null)}>
                Отмена
              </Button>
              <Button className={styles.deleteButton} size={40} view="primary" onClick={handleConfirmDelete}>
                Удалить
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

type ChatComposerProps = {
  draft: string;
  inputRef: React.RefObject<HTMLInputElement | null>;
  responding: boolean;
  onDraftChange: (value: string) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
};

function ChatComposer({ draft, inputRef, responding, onDraftChange, onSubmit }: ChatComposerProps) {
  return (
    <form className={styles.composer} onSubmit={onSubmit}>
      <input
        ref={inputRef}
        aria-label="Сообщение для VPIKe AI"
        className={styles.composerInput}
        disabled={responding}
        placeholder="Спросите что-нибудь"
        value={draft}
        onChange={(event) => onDraftChange(event.target.value)}
      />
      <IconButton
        aria-label="Отправить сообщение"
        className={styles.sendButton}
        disabled={!draft.trim() || responding}
        icon={SendMIcon}
        size={32}
        type="submit"
        view="primary"
      />
    </form>
  );
}
