import React, { useRef, useState, useEffect } from 'react';

interface ScenePreviewerProps {
  script: any;
  sceneIndex: number;
  aspectRatio: string;
}

export const ScenePreviewer: React.FC<ScenePreviewerProps> = ({ script, sceneIndex, aspectRatio }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  if (!script || !script.scenes || !script.scenes[sceneIndex]) {
    return <div className="text-slate-400">暂无分镜数据</div>;
  }

  const scene = script.scenes[sceneIndex];
  const globalStyle = script.globalStyle || {};
  
  const width = aspectRatio === '16:9' ? 640 : aspectRatio === '1:1' ? 400 : 360;
  const height = aspectRatio === '16:9' ? 360 : aspectRatio === '1:1' ? 400 : 640;

  const totalFrames = scene.durationInFrames || 90;
  const [currentFrame, setCurrentFrame] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);

  // 自动播放逻辑
  useEffect(() => {
    if (!isPlaying) return;
    const interval = setInterval(() => {
      setCurrentFrame(prev => {
        if (prev >= totalFrames) return 0;
        return prev + 1;
      });
    }, 1000 / 30); // ~33ms per frame
    return () => clearInterval(interval);
  }, [isPlaying, totalFrames]);

  useEffect(() => {
    const observer = new ResizeObserver((entries) => {
      if (entries[0]) {
        // 计算可用空间，减去底部文字的高度预留
        const { width: containerWidth, height: containerHeight } = entries[0].contentRect;
        const availableHeight = containerHeight - 40; 
        const scaleX = containerWidth / width;
        const scaleY = availableHeight / height;
        const newScale = Math.min(scaleX, scaleY);
        // 留出 5% 的安全边距
        setScale(newScale > 0 ? newScale * 0.95 : 1);
      }
    });

    if (containerRef.current) {
      observer.observe(containerRef.current);
    }
    return () => observer.disconnect();
  }, [width, height]);

  // 辅助函数：根据字分层映射实际字体
  const getFontFamily = (tier: string) => {
    switch (tier) {
      case 'title': return '"ZCOOL XiaoWei", serif';
      case 'subtitle': return '"Noto Sans SC", sans-serif';
      case 'accent': return '"Ma Shan Zheng", cursive';
      case 'ui': return 'Inter, sans-serif';
      case 'number': return '"Bebas Neue", cursive';
      case 'bodySerif': return '"Noto Serif SC", serif';
      case 'kaiStyle': return '"LXGW WenKai TC", serif';
      default: return '"Noto Sans SC", sans-serif';
    }
  };

  // 辅助函数：解析 positionPreset
  const getPositionStyles = (preset: string): React.CSSProperties => {
    switch (preset) {
      case 'hero_top': return { top: '20%', left: '50%', transform: 'translate(-50%, -50%)' };
      case 'hero_center': return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' };
      case 'hero_lower': return { bottom: '25%', left: '50%', transform: 'translate(-50%, 50%)' };
      case 'left_focus': return { top: '50%', left: '30%', transform: 'translate(-50%, -50%)' };
      case 'right_focus': return { top: '50%', right: '30%', transform: 'translate(50%, -50%)' };
      default: return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }; // 默认居中
    }
  };

  return (
    <div className="w-full h-full flex flex-col items-center justify-center p-4 overflow-hidden relative group" ref={containerRef}>
      {/* 画面预览框 - 开启 scale 缩放 */}
      <div 
        className="relative overflow-hidden transition-colors duration-300 rounded-lg shadow-md shrink-0 mb-8"
        style={{
          width: `${width}px`,
          height: `${height}px`,
          backgroundColor: globalStyle.backgroundColor || '#050F1E',
          transform: `scale(${scale})`,
          transformOrigin: 'center center'
        }}
      >
        {/* 图层按顺序叠加 */}
        {scene.layers?.map((layer: any, idx: number) => {
          const { preset, params, enterAtFrame = 0, durationInFrames = totalFrames } = layer;
          const isVisible = currentFrame >= enterAtFrame && currentFrame <= (enterAtFrame + durationInFrames);
          
          if (!isVisible && preset !== 'backing.glass_plate' && preset !== 'bg.mesh_gradient' && preset !== 'bg.tech_grid' && preset !== 'bg.noise_grain') {
             // 对于文字和媒体图层，如果不处于活跃帧区间，将其设为透明 (也可直接返回 null 提升性能)
             // 返回 null 会打断动画，透明度更好
             // 为了简化，由于只是静态草图，这里如果不可见直接返回 null
             return null;
          }
          
          // 渲染衬底层 backing.*
          if (preset?.startsWith('backing.')) {
            return (
              <div key={idx} style={{
                position: 'absolute',
                top: 0, left: 0, right: 0, bottom: 0,
                margin: 'auto',
                width: params?.width || '80%',
                height: params?.height || '40%',
                backgroundColor: params?.color || 'rgba(0,0,0,0.5)',
                opacity: params?.opacity || 1,
                borderRadius: `${params?.borderRadius || 8}px`,
                border: params?.borderColor ? `${params?.borderWidth || 1}px solid ${params.borderColor}` : 'none',
                backdropFilter: preset === 'backing.glass_plate' ? `blur(${params?.blurAmount || 10}px)` : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none' // 让背后的层点击透过
              }} />
            );
          }

          // 渲染文字层 text.* 和 caption.subtitle
          if (preset?.startsWith('text.') || preset === 'caption.subtitle' || preset === 'overlay.badge_pop') {
            const posStyles = getPositionStyles(params?.positionPreset);
            const fontFamily = getFontFamily(params?.fontTier || globalStyle.fontTier);
            
            // 对多文本数组 (hero_billboard) 和单文本的处理
            const textContent = params?.texts ? params.texts.join('\n') : (params?.text || '');
            
            // 针对数字滚动做特殊预览
            const displayValue = preset === 'text.counter_number' ? 
              `${params?.prefix || ''}${params?.value || 0}${params?.suffix || ''}` : textContent;

            // 针对标签胶囊的渲染
            if (preset === 'text.label_chip') {
                return (
                    <div key={idx} style={{
                        position: 'absolute',
                        ...posStyles,
                        backgroundColor: params?.bgColor || 'rgba(255,255,255,0.1)',
                        border: params?.variant === 'outlined' ? '1px solid #fff' : 'none',
                        color: params?.color || '#fff',
                        padding: '4px 8px',
                        borderRadius: `${params?.borderRadius || 16}px`,
                        fontFamily,
                        fontSize: `${(params?.fontSize || 24) / 1.5}px`,
                        fontWeight: params?.fontWeight || 'normal',
                        textAlign: params?.textAlign || 'center',
                        whiteSpace: 'pre-wrap',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px'
                      }}>
                        {params?.icon && <span>{params.icon}</span>}
                        <span>{params?.text || ''}</span>
                      </div>
                )
            }

            return (
              <div key={idx} style={{
                position: 'absolute',
                ...posStyles,
                color: params?.color || '#ffffff',
                fontSize: `${(params?.fontSize || 40) / 1.5}px`, // 缩小比例展示
                fontFamily,
                fontWeight: params?.fontWeight || 'bold',
                textAlign: params?.textAlign || 'center',
                textShadow: params?.textShadow || 'none',
                whiteSpace: 'pre-wrap',
                width: '90%',
              }}>
                {displayValue || <span style={{ opacity: 0.5 }}>(无文本)</span>}
              </div>
            );
          }

          // 如果是背景或素材图层，仅做一个标记
          if (preset?.startsWith('media.') && preset !== 'media.audio') {
             return (
                 <div key={idx} className="absolute top-2 left-2 bg-white/80 px-2 py-0.5 rounded text-[10px] text-slate-800 shadow-sm backdrop-blur-sm font-medium">
                     {preset === 'media.video' ? '视频素材' : '图像素材'}
                 </div>
             )
          }

          return null;
        })}

        {/* 顶部信息栏已移除 */}
      </div>
      
      {/* 迷你时间轴控件 */}
      <div className="absolute bottom-4 left-6 right-6 bg-slate-900/80 backdrop-blur-md px-4 py-2 rounded-xl flex items-center gap-3 shadow-lg opacity-0 group-hover:opacity-100 transition-opacity duration-300 border border-slate-700">
        <button 
           onClick={() => setIsPlaying(!isPlaying)}
           className="w-8 h-8 rounded-full bg-blue-500 hover:bg-blue-400 text-white flex items-center justify-center shrink-0 shadow-md transition-colors"
           title={isPlaying ? "暂停" : "播放"}
        >
           {isPlaying ? (
             <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
           ) : (
             <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
           )}
        </button>
        
        <input 
          type="range" 
          min="0" 
          max={totalFrames} 
          value={currentFrame} 
          onChange={(e) => {
            setIsPlaying(false);
            setCurrentFrame(parseInt(e.target.value));
          }}
          className="flex-1 h-1.5 bg-slate-700 rounded-full appearance-none cursor-pointer accent-blue-500"
        />
        
        <div className="text-white text-[10px] font-mono shrink-0 w-[80px] text-right">
          {currentFrame} / {totalFrames} 帧
        </div>
      </div>
    </div>
  );
};
