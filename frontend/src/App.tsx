import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './contexts/ToastContext';
import MainLayout from './layouts/MainLayout';
import DeconstructProjectList from './pages/deconstruct/ProjectList';
import DeconstructProjectDetail from './pages/deconstruct/ProjectDetail';
import VisualDashboard from './pages/deconstruct/VisualDashboard';
import CreateProjectList from './pages/create/ProjectList';
import CreateProjectDetail from './pages/create/ProjectDetail';
import CreateProjectWorkflow from './pages/create/ProjectWorkflow';
import CreateProjectGapDetection from './pages/create/ProjectGapDetection';
import { TemplateMatchDebug } from './pages/create/TemplateMatchDebug';
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
            <Route path="deconstruct/detail/:id/visualize" element={<VisualDashboard />} />
            
            <Route path="create" element={<CreateProjectList />} />
            <Route path="create/detail/:id" element={<CreateProjectDetail />} />
            <Route path="create/detail/:id/workflow" element={<CreateProjectWorkflow />} />
            <Route path="create/detail/:id/gap-detection" element={<CreateProjectGapDetection />} />
            <Route path="create/detail/:projectId/template-debug" element={<TemplateMatchDebug />} />
          </Route>
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  );
}
