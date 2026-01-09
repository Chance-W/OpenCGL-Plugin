# 深色模式下 ListView 新增行视觉不明显 - 分析

## 原因

在 `GlobalComponents.css` 中：

1. **`.list-view`** 容器使用 `-fx-background-color: -theme-bg-primary`（深色下约 `#1e1e1e`）。
2. **`.list-cell`** 统一使用 `-fx-background-color: transparent`（约 1091–1097 行）。
3. 因此每一行（list-cell）都是**透明**的，看到的都是背后的 list-view 背景，颜色一致。
4. 深色模式下，新增一行 = 多出一块透明区域，与周围背景相同，**视觉上难以分辨**“多了一行”。

选中/悬停有单独规则（`-theme-selected-bg` / `-theme-hover-bg`），但**普通填充行（:filled）没有与容器区分的背景**，导致“添加新行”不明显。

## 修改思路（全局）

- 仅针对 **ListView**（不含 ComboBox 下拉、MFX 等）的 **:filled** 单元格：
  - **默认状态**：给一个与容器有对比的背景，例如 `-theme-bg-secondary`（深色下约 `#252526`），使每一行与 list 背景区分开。
  - **悬停/选中**：用相同选择器权重显式写 `:hover` / `:selected` / `:selected:hover`，继续用 `-theme-hover-bg`、`-theme-selected-bg`（或现有 accent），保证交互状态不变。
- 这样**不改变** ComboBox 内 list-cell、TreeView、TableView 等已有表现，只增强 ListView 行的可辨识度。

## 修改位置

在 `GlobalComponents.css` 的「ListView 专用样式」区块中，为  
`.list-view:not(.mfx-list-view) .list-cell:filled` 增加默认背景，并补全 hover/selected 覆盖。
