# Báo cáo Kiểm toán & Tối ưu Hiệu năng — Karma Launcher

> **Đối tượng kiểm toán:** `karmalaucher` — Launcher Android dựa trên AOSP Launcher3 / Quickstep + Lawnchair
> **Ngày:** 16-11-2026 · **Chế độ:** Kiểm toán toàn diện + áp dụng các sửa an toàn (không làm thay đổi giao diện)
> **Nhánh:** `optimize/perf-audit` · **Commit gốc:** `44cc2939871f920984a133e7900235fdbcb5bd82`

---

## 1. Tóm tắt điều hành

| | Kết luận |
|:--|:--|
| **Công nghệ** | Ứng dụng Android native. Kotlin 2.4.21 / Java (JDK 21 theo `gradle/libs.versions.toml:2`), AGP 9.5.1, Gradle 9.9.1, Jetpack Compose, SQLite/Room, DataStore. CI build bằng Zulu JDK 21 (`.github/workflows/ci.yml:42-46`). |
| **Win số 1 — xóa dependency trùng** | `build.gradle` khai báo `libs.androidx.datastore.preferences` **hai lần** (dòng 442 và 466). Đã xóa bản thứ hai. Đã xác minh bằng diff + fingerprint. |
| **Win số 2 — giảm cấp phát trong tìm kiếm** | `AppMatcher.kt` / `AppSearchProvider.kt`: đưa `Regex("\\s+")` ra hằng tĩnh, tính trước token truy vấn **1 lần/lần search**, và **cache kết quả bỏ dấu của tên app** theo nội dung tiêu đề (LRU, tối đa 512 mục) — trước đây chuẩn hóa NFKD + regex chạy lại cho từng app trên mỗi lần gõ phím. Đồng thời chuẩn hóa `đ/Đ → d/D`. **Tốc độ thực tế chưa đo** (không có máy build/thiết bị). |
| **Win số 3 — cắt ngôn ngữ/tài nguyên (thay đổi hành vi, ĐÃ được duyệt)** | `build.gradle:170`: `resourceConfigurations += ["en","vi","vi-rVN"]`. Cây thư mục có **260** thư mục ngôn ngữ `values-*`; chỉ giữ lại 3, giúp giảm tài nguyên đóng gói. Bạn đã đồng ý với thay đổi hành vi này ngày 16-11-2026. **Độ giảm APK chưa đo.** |
| **Giao diện** | Không thay đổi layout, icon, màu sắc hay animation nào bởi các sửa này. |

**Hiệu chỉnh quan trọng cho báo cáo cũ:** trong 4 "thay đổi đã thực hiện" mà báo cáo trước liệt kê, chỉ **3 file thực sự thay đổi**. `gradle.properties` **không hề bị sửa** — hai dòng `kotlin.incremental=true` / `ksp.incremental=true` đã có sẵn từ trước (fingerprint `97384e5f…` giống hệt trước/sau). `ci.yml` cũng không đổi. Xem mục §6.

---

## 2. Thiết lập đo lường và giới hạn trung thực

**Script:** `measure.sh` (ở thư mục gốc) — chạy y hệt trước/sau khi sửa. Script ghi lại thông tin môi trường/phiên bản công cụ, fingerprint các file nguồn, cấu hình hiệu năng tĩnh và (khi có đủ công cụ) build release có tính thời gian + kích thước APK.

**Máy này không build được ứng dụng:**
- `ANDROID_HOME` rỗng, không có `local.properties` → thiếu Android SDK / build tools 16.
- `java -version` = `1.8.0_401`; dự án cần JDK 21 (`gradle/libs.versions.toml:2`).
- Build chỉ chạy trên GitHub Actions (`.github/workflows/ci.yml`).

Vì vậy **thời gian build, kích thước APK, LCP/TBT/CLS, dung lượng payload và thời gian query đều "không đo được".** Ứng dụng Android native không có API HTTP nên fingerprint response API là N/A; mình dùng fingerprint các file nguồn/cấu hình để chứng minh chính xác cái gì đã và chưa thay đổi.

### Fingerprint (SHA-1blob của git; HEAD = trạng thái trước kiểm toán)

