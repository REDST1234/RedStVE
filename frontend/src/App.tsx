import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './contexts/ToastContext';
import MainLayout from './layouts/MainLayout';
import DeconstructProjectList from './pages/deconstruct/ProjectList';
import DeconstructProjectDetail from './pages/deconstruct/ProjectDetail';
import CreateProjectList from './pages/create/ProjectList';
import CreateProjectDetail from './pages/create/ProjectDetail';
import './index.css';

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <Routes>
          <Route path="/" element={<Navigate to="/deconstruct" replace />} />
          <Route element={<MainLayout />}>
            {/* 拆解流模块 */}
            <Route path="deconstruct" element={<DeconstructProjectList />} />
            <Route path="deconstruct/detail/:id" element={<DeconstructProjectDetail />} />
            
            <Route path="create" element={<CreateProjectList />} />
            <Route path="create/detail/:id" element={<CreateProjectDetail />} />
          </Route>
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  );
}
