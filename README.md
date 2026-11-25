# 极简电视桌面（Quiet TV Launcher）

原名「杂志桌面」。这是一个用 Java 写成的 Android 电视启动器：全屏时钟和一行新闻，遥控器方向键移动焦点，同一局域网里的手机可以扫码换壁纸、看照片、回到桌面或切到 HDMI。

公开仓库仍使用包名 `com.lin.magazinehome`，以免和已经装在电视上的应用错成两个包。桌面上的名字是「极简电视桌面」。

## 请先读

- 移交时的 `AndroidManifest.xml` 写着 `versionName="5.3.1"`、`versionCode="11"`。这只说明那份源码清单里有这两个数字。家里那台 TCL 55T5M 上已安装的包，先前记录也是 5.3.1 / code 11、包名 `com.lin.magazinehome`。**源码和那份 APK 是否逐字节对应，没有核验。** 本仓库不表示已经复现已安装的那一版。
- 公开树把 `versionName` 改成 `5.3.1-public.1`，`versionCode` 仍是 11。多出来的后缀用来标明：这是整理过的公开树，不是那份已安装包。
- 调试 APK 用构建时临时生成的调试密钥签名。密钥在 `build/` 里，不进 git。Android 不允许用另一把签名覆盖已经安装的同包名应用。**这个调试包不能替换电视上的旧包，也不是正式发布。**
- 这次整理没有连接电视，没有安装，没有做真机或模拟器验收。
- **还没有选定开源许可证。** 仓库设为公开，只表示文件可以被看到，不表示授予使用、修改、再分发或贡献的许可。这些由仓库所有者决定。这里不代选 MIT、Apache 或其他许可证。`app/src/io/nayuki/qrcodegen/` 里的文件自带 MIT 许可头，只覆盖这组二维码源码。

## 代码里实际有的

下面只写当前源码能对上的行为。

- **启动器。** `MainActivity` 响应 `HOME`。首页是全屏画面：时钟、日期、节气、可选农历、天气、一条新闻，底部一排可聚焦的入口。
- **遥控器焦点。** 首页磁贴和设置页用方向键移动焦点。设置里右键下一步、左键上一步。没有使用 Leanback 控件库。`leanback` 和触摸屏都声明为非必需。
- **应用。** 抽屉列出本机启动器和 Leanback 启动器里的应用。首页最多固定 8 个。菜单键或长按可以移动、移除。未自定义时，会尝试固定一批常见影视包名（装了才出现）和 `com.tcl.settings`。
- **壁纸来源。** 必应每日、内置渐变、纯色、自定义文件夹、自定义图片链接、手机上传、暗色渐变。渐变在内存里绘制，见下文素材。必应和自定义链接会访问网络。Ken Burns 式缓慢推镜可以开关。
- **新闻。** 可选中新网 RSS、新浪滚动接口、IT之家 RSS。失败时按源码里的顺序换源。结果缓存在应用私有偏好里。
- **天气。** Open-Meteo，不需要密钥。城市可以选内置列表，也可以在手机页搜索（电视代为请求 Open-Meteo 地理编码）。「自动定位」会请求 `ip-api.com`，失败再试 `ipapi.co`。
- **农历和节气。** 农历用系统 ICU（API 24 起有）。节气用一段近似的太阳黄经，跨过 15° 的当天显示名称。这不是天文年历。
- **局域网配套页。** 开机广播和进入首页都会启动一个很小的自写 HTTP 服务，端口依次试 8686、8687、8688。电视上的「扫码」用二维码打开 `http://<电视IPv4>:<端口>/?k=<配对码>`。页面可以：上传并裁切照片、设为壁纸、只存相册、在电视上看图、删除、换下一张、回桌面、切到 HDMI、打开已经安装的投屏接收应用、把 http/https 链接交给系统、定时待机、改壁纸来源和间隔、屏保样式和亮度、天气城市、展签。
- **屏保。** 四种黑白样式：数字、指针、翻页、文字。空闲一段时间后打开，也可作为系统 Dream（`SaverDream`）。任意键退出。
- **HDMI 相关代码。** 数字键 1–3、首页入口、双击主页键、手机页，都会调用 `Hdmi.switchTo`。实现是：查 `TvInputManager` 的 HDMI 输入，拼一个直通 URI，再显式打开 `com.tcl.tv` / `com.tcl.tv.TVActivity`，并带上 `targetSourceId = 7 + 端口号`；失败则再发一条 `com.tcl.receiver.KUKONG_SWITCH_SOURCE` 广播。找不到输入 id 时，回退到 droidlogic 常见的服务名。信号监听把 `INPUT_STATE_CONNECTED` 当作有信号。源码注释写过某台 TCL 上的编号。**这次没有在 TCL 55T5M 上复核。**
- **投屏。** 不实现 DLNA、AirPlay 或 Miracast。只是按顺序打开本机已安装的接收应用：TCL 多屏互动、乐播、EShare、AirPin。没有这些应用时，按钮不可用。
- **定时待机。** `AlarmManager` 到点后，先试无障碍的锁屏全局动作，再在 `adb_boost` 已打开时试本机按键，否则进入自己的黑屏时钟。

