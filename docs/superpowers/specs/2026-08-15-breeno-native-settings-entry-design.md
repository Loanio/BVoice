# Breeno 原生设置入口设计

## 目标

将 BreenoTTSHook 的入口从右上角悬浮按钮改为小布“设置”页“个性化设置”分组中的一条原生偏好项。

## 用户体验

入口插在“小布音色”之后，标题为“第三方音色”。副标题显示当前选择音色；未配置时显示“点击配置”。点击后打开现有完整配置对话框。入口使用宿主 `Preference`/`RecyclerView` 的渲染链，以获得原生间距、字体、箭头和暗色主题，不手工仿制布局。

## 实现边界

- 仅在小布设置 Activity 的 PreferenceScreen 中插入一项。
- 入口键固定为 `dev.breenottshook.preference.third_party_voice`，避免重复添加。
- 通过反射创建宿主已包含的 `androidx.preference.Preference`；若 PreferenceScreen 或锚点不存在，不显示入口且不影响其它 Hook。
- 移除 decor 悬浮按钮路径。

## 验证

- 单测验证入口描述符的键、标题、默认副标题和排序规则。
- 构建 debug APK 并安装到 Android 15 设备。
- 使用 UI dump 验证“个性化设置”中“小布音色”之后出现“第三方音色”，且无悬浮按钮。
