import { useState, useRef, useEffect } from 'react';
import { Link, Scissors, ArrowRight, Settings2 } from 'lucide-react';

interface TransitionNodeProps {
  sceneIdx: number;
  transition: any;
  updateTransition: (fromIdx: number, toIdx: number, preset: string) => void;
  updateTransitionParam: (fromIdx: number, toIdx: number, key: string, value: any) => void;
}

export function TransitionNode({
  sceneIdx,
  transition,
  updateTransition,
  updateTransitionParam
}: TransitionNodeProps) {
  const [isOpen, setIsOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  const currentPreset = transition?.preset || 'none';

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="relative flex items-center justify-center px-2" ref={popoverRef}>
      {/* 触发按钮 */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`w-10 h-10 rounded-full flex items-center justify-center border transition-all shadow-sm z-10 ${
          currentPreset !== 'none'
            ? 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
            : 'bg-white border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-slate-600'
        }`}
        title="设置转场"
      >
        {currentPreset === 'none' ? <Scissors size={18} /> : <Link size={18} />}
      </button>

      {/* 悬浮设置面板 */}
      {isOpen && (
        <div className="absolute top-12 left-1/2 -translate-x-1/2 w-64 bg-white rounded-xl shadow-lg border border-slate-200 p-4 z-50 animate-in fade-in zoom-in-95 duration-200">
          <div className="flex items-center gap-2 mb-3 text-slate-700 font-medium pb-2 border-b border-slate-100">
            <Settings2 size={16} className="text-slate-400" />
            转场设置 (场景 {sceneIdx + 1} <ArrowRight size={14} className="inline" /> 场景 {sceneIdx + 2})
          </div>

          <div className="space-y-4 text-sm">
            {/* 转场类型 */}
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">转场效果</label>
              <select
                value={currentPreset}
                onChange={e => updateTransition(sceneIdx, sceneIdx + 1, e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 text-slate-700 rounded-md py-1.5 px-2 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              >
                <option value="none">无 (硬切)</option>
                <option value="transition.fade">淡入淡出</option>
                <option value="transition.slide">滑动推入</option>
                <option value="transition.wipe">擦除</option>
              </select>
            </div>

            {/* 参数配置 */}
            {transition && (
              <div className="bg-slate-50 p-3 rounded-lg border border-slate-100 space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <label className="text-xs font-medium text-slate-500 shrink-0">持续帧数</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={transition.params?.durationInFrames || 15}
                      min={5}
                      max={60}
                      onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'durationInFrames', parseInt(e.target.value))}
                      className="w-16 bg-white border border-slate-200 text-center text-slate-700 rounded py-1 px-2 focus:outline-none focus:border-blue-500"
                    />
                    <span className="text-xs text-slate-400">帧</span>
                  </div>
                </div>

                <div className="flex items-center justify-between gap-3">
                  <label className="text-xs font-medium text-slate-500 shrink-0">缓动曲线</label>
                  <select
                    value={transition.params?.timing || 'linear'}
                    onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'timing', e.target.value)}
                    className="flex-1 bg-white border border-slate-200 text-slate-700 rounded py-1 px-2 focus:outline-none focus:border-blue-500"
                  >
                    <option value="linear">线性 (Linear)</option>
                    <option value="spring">弹簧 (Spring)</option>
                  </select>
                </div>

                {currentPreset === 'transition.slide' && (
                  <div className="flex items-center justify-between gap-3">
                    <label className="text-xs font-medium text-slate-500 shrink-0">推入方向</label>
                    <select
                      value={transition.params?.direction || 'from-left'}
                      onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'direction', e.target.value)}
                      className="flex-1 bg-white border border-slate-200 text-slate-700 rounded py-1 px-2 focus:outline-none focus:border-blue-500"
                    >
                      <option value="from-left">← 从左侧</option>
                      <option value="from-right">→ 从右侧</option>
                      <option value="from-top">↑ 从上方</option>
                      <option value="from-bottom">↓ 从下方</option>
                    </select>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