### 代码里有、当前手机页没有画出来的

- `Remote` 还接受指针移动、点击、长按、滚动、方向键和输入文字。`RemoteService` 能用无障碍接口做这些事。`assets/web/index.html` **没有**方向键或触控板，不会主动发这些指令。拿到配对链接的其他客户端仍然可以发。
- `adb_boost` 为真时，`LocalAdb` 只连接 `127.0.0.1:5555`，用 Android 调试协议执行 `input keyevent` / `input tap` / `input swipe`。配对密钥在应用私有目录里现生成，不在仓库中。这个开关**默认关闭**。当前设置页和手机页都没有把它打开的控件。本说明不写如何打开网络调试，也不写如何跳过系统的授权确认。

## 这次没有验证的

没有改电视，没有安装这个 APK，没有跑模拟器。

因此下面这些只能当作源码里的路径，不能当作这台 TCL 55T5M 上已经发生过的事：

- HDMI 无信号时切到黑屏时钟，以及信号回来后再切走
- 开机后按偏好进入 HDMI
- 休眠、亮屏之后回到 HDMI 或桌面
- 用屏幕常亮锁避开系统屏保时是否还会闪黑
- 32 位设备上的内存占用、流畅度、耗电。解码注释里提到过往 1920×1080 缩小，公开树的渐变则画成 1280×720。没有测量数字

兼容下限以清单为准：`minSdkVersion="24"`（Android 7.0），`targetSdkVersion="30"`。更高版本系统上的后台启动、存储权限和明文流量策略没有测过。部分调用自己判断了 API（例如 API 30 的输入法回车）。

## 技术架构

| 部分 | 实现 |
|---|---|
| 语言 | Java 8 语言级别，源码在 `app/src` |
| 界面 | 几乎全部用代码建视图，没有 layout XML |
| 构建 | 不用 Gradle。`aapt2` + `javac` + `d8` + `zipalign` + `apksigner` |
| 包名 | `com.lin.magazinehome`（故意不改） |
| 版本 | `5.3.1-public.1`（code 11） |
| 进程内消息 | `Bus`，不发系统广播 |
| 手机服务 | `PhoneServer`，一个 accept 线程，短连接各自处理；WebSocket 只传文本 |
| 二维码 | Project Nayuki 的 QR Code generator，源码在树内 |
| 偏好 | `SharedPreferences` 文件名 `mag`，仅本应用可读 |
| 模型 | 电视上不运行生成式模型。AI 用于自然语言辅助开发和这次公开整理 |

## 目录

```
app/AndroidManifest.xml          组件、权限、版本
app/src/com/lin/magazinehome/    启动器、壁纸、新闻、天气、HDMI、局域网页、屏保
app/src/io/nayuki/qrcodegen/     第三方二维码库（文件内 MIT）
app/assets/web/index.html        手机页
app/assets/wall/CREDITS.txt      说明未包含的照片，不是图片授权
app/assets/dark/LIST.txt         说明未包含的暗色图片
app/res/                        字符串、主题、无障碍声明、两张新画的图标
host-test/HostCheck.java         不依赖设备的逻辑检查
tools/                           检查、扫描、图标生成
build.sh                         参数化调试构建
.github/workflows/debug.yml      拉取公开 SDK 后构建调试 APK
```

## 权限、网络、照片放在哪

清单里的权限，以及源码里的用途：

- `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`：新闻、天气、必应、自定义图片、显示局域网地址
- `RECEIVE_BOOT_COMPLETED`：开机启动局域网服务，并按代码尝试开机切 HDMI
- `READ_EXTERNAL_STORAGE`：壁纸来源选「自定义文件夹」时读取图片目录
- `KILL_BACKGROUND_PROCESSES`：设置里的「内存」会对非常驻、非本应用调用 `killBackgroundProcesses`。这是系统提供的后台清理接口，不是 root
- `WAKE_LOCK`：不在自己的屏保页上时持有屏幕常亮锁。源码意图是减少系统屏保插进来。没有功耗数据

明文流量是打开的（`usesCleartextTraffic`）。局域网页是 HTTP；自动定位的第一个接口也是 HTTP。