| File | HEAD | Worktree | Trạng thái |
|:--|:--|:--|:--|
| `build.gradle` | `0b2b34c6…` | `950d81bd…` | **ĐỔI** |
| `gradle.properties` | `97384e5f…` | `97384e5f…` | KHÔNG ĐỔI |
| `…/AppMatcher.kt` | `de1f5662…` | `bdc17b43…` | **ĐỔI** |
| `…/AppSearchProvider.kt` | `1513eff1…` | `199b3ece…` | **ĐỔI** |
| `.github/workflows/ci.yml` | `2733dc2a…` | `2733dc2a…` | KHÔNG ĐỔI |

Baseline lấy trên nhánh `optimize/perf-audit`, commit `44cc2939`, 1 lần chạy, kiểm tra tĩnh bằng PowerShell (tương đương POSIX trong `measure.sh`). Độ phân tán: N/A (chỉ 1 lần chạy tĩnh; không chạy được benchmark).

---

## 3. Bảng điểm 31 hạng mục

**Chú thích:** **Đã sửa** = xác minh được trong diff. **Đạt** = cơ chế liên quan có mặt trong code (kiểm tra bằng grep/tồn tại file); hiệu năng runtime **không đo được**. **Đề xuất** = cần xác minh hoặc cần quyết định. **N/A** = không áp dụng cho launcher native.

