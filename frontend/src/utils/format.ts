export const formatBytes = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
};

export const formatDuration = (seconds?: number): string => {
  if (!seconds && seconds !== 0) return '00:00:00';
  const total = Math.max(0, Math.floor(seconds));
  const h = Math.floor(total / 3600).toString().padStart(2, '0');
  const m = Math.floor((total % 3600) / 60).toString().padStart(2, '0');
  const s = (total % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
};

export const extractFileName = (path?: string): string => {
  if (!path) return 'unknown.mp4';
  const normalized = path.replace(/\\/g, '/');
  const segments = normalized.split('/');
  let name = segments[segments.length - 1] || 'unknown.mp4';
  const uuidPrefixRegex = /^[a-fA-F0-9]{32}_/;
  if (uuidPrefixRegex.test(name)) {
    name = name.replace(uuidPrefixRegex, '');
  }
  return name;
};