会主动访问的网络，在对应功能开着或被用到时：

- `https://cn.bing.com` 必应壁纸
- `https://www.chinanews.com.cn` 、`https://feed.mix.sina.com.cn` 、`https://www.ithome.com` 新闻
- `https://api.open-meteo.com` 天气，`https://geocoding-api.open-meteo.com` 城市搜索
- `http://ip-api.com` 、`https://ipapi.co` 仅在天气城市为「自动定位」且缓存过期时
- 用户填写的自定义图片链接，且只允许 `http` 或 `https`

**不能说这个应用绝不联网。**

照片和缓存：

- 手机上传在应用私有目录 `files/phone/`，最多保留 40 张，旁边有 `.cap` 说明文字，缩略图在 `files/thumbs/`
- 必应缓存在 `files/bing/`，自定义链接缓存在 `files/custom_url.jpg`
- 文件夹来源还会看应用外部目录 `wallpapers/`，以及存储上的 `Pictures/Wallpapers`、`Download/Wallpapers`、`Wallpapers`
- 手机页用画布重新导出 JPEG 再上传。页面会读 EXIF 日期，放进说明文字；其中的 GPS 解析没有把地点写入 `exif.loc`
- 服务端保存后会尝试删掉 GPS 相关 EXIF。这是尽力而为：框架接口失败时文件仍会留下。不要把它理解成「保证没有位置信息」
- 看图页只打开上述 `phone` 目录里的文件

## 局域网服务要注意什么

服务监听所有网卡，这是为了让同一 Wi-Fi 下的手机打开。源码另外做了这些限制：