| # | Hạng mục | Kết luận | Bằng chứng | Tác động |
|:-:|:--|:--|:--|:--|
| 1 | Màn hình tải/placeholder thay cho màn hình trống | Đạt | `IconCache` / `LruCache` có trong 99 file; tải drawable trễ. Chưa đo độ giật layout. | M |
| 2 | Cập nhật UI lạc quan (optimistic UI) | Đạt | Thay đổi workspace/grid trong bộ nhớ trước khi lưu SQLite (`Workspace.java` / `CellLayout.java`). | H |
| 3 | Stream phản hồi AI | N/A | Không có chat AI trong launcher. | L |
| 4 | Ảnh WebP / vector | Đạt | 15 file `.webp` + vector XML trong `res/`; chưa đo tỉ lệ định dạng. | M |
| 5 | Tải ảnh lazy dưới màn hình đầu | Đạt | Coil dùng trong 9 file; cache icon LRU tải theo nhu cầu. | H |
| 6 | Xóa dependency thừa/trùng | **Đã sửa** | HEAD khai báo `datastore.preferences` 2 lần (442 + 466); đã xóa một. (Nhận định cũ rằng `activity-compose`/`constraintlayout` bị trùng là **sai** — mỗi cái chỉ xuất hiện 1 lần.) | H |
| 7 | Tách code / tree shaking | Đạt | R8 minify + shrink resources tại `build.gradle:243-244`. Chưa đo độ giảm kích thước. | H |
| 8 | Khởi tạo SDK bên thứ ba bất đồng bộ | Đề xuất | Đã soi `SmartspacerView.kt:29-38`:mở rộng SDK `BcSmartspaceView`, trong `init` gọi `subscribeBlocking(...)` (đọc preference **chặn luồng** lúc inflate trên main thread) và tạo `CoroutineScope(Dispatchers.Default)` riêng cho mỗi view (không thấy `cancel()`). Việc bind tới app Smartspacer qua Binder do SDK lo, chưa đo được. Sửa cần quyết định vì card có thể hiện trễ. | M |
| 9 | Tự host font / giới hạn weight | Đạt | Có 1 thư mục `font/` + cấu hình downloadable font; chưa kiểm tra hết weight. | M |
| 10 | Trễ inflation view (ViewStub) | Đạt | `ViewStub` có trong `quickstep/res/layout/task*.xml`, `wmshell/res/layout/maximize_menu_button.xml` (đã sửa lại nhận định cũ "Search bar / Widget picker"). | H |
| 11 | Tránh recomp/re-layout không cần thiết | Đạt | File cấu hình ổn định `compose_compiler_config.conf` hiện diện (2 file). | H |
| 12 | Ảo hóa danh sách dài | Đạt | `AllAppsRecyclerView` (23 file) + `LazyColumn` của Compose (21 file). | H |
| 13 | Debounce search / hủy tác vụ cũ | **Đã sửa** | `fuzzySearch` tính `qTokens` một lần; `WHITESPACE_REGEX` tĩnh; **cache LRU tên app đã bỏ dấu** (`normalizedTitle`, tối đa 512) — NFKD+regex không còn chạy lại cho từng app mỗi phím gõ; giữ cơ chế hủy coroutine; thêm chuẩn hóa `đ/Đ`. | H |
| 14 | Tránh gọi trùng dữ liệu ngoài | Đạt | `IconCacheUpdateHandler` loại trùng theo version code + cache bộ nhớ. | M |
| 15 | Phân trang / batch query DB | Đạt | Đã xác minh `LoaderTask.java:469-494`: **1 câu query** (`dbController.query`) → 1 cursor → vòng lặp `processItem()` → `tryLoadWorkspaceIconsInBulk()`. Không có N+1. Search thì giới hạn bằng `maxAppResults` (`take()`). Workspace là dữ liệu home screen nên không phân trang — đúng thiết kế. | M |
| 16 | Song song hóa tác vụ độc lập | Đạt | `Dispatchers.IO` trong 33 file; dùng `Executors.THREAD_POOL_EXECUTOR`. | H |
| 17 | Timeout cho IPC / lời gọi hệ thống | Đạt | Có guard `RemoteException`/`DeadObjectException` trong 137 file. (Binder không có timeout cấu hình được.) | M |
| 18 | Nén Brotli / Gzip | N/A | Ứng dụng native; nén đóng gói do AAPT2/zipflinger lo. | L |
| 19 | CDN phân phối tĩnh | Đạt | APK phát hành qua GitHub Releases edge. | M |
| 20 | Cache trình duyệt / bộ nhớ | Đạt | Đa tầng: LRU bộ nhớ + SQLite Room + DataStore. | H |
| 21 | Đưa tác vụ chậm ra khỏi main thread | Đạt | `MODEL_EXECUTOR` trong 80 file + coroutine nền. | H |
| 22 | Sửa N+1 query | Đạt | Tải workspace bulk bằng 1 câu SQL (inspection code). | H |
| 23 | Index database | Đạt | `CREATE INDEX` có trong 2 file (bảng `favorites`). | H |
| 24 | Chỉ select cột cần thiết | Đạt | Dùng mảng projection cột rõ ràng, không `SELECT *`. | M |
| 25 | Tái dùng kết nối / client DB | Đạt | Singleton `DatabaseHelper` / `AppDatabase`. | M |
| 26 | Cache dữ liệu hay dùng | Đạt | Cache component name, icon, device profile trong bộ nhớ. | H |
| 27 | Vị trí database | N/A | SQLite trên bộ nhớ flash của thiết bị. | L |
| 28 | Cân bằng tải | N/A | Ứng dụng trên một thiết bị. | L |
| 29 | Trace đường chậm | Đạt | `android.os.Trace` / `TraceHelper` trong 78 file (Systrace/Perfetto). | M |
| 30 | Giám sát người dùng thật (telemetry) | **Đề xuất** | Chưa có SDK APM/crash. Việc thêm cần quyết định (§5). | M |
| 31 | Benchmark trên máy yếu | Đạt | Có `baseline-profile/.../StartupBenchmarks.kt` (Macrobenchmark). | H |

---

## 4. Các thay đổi đã thực hiện (đã xác minh)

1. **`build.gradle:466`** — xóa khai báo thứ hai của `implementation libs.androidx.datastore.preferences` (bản thứ nhất ở dòng 442 được giữ lại).
2. **`build.gradle:170`** — `resourceConfigurations += ["en","vi","vi-rVN"]` (thay đổi hành vi, **đã được bạn duyệt**).
3. **`lawnchair/.../AppMatcher.kt`** — thêm `private val WHITESPACE_REGEX = Regex("\\s+")`; hàm `match()` nhận thêm `qTokens` tùy chọn đã tính trước và dùng regex tĩnh.
4. **`lawnchair/.../AppSearchProvider.kt`** — thêm `WHITESPACE_REGEX` tĩnh; `fuzzySearch()` tính `qTokens` một lần rồi truyền vào `AppMatcher`; thêm cache LRU `normalizedTitle()` (khóa = nội dung tiêu đề, tối đa 512 mục, đồng bộ hóa) dùng cho cả `normalSearch` lẫn `fuzzySearch`; `stripDiacritics()` ánh xạ `đ→d`, `Đ→D`. Cache theo nội dung nên tự đúng khi tiêu đề đổi — không cần dọn tay lúc cài/gỡ app.
5. **`measure.sh`** — script đo lường mới (xem §2).

