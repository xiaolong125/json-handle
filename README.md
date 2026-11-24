# JSON Handle for Android Studio

在 Android Studio 中查看和编辑 JSON。插件在本机处理文本，工作区随工具窗口标签页或 IDE 会话关闭而清除。

## 安装

支持 Android Studio 2026.1.x（IDE 构建号 261）。在 **Settings → Plugins → ⚙ → Install Plugin from Disk…** 选择 `build/distributions/json-handle-0.1.3.zip`，按 IDE 提示重启。

## 使用

- 打开右侧 **JSON Handle** 工具窗口，在 **JSON** 标签页粘贴或输入文本。树会随文本更新，节点按 JSON 值的类型着色，解析失败时会显示错误。
- 展开对象或数组，选中节点后点击 **Edit Value…**。输入的替换值遵循 JSON 语法，例如字符串需要双引号。对象键名可以直接在源码区域修改。
- **Format** 使用两个空格缩进，**Minify** 移除多余空白。两者都保留键的顺序、重复键和数字精度。
- 在编辑器内右键点击 **Open in JSON Handle**，可将选中文本或当前 `.json` 文件复制到独立的工具窗口标签页。需要写回时，从工具窗口复制编辑结果。
- 在 **Encode / Decode** 标签页可转换 Unicode 转义、UTF-8 Base64 和 URL 组件。

每个 JSON 文档最多 524,288 个 UTF-16 代码单元，最多嵌套 128 层。

## 构建

使用 JDK 21、Gradle 9.6.1 和本机 Android Studio 2026.1.x。macOS 默认从 `/Applications/Android Studio.app/Contents` 读取 IDE SDK，可通过 `ANDROID_STUDIO_HOME` 或 `-PstudioHome` 指定包含 `lib` 的 IDE 安装目录。

```sh
gradle --offline buildPlugin
```

安装包输出到 `build/distributions/json-handle-0.1.3.zip`。
