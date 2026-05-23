import { request } from '../services/http';
import { 
  ApiResponse, 
  SingleUploadData, 
  BatchUploadData,
  VideoTaskResultData
} from '../types';

export const videoApi = {
  // 单文件上传
  uploadSingle: (file: File, projectId?: string, debugMode = false) => {
    const formData = new FormData();
    formData.append('file', file);
    if (projectId) formData.append('projectId', projectId);
    formData.append('debugMode', String(debugMode));
    
    return request<ApiResponse<SingleUploadData>>('/v1/videos/upload', {
      method: 'POST',
      body: formData
    });
  },

  // 批量上传
  uploadBatch: (files: File[], projectId?: string, debugMode = false) => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    if (projectId) formData.append('projectId', projectId);
    formData.append('debugMode', String(debugMode));
    
    return request<ApiResponse<BatchUploadData>>('/v1/videos/upload/batch', {
      method: 'POST',
      body: formData
    });
  },

  // Debug: 手动触发 ASR 异步分析
  triggerAsrDebug: (taskId: string) => {
    return request<ApiResponse<boolean>>(`/v1/videos/tasks/${taskId}/debug/asr-trigger`, {
      method: 'POST'
    });
  },

  // 删除素材
  deleteMaterial: (materialBizId: string) => {
    return request<ApiResponse<boolean>>(`/v1/videos/materials/${materialBizId}`, { 
      method: 'DELETE' 
    });
  },

  // 查询拆解分析结果
  getTaskResult: (taskId: string, includeTimeline = false) => {
    return request<ApiResponse<VideoTaskResultData>>(`/v1/videos/tasks/${taskId}/result?includeTimeline=${includeTimeline}`, {
      method: 'GET'
    });
  }
};
