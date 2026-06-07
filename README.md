# Moe

Moe 是一个 Android 原生影视库应用，使用 Jetpack Compose + Material Design 3 / Material You 实现。

## 功能

- 光鸭网盘短信登录、token 刷新、全目录视频导入、播放直链解析
- 本地影视库、元数据、播放进度 SQLite 保存
- Javinizer 风格的多源刮削与字段优先级聚合
- Android Media3 播放器和本地播放进度记忆
- GitHub Actions 自动构建已签名 release APK

## 技术实现

- UI: Jetpack Compose Material3，动态色，底部导航，48dp 以上触控目标
- 网络: OkHttp
- 刮削: Jsoup，当前实现 JavDB 和 JavBus Android 原生 scraper
- 播放: AndroidX Media3 ExoPlayer
- 本地库: SQLiteOpenHelper
- 构建: Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21

## 参考实现

- 光鸭网盘接口流程参考 https://github.com/DDSRem-Dev/guangyaclient
- 刮削架构和聚合思路参考 https://github.com/javinizer/javinizer-go

## CI 构建

推送到 `main` 后，`.github/workflows/android.yml` 会在 GitHub Actions 中安装 JDK、Android SDK、Gradle，然后执行：

```bash
gradle --no-daemon :app:assembleRelease
```

产物会上传为 `Moe-release-apk` artifact。

## 固定签名

Release 构建使用仓库内固定测试签名：

- 文件: `app/signing/moe-release.p12`
- alias: `moe`
- store/key password: `moe-fixed-signing`
- SHA-256: `914D6FBBA3A869D46DB27D41B11A01BD837D16EBD251437154E1CE80ABA9D453`

这把钥匙用于测试版本保持签名一致，不应作为生产发布密钥。

## 本地构建

当前开发环境没有 Gradle/JDK/Android SDK，本次未在本地执行构建。请以 GitHub Actions 构建结果为准。
