import axios from 'axios';
import { AUTH_UNAUTHORIZED_EVENT } from '../constants/auth';
import { clearAccessToken, getAccessToken, isAccessTokenExpired } from '../utils/auth';

export type AuthRequest = {
  login: string;
  password: string;
};

export type AuthResponse = {
  token: string;
};

export type TeamResponse = {
  jiraProjectKey: string;
  name: string;
};

export type SignificantEventResponse = {
  jiraIssueKey: string;
  priority: 'RED' | 'YELLOW' | 'GREEN' | string;
  taskName: string;
  teamName: string;
  teamKey: string;
  assignees: string[];
  problem: string;
  jiraUrl: string;
  acknowledged: boolean;
  acknowledgedAt: string | null;
};

export type DashboardTaskResponse = {
  jiraIssueKey: string;
  initiatorRole: string;
  initiatorName: string;
  taskName: string;
  priority: string | null;
  startDate: string | null;
  dueDate: string | null;
  deadlineOverdue: boolean;
  assignees: string[];
  jiraUrl: string;
  status: 'OPEN' | 'CLOSED' | string;
};

export type DashboardStatsResponse = {
  inWorkCount: number;
  inWorkDelta: number;
  backlogCount: number;
  backlogDelta: number;
  doneCount: number;
  doneDelta: number;
};

export type PersonTaskStatsResponse = {
  personName: string;
  easyCount: number;
  hardCount: number;
};

export type DashboardChartsResponse = {
  completedPerPerson: PersonTaskStatsResponse[];
  activePerPerson: PersonTaskStatsResponse[];
};

export type RoadmapPhaseResponse = {
  phaseName: string;
  assignee: string;
  startDate: string | null;
  endDate: string | null;
};

export type RoadmapTaskResponse = {
  key: string;
  name: string;
  color: string;
  startDate: string | null;
  endDate: string | null;
  phases: RoadmapPhaseResponse[];
};

export type DashboardRoadmapResponse = {
  tasks: RoadmapTaskResponse[];
};

export type CaseType = 'GENERAL' | 'MEETING_SUMMARY' | 'CONFERENCE_INFO' | 'TASK_ASSIGNMENT' | 'JIRA_INFO' | 'WEEKLY_DIGEST';

export type ChatResponse = {
  id: number;
  title: string;
  pinned: boolean;
  createdAt: string;
  caseType: CaseType;
};

export type MessageResponse = {
  id: number;
  text: string;
  sender: 'USER' | 'SYSTEM';
  createdAt: string;
};

export type SendMessageResponse = {
  chatId: number;
  userMessage: MessageResponse;
  aiMessage: MessageResponse;
};

export type EventsRequestParams = {
  teamKey?: string;
  weekStart?: string;
};

export type DashboardTasksRequestParams = {
  teamKey?: string;
  weekStart?: string;
  weekEnd?: string;
};

export type DashboardRoadmapRequestParams = {
  teamKey?: string;
};

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const API_CLIENT = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

API_CLIENT.interceptors.request.use((config) => {
  const token = getAccessToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

API_CLIENT.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const token = getAccessToken();

      if (!token || isAccessTokenExpired(token)) {
        clearAccessToken();
        window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT));
      }
    }

    return Promise.reject(error);
  },
);

export function isUnauthorizedApiError(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

export function getApiErrorMessage(error: unknown, fallback = 'Не удалось выполнить запрос') {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const responseData = error.response?.data;

  if (responseData && typeof responseData === 'object') {
    if ('error' in responseData && typeof responseData.error === 'string') {
      return responseData.error;
    }

    const validationMessages = Object.values(responseData).filter((value): value is string => typeof value === 'string');

    if (validationMessages.length > 0) {
      return validationMessages.join(', ');
    }
  }

  if ((error.response?.status === 401 || error.response?.status === 403) && error.config?.url?.startsWith('/auth/')) {
    return 'Проверьте логин и пароль';
  }

  if (error.response?.status === 401) {
    return 'Нет доступа к данным. Попробуйте обновить страницу или войти заново';
  }

  if (error.response?.status === 403) {
    return 'Недостаточно прав для выполнения запроса';
  }

  return error.message || fallback;
}

export async function login(request: AuthRequest) {
  const response = await API_CLIENT.post<AuthResponse>('/auth/login', request);

  return response.data;
}

export async function register(request: AuthRequest) {
  const response = await API_CLIENT.post<AuthResponse>('/auth/register', request);

  return response.data;
}

export async function fetchTeams() {
  const response = await API_CLIENT.get<TeamResponse[]>('/teams');

  return response.data;
}

export async function fetchSignificantEvents(params: EventsRequestParams) {
  const response = await API_CLIENT.get<SignificantEventResponse[]>('/events', { params });

  return response.data;
}

export async function acknowledgeEvent(issueKey: string) {
  const response = await API_CLIENT.patch<SignificantEventResponse>(`/events/${encodeURIComponent(issueKey)}/acknowledge`);

  return response.data;
}

export async function fetchDashboardTasks(params: DashboardTasksRequestParams) {
  const response = await API_CLIENT.get<DashboardTaskResponse[]>('/dashboard/tasks', { params });

  return response.data;
}

export async function fetchDashboardStats(params: DashboardTasksRequestParams) {
  const response = await API_CLIENT.get<DashboardStatsResponse>('/dashboard/stats', { params });

  return response.data;
}

export async function fetchDashboardCharts(params: DashboardTasksRequestParams) {
  const response = await API_CLIENT.get<DashboardChartsResponse>('/dashboard/charts', { params });

  return response.data;
}

export async function fetchDashboardRoadmap(params: DashboardRoadmapRequestParams) {
  const response = await API_CLIENT.get<DashboardRoadmapResponse>('/dashboard/roadmap', { params });

  return response.data;
}

export async function fetchChats() {
  const response = await API_CLIENT.get<ChatResponse[]>('/chats');

  return response.data;
}

export async function fetchChatMessages(chatId: number) {
  const response = await API_CLIENT.get<MessageResponse[]>(`/chats/${chatId}/messages`);

  return response.data;
}

export async function sendMessage(request: { chatId?: number | null; text: string; caseType?: CaseType }) {
  const response = await API_CLIENT.post<SendMessageResponse>('/messages', request);

  return response.data;
}

export async function updateChatTitle(chatId: number, title: string) {
  const response = await API_CLIENT.patch<ChatResponse>(`/chats/${chatId}/title`, { title });

  return response.data;
}

export async function toggleChatPin(chatId: number) {
  const response = await API_CLIENT.patch<ChatResponse>(`/chats/${chatId}/pin`);

  return response.data;
}

export async function deleteChat(chatId: number) {
  await API_CLIENT.delete(`/chats/${chatId}`);
}