- TCP 对端不是回环、站点本地或链路本地地址时，直接关掉，不读请求
- `Host` 必须是带正确端口的 IPv4 局域网字面量，或 `localhost`。用来拒绝「域名指到电视 IP」这种 Host。解析不查 DNS
- 浏览器若带了 `Origin`，必须是同一个 `http://主机:端口`。`Origin: null` 拒绝。没有 `Origin` 的非浏览器客户端，只要配对码对，仍然可以访问
- 若带了 `Sec-Fetch-Site`，只接受 `same-origin` 或 `none`
- 配对码是 8 位，字母表为公开的 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`（去掉易混的 0/O、1/I）。用 `SecureRandom` 抽取。它不是外部服务的密钥。设置里「重置手机配对码」会使旧二维码失效
- 链接本身就是权限。同一局域网中拿到链接的人，可以做手机页上的事，也可以调用页面没画出来、但 `Remote.exec` 仍接受的指令
- 未通过配对的请求不会先读最大 16MB 的正文；回答 403 后关闭连接，并等待约 300 毫秒
- 上传上限 16MB，先按图片解码，宽高都要至少 64。删除和缩略图的文件名拒绝 `/` 和 `..`
- 手机页要求打开的链接、以及电视代为下载的地址，都限制为 http/https

这不是「完全安全」，也挡不住已经拿到配对链接、并且就在这个局域网里的人。不要把二维码截到公开场合，不要把端口映射到公网。重置配对码是源码里提供的正常失效方式。

无障碍服务声明为可以查看窗口内容并做手势。源码用它寻找焦点、点击和输入；审查到的状态接口没有把窗口文字发给手机页。服务导出，但绑定需要系统的无障碍绑定权限。当前手机页不依赖它也能用壁纸和切换功能。若要使用代码里的光标路径，应在系统无障碍设置里由使用者自己打开。

## 构建

调试 APK 在 **JDK 17**、平台 `android-30`、构建工具 `34.0.0` 上实际编过。同一套构建工具配 JDK 21 时，`d8` 在读取二维码库的 `QrSegment$Mode` 时发生内部空指针，没有产出 APK。主机检查不跑 `d8`，JDK 21 可以跑那些检查。SDK 需要：

- 平台 `platforms;android-30`
- 构建工具 `build-tools;34.0.0`

公开来源是 Android 命令行工具，例如 `commandlinetools-linux-11076708_latest.zip`，再用 `sdkmanager` 安装上面两项。SDK 和 JDK 都不要放进仓库。

```bash
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/sdk
bash tools/host-check.sh
python3 tools/scan-tree.py
bash build.sh
```

产物是 `build/quiet-tv-launcher-debug.apk`。未设置 `KEYSTORE` 时，脚本会在 `build/` 里临时生成调试密钥库，密码写在 `build/debug-keystore-pass.txt`，两者都被 git 忽略。换一把正式密钥时只通过环境变量：

```bash
export KEYSTORE=/path/to/your.keystore
export KEY_ALIAS=your-alias
export KEYSTORE_PASSWORD=...
export KEY_PASSWORD=...   # 可省略，此时与 KEYSTORE_PASSWORD 相同
bash build.sh
```

不要把正式密钥、密码或签过名的发布包提交进来。调试签名每次清掉 `build/` 都会变。GitHub Actions 工作流 `.github/workflows/debug.yml` 会做同样的扫描、主机检查和调试构建，并把 APK 留在该次运行的构件里。那仍然是调试签名，不是发布。

`host-test` 检查配对码形状、局域网地址判断、文本清理、展签字样、节气公式的边界。它不启动模拟器，也不能代替真机。

## 安装，以及恢复原来的桌面

这是系统自己的安装方式，不是绕过安装限制的做法。

1. 调试包的签名和已经装在电视上的「杂志桌面」不同。系统会拒绝覆盖安装。若要试这个调试包，需要先换掉默认桌面，再卸载旧应用，然后安装。
2. 换默认桌面：打开系统设置里的应用或主屏幕应用，把主屏幕选回系统自带桌面或其他启动器。菜单名字因系统而异，常见位置是「设置 → 应用 → 当前桌面应用」。卸载前先换掉，避免卸完暂时没有主屏幕。
3. 卸载旧应用后，用文件管理或 `adb install` 安装调试 APK。系统询问主屏幕应用时，可选「极简电视桌面」，也可以继续用系统桌面。
4. 想恢复：再把主屏幕选回系统桌面，然后卸载本应用。应用私有目录里的上传照片和缓存会随卸载删除。

不提供关闭系统安装校验、强行启用无障碍、或未经确认打开网络调试的步骤。

## 素材与第三方

移交时排除了 12 个二进制文件，本仓库没有把它们找回来：

| 未包含 | 公开树里怎么处理 |
|---|---|
| `assets/dark/d0.jpg` `d1.jpg` `d2.jpg` | 「暗色」改为内存渐变 |
| `assets/fonts/NotoSerifSC-Regular.otf` `NotoSerifSC-Bold.otf` | 使用系统 `serif` / `sans-serif`。中文是否像衬线，取决于电视自己的字体回退。不因为文件名像 Noto 就认定可以再分发 |
| `assets/wall/w0.jpg` … `w4.jpg` | 「内置精选」改为内存渐变。旧索引里的 Wikimedia 署名没有复核，照片不重新下载 |
| `res/drawable-xhdpi/banner.png` | 用 `tools/make_brand_pngs.py` 新画的 320×180 横幅 |
| `res/mipmap-xhdpi/ic_launcher.png` | 同一脚本新画的 192×192 图标 |

**没有声称仓库里的全部图片都已获授权。** 新增的只有这两张程序绘制的标记，以及运行时的纯色渐变。没有放入来源不明的壁纸、截图或照片。

二维码库版权和 MIT 文本在对应源文件头部，作者为 Project Nayuki。新闻、天气、必应页面和投屏应用是运行时访问的外部服务或本机应用，它们的内容不在本仓库里。

## 和移交快照的差别

业务方向没有改：仍然是这个启动器，不是新应用。为了能公开构建、并且不把占位符当成密钥，做了这些必要改动：

- 配对码改为独立的公开字母表 + `SecureRandom`。没有找回被替换掉的原字符串
- 局域网服务增加对端、Host、Origin、`Sec-Fetch-Site` 检查；未认证请求不再预读大正文
- 出站 URL 和「在电视上打开链接」限制为 http/https
- 上传后尝试去掉 GPS EXIF；看图路径限制在私有相册目录
- 去掉对未移交字体和照片文件的依赖
- 补上两张自绘图标，否则资源链接无法通过
- 版本名加上 `-public.1`；显示名改为「极简电视桌面」
- 构建脚本不再指向某台机器上的 SDK 目录，也不含签名密码。正式签名只从环境变量读取
- 增加忽略规则、主机检查、树扫描和 GitHub Actions

没有加新的桌面功能，没有加生成式功能，没有发布 GitHub Pages。

## 已知限制

- 许可证未指定，见上文
- 与已安装 5.3.1 APK 的一致性未核验；调试签名不能覆盖旧签名
- HDMI、无信号、休眠恢复未做真机验收
- 局域网能力取决于配对链接。链接泄露等于把页面上的控制权交给同一网络中的持有者
- `adb_boost` 的实现还在，但界面没有开关，默认关闭
- `targetSdk 30` 在更新的系统上可能遇到行为差异，未测试
- 主机检查和 CI 只证明源码能编过、纯逻辑断言成立。它们不是仪器测试，更不是这台电视上的验收

## 开发辅助

整理和实现说明使用了 AI 做自然语言辅助。应用运行时不下载、也不在电视上运行语言模型。
