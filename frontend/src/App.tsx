import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthLayout } from './components/AuthLayout/AuthLayout';
import { ROUTES } from './constants/routes';
import { AuthPage } from './pages/AuthPage/AuthPage';
import { AnalyticsPage } from './pages/AnalyticsPage/AnalyticsPage';
import { ChatPage } from './pages/ChatPage/ChatPage';
import { SignificantEventsPage } from './pages/SignificantEventsPage/SignificantEventsPage';

const QUERY_CLIENT = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={QUERY_CLIENT}>
      <BrowserRouter>
        <Routes>
          <Route path={ROUTES.AUTH} element={<AuthPage />} />
          <Route element={<AuthLayout />}>
            <Route path={ROUTES.SIGNIFICANT_EVENTS} element={<SignificantEventsPage />} />
            <Route path={ROUTES.ANALYTICS} element={<AnalyticsPage />} />
            <Route path={ROUTES.CHAT} element={<ChatPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
