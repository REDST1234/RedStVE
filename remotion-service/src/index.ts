import { registerAllPresets } from './presets/registerAll';

import { registerRoot } from "remotion";
import { RemotionRoot } from "./Root";

// 显式调用注册函数，防止 Tree Shaking 剔除预设
registerAllPresets();

registerRoot(RemotionRoot);
