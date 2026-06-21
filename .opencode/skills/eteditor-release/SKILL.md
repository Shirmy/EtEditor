---
name: eteditor-release
description: 用户说"发布"时使用。总结未推送commit、定版本号、改CHANGELOG、编译导出APK、推送、创建GitHub Release
---

# EtEditor 发布流程

## 整理更新记录（用户说"发布"时）

1. 按类别（通用/EPUB/TXT）自动总结未推送的 commit 给用户看
2. 用户说"同意"后，再按下面流程走

## 版本号规则

- 修 bug → 动第三位：`3.9.0 → 3.9.1`
- 加功能 → 动第二位：`3.9.x → 4.0.0`
- 大改 → 动第一位：`3.x → 4.0.0`

## CHANGELOG 格式

```
## x.x.x

EPUB
- 新功能/重构（放开头）
- 优化xxx
- 优化yyy
- 修复zzz
```

- 重构/新功能放开头 → 优化类 → 修复类，不空行

## 执行步骤（用户同意后）

1. 按规则定版本号
2. 改 `app/build.gradle.kts`（versionName + versionCode）
3. 写 `CHANGELOG.md`
4. 编译导出 APK（`./gradlew exportReleaseApk`）
5. APK 复制到 `C:\Users\lxy\Nutstore\2\杂七杂八\Coding相关\EPUB编辑器` + 删 release-apk 原包
6. commit 版本/CHANGELOG 改动 + `git push origin master`
7. `gh release create` 创建 GitHub Release + 上传 APK

## 其他

- 用户代码由用户自己 commit，我只 commit 版本/CHANGELOG 改动
- git 已配置代理 `http://127.0.0.1:7890`
