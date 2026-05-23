import { request } from '../services/http';
import { 
  ApiResponse, 
  DeconstructProjectListApiData, 
  DeconstructProjectDetailApiData, 
  Project 
} from '../types';

export const deconstructApi = {
  // 获取项目列表
  getProjects: () => {
    return request<ApiResponse<DeconstructProjectListApiData>>('/v1/deconstruct/projects', {
      method: 'GET'
    });
  },

  // 获取项目详情
  getProject: (id: string) => {
    return request<ApiResponse<DeconstructProjectDetailApiData>>(`/v1/deconstruct/projects/${id}`, { 
      method: 'GET' 
    });
  },

  // 创建项目
  createProject: (payload: { title: string; description: string; tags: string[]; coverUrl: string }) => {
    return request<ApiResponse<Project>>('/api/v1/deconstruct/projects', {
      method: 'POST',
      body: JSON.stringify(payload),
      headers: { 'Content-Type': 'application/json' }
    });
  },

  // 更新项目
  updateProject: (id: string, payload: { title: string; description: string; tags: string[]; coverUrl: string }) => {
    return request<ApiResponse<Project>>(`/api/v1/deconstruct/projects/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
      headers: { 'Content-Type': 'application/json' }
    });
  },

  // 删除项目
  deleteProject: (id: string) => {
    return request<ApiResponse<boolean>>(`/api/v1/deconstruct/projects/${id}`, { 
      method: 'DELETE' 
    });
  }
};