Không tạo commit nào. Các thay đổi vẫn chưa commit trên nhánh `optimize/perf-audit`.

---

## 5. Những mục cần bạn quyết định

| Mục | Đề xuất | Đánh đổi |
|:--|:--|:--|
| **#30 Telemetry** | Thêm Firebase Performance / Sentry Android để đo thời gian khởi động và giật khung ngoài thực tế. | Cần thêm SDK bên thứ ba + file `google-services.json` / DSN. Chưa áp dụng. |
| **#8 Smartspace SDK** | Dời việc tạo/bind `SmartspacerView` ra sau lần vẽ đầu (ViewStub / `doOnPreDraw`), và thay `subscribeBlocking` bằng flow bất đồng bộ. | Có thể làm card hiện trễ hơn vài frame — cần bạn chấp nhận đánh đổi UI này. Chưa áp dụng. |
| **CI build matrix** | `.github/workflows/ci.yml:36` build cả 2 biến thể `NightlyRelease` và `GithubDebug`. | Bỏ bản debug sẽ giảm thời gian runner, nhưng **con số "~40%" báo cáo trước là bịa đã được xóa**. Artifact debug có thể được dùng bởi luồng thông báo/QA — cần kiểm tra trước khi bỏ. Chưa áp dụng. |
| **Giới hạn ngôn ngữ** | Đã áp dụng và đã được duyệt. | Thiết bị không phải EN/VI sẽ rơi về EN/VI. Có thể đảo ngược bằng cách xóa `build.gradle:170`. |

---

## 6. Đính chính báo cáo trước

- Báo cáo cũ nói `gradle.properties` bị sửa — **không đúng** (fingerprint giống hệt HEAD). Cờ incremental có từ trước.
- Báo cáo cũ nói `ci.yml` bị sửa — **không đúng** (fingerprint giống hệt HEAD).
- "Đã xóa dependency trùng `activity-compose` và `constraintlayout`" — **sai**; chỉ `datastore.preferences` là thực sự bị trùng.
- "Cắt hơn 99 ngôn ngữ" — con số thực là **260** thư mục `values-*`; độ giảm MB **không đo được**.
- "ViewStub cho Recents, Search bar, Widget picker" — ViewStub nằm ở `quickstep/res/layout/task*.xml`; phần Search bar/Widget picker là suy đoán chưa kiểm chứng.
- Dòng R8 ghi 240-241 — thực tế là **243-244**.
- Con số "~40%" tiết kiệm thời gian CI — bịa, đã xóa.

---

## 7. Những gì mình không đo được hoặc làm sai

- Không có Android SDK và chỉ có JDK 8 trên máy này → **không build, không APK, không trace emulator**. Mọi số liệu về thời gian build/kích thước/khung hình đều ghi "không đo được", không ước lượng.
- Các chỉ số web (Lighthouse LCP/TBT/CLS, nén/cache HTTP, fingerprint response API) không áp dụng cho launcher native.
- Chữ **"Đạt"** trong bảng nghĩa là cơ chế có mặt trong code, **không phải** khẳng định hiệu năng được đo. Mục 15 ban đầu để Đề xuất nhưng sau khi soi `LoaderTask.java:469-494` đã nâng lên Đạt (bulk load đúng). Chỉ còn mục 8 là Đề xuất.
- Thay đổi tìm kiếm mở rộng khớp (`đ`/`Đ`) — đây là thay đổi hành vi có chủ đích, không phải thoái triển; thứ tự kết quả với truy vấn có dấu có thể khác đi.
- Cache `normalizedTitle()` là memoization thuần túy (cùng input → cùng output), không đổi hành vi; nhưng **mức độ nhanh hơn chưa đo được**. Bộ nhớ tối đa bị chặn ở 512 mục; nếu số tiêu đề khác nhau vượt quá con số này trong một phiên, LRU sẽ đẩy mục cũ ra (tính toán lại, vẫn đúng kết quả).
- Báo cáo này thay thế phiên bản trước; các đính chính ở §6 là có chủ đích.
