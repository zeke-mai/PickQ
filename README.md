# PickQ

Android 8.0+ 本地刷题应用。 [下载 APK](https://github.com/zeke-mai/PickQ/releases/latest)

## 功能

- 导入 `.xls` / `.xlsx`，支持其他应用“打开”或“分享”给 PickQ。
- 导入前预览格式问题与重复题，显示工作表、行号和详情；**只检查当前文档内重复，不与已有题库比较**。
- 顺序练习、模拟考试、错题集；支持编辑题目、选项乱序和答对自动跳转。
- 题库改名、文件夹管理及删除；删除文件夹会保留题库并移回未分组。
- 应用内在线更新或选择本地 APK 更新；云端题库暂为预留入口。

## 题库格式

支持题干、题型、选项 A–H、正确答案、解析、章节和难度，非必要列可省略；兼容“试题内容 / 试题类型 / 正确选项”等表头及旧 XLS 材料题。

支持单选、多选、不定项、判断、填空、简答、排序、计算和论述。格式错误题不导入，文档内完全重复题默认跳过，可选择保留；缺少标准答案的题使用人工判分。图片题暂不支持。

## 构建与发布

需要 JDK 17、Android SDK 34，SDK 路径配置在 `local.properties`。

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

提交主分支或 PR 自动测试构建；推送 `v*` 标签自动签名发布。仓库 Actions Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。后续版本必须沿用同一签名。更新仓库可由 `UPDATE_REPO=账号/仓库` 指定，也可在应用总设置修改。

## 数据说明

题库和练习记录保存在本机，不上传。卸载会清除数据，暂无导出备份；未交卷考试不跨进程保存。第三方依赖见 [THIRD_PARTY.md](THIRD_PARTY.md)。
