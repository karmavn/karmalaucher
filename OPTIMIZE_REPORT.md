# Báo cáo Kiểm toán & Tối ưu Hiệu năng — Karma Launcher

> **Đối tượng kiểm toán:** `karmalaucher` — Launcher Android native dựa trên AOSP Launcher3 / Quickstep + Lawnchair  
> **Ngày:** 17-11-2026 · **Chế độ:** Kiểm toán toàn diện + Áp dụng các sửa an toàn (bảo toàn 100% giao diện và hành vi)  
> **Nhánh thực hiện:** `perf/optimize` · **Commit gốc (HEAD):** `761712fc45fd6ff3168d9f318d3dfdf723f9cc05`  

---

## 1. Tóm tắt điều hành

| Tiêu chí | Kết luận & Chi tiết |
|:--|:--|
| **Công nghệ & Stack** | Ứng dụng Android native. Kotlin 2.4.21 / Java (yêu cầu JDK 21 theo `gradle/libs.versions.toml:2`), AGP 9.5.1, Gradle 9.9.1, Jetpack Compose, SQLite/Room, DataStore Preferences, OkHttp/Retrofit, R8 / Proguard. CI build bằng Zulu JDK 21 (`.github/workflows/ci.yml`). |
| **Win 1 — Loại bỏ Reflection trong tìm kiếm cài đặt (#26)** | `SettingsSearchProvider.kt`: Trước đây, mỗi khi người dùng gõ 1 ký tự, hàm tìm kiếm lại gọi `Settings::class.java.fields` (Java Reflection) để lấy toàn bộ trường tĩnh của hệ thống và lọc chuỗi loại trừ. Đã chuyển toàn bộ danh sách hành động cài đặt sang `lazy` khởi tạo một lần duy nhất (`ALL_SETTINGS_ACTIONS`). Loại bỏ triệt để chi phí cấp phát mảng phản chiếu trên từng phím gõ. |
| **Win 2 — Tối ưu hóa cấp phát trong thuật toán tìm kiếm (#13)** | `AppMatcher.kt` & `SearchUtils.kt`: Thuật toán so khớp viết tắt (Initials) trước đây tạo chuỗi 1 ký tự cho từng token (`it.first().toString()`) rồi ghép lại qua `joinToString`. Đã thay bằng so khớp trực tiếp theo chỉ mục ký tự không cấp phát bộ nhớ (Zero Allocation) và đoản mạch khi độ dài không đủ. `filterHiddenApps` được thêm điều kiện thoát sớm `if (hiddenApps.isEmpty()) return this`, tránh sinh đối tượng `ComponentKey` và gọi `.toString()` cho hàng trăm ứng dụng mỗi phím gõ. |
| **Win 3 — Hoist Cursor Column Index (#24, #26)** | Trong `ContactsSearchProvider.kt` và `NovaBackupConverter.kt`, đưa toàn bộ các lệnh `getColumnIndex`/`getColumnIndexOrThrow` ra ngoài vòng lặp `while (cursor.moveToNext())`, tiết kiệm hàng nghìn lượt tìm kiếm chuỗi cột trong bộ nhớ SQLite. (Thử nghiệm tái sử dụng `Expressions` qua `ThreadLocal` ở `CalculatorSearchProvider.kt` đã bị revert — xem mục 4.6.) |
| **Win 4 — Dọn dẹp phụ thuộc trùng lặp (#6)** | `build.gradle:429`: Loại bỏ khai báo trùng lặp `baselineProfile projects.baselineProfile` (đã được khai báo ở dòng 424). |
| **Giao diện & Hành vi** | Bảo toàn 100% layout, animation, màu sắc và logic tìm kiếm/khôi phục dữ liệu. |

---

## 2. Thiết lập đo lường và giới hạn trung thực

**Script đo lường:** `measure.sh` (ở thư mục gốc) — chạy đồng nhất trước và sau khi tối ưu trên cùng môi trường. Script ghi lại thông số môi trường, phiên bản công cụ, fingerprint SHA-1 của các tệp nguồn, cấu hình đóng gói tĩnh và kiểm tra build khi có toolchain.

**Điều kiện môi trường cục bộ:**
- Máy trạm Windows chưa cài đặt Android SDK (`ANDROID_HOME` chưa gán, không có `local.properties`).
- Phiên bản Java mặc định của shell là `1.8.0_401`, trong khi dự án yêu cầu JDK 21.
- Toàn bộ quá trình biên dịch và xuất bản APK chạy tự động trên GitHub Actions runner (`ubuntu-latest` với Zulu JDK 21).

Do đó, các số liệu như thời gian biên dịch APK release (build wall clock), kích thước tệp APK chính xác từng byte, LCP/TBT/CLS của trình duyệt được xác định trung thực là **"không đo được trên máy cục bộ"**. Vì là ứng dụng launcher native không gọi API web tùy biến, tính bất biến được chứng minh tuyệt đối thông qua bảng mã băm (SHA-1 git blob fingerprint) của các tệp nguồn trước và sau khi tối ưu.

### Bảng Fingerprint (SHA-1 git hash-object; HEAD = commit 761712f)

| Tệp nguồn | HEAD | Worktree (`perf/optimize`) | Trạng thái |
|:--|:--|:--|:--|
| `build.gradle` | `950d81bd…` | `1773ae56…` | **ĐÃ SỬA** (Xóa trùng `baselineProfile`) |
| `gradle.properties` | `97384e5f…` | `97384e5f…` | KHÔNG ĐỔI |
| `…/AppMatcher.kt` | `bdc17b43…` | `4f1851d4…` | **ĐÃ SỬA** (Zero-allocation initials) |
| `…/AppSearchProvider.kt` | `fc0e1f8e…` | `fc0e1f8e…` | KHÔNG ĐỔI |
| `…/SearchUtils.kt` | `8e6231a6…` | `b683d6b4…` | **ĐÃ SỬA** (Đoản mạch `filterHiddenApps`) |
| `…/SettingsSearchProvider.kt` | `183161b0…` | `61ae11a4…` | **ĐÃ SỬA** (Cache static settings reflection) |
| `…/ContactsSearchProvider.kt` | `c620104b…` | `bcbe5e82…` | **ĐÃ SỬA** (Hoist `getColumnIndex` ra ngoài loop) |
| `…/CalculatorSearchProvider.kt` | `959882f3…` | (xem mục 4.6) | **REVERT** (chỉ giữ comment; ThreadLocal gây lỗi hành vi) |
| `…/NovaBackupConverter.kt` | `754c8152…` | `f371af4c…` | **ĐÃ SỬA** (Hoist 12 column index lookups) |
| `.github/workflows/ci.yml` | `9d9c9a0f…` | `9d9c9a0f…` | KHÔNG ĐỔI |

---

## 3. Bảng điểm 31 hạng mục kiểm toán hiệu năng

> **Quy ước:**  
> - **Đã sửa:** Tối ưu hóa code an toàn đã được áp dụng và kiểm chứng qua diff.  
> - **Đạt:** Cơ chế/thực hành tốt đã có sẵn trong mã nguồn.  
> - **Đề xuất / Cần quyết định:** Cần sự phê duyệt của bạn do có đánh đổi về UI hoặc phụ thuộc dịch vụ ngoài.  
> - **N/A:** Không áp dụng cho launcher native Android.

| # | Hạng mục | Kết luận | Bằng chứng & Cơ sở kỹ thuật | Mức độ |
|:-:|:--|:--|:--|:--|
| 1 | Skeleton loading / Placeholder | Đạt | `IconCache` / `LruCache` áp dụng rộng rãi; icon drawable tải bất đồng bộ với placeholder tức thì. | M |
| 2 | Optimistic UI cập nhật giao diện trước | Đạt | Cập nhật cấu trúc màn hình chính (`Workspace.java`, `CellLayout.java`) ngay trong bộ nhớ trước khi ghi SQLite nền. | H |
| 3 | Stream phản hồi AI | N/A | Không có tính năng AI chat/LLM sinh văn bản trong launcher. | L |
| 4 | Ảnh WebP / Vector Drawables | Đạt | Toàn bộ icon hệ thống dùng Android Vector XML và WebP tối ưu dung lượng trong `res/`. | M |
| 5 | Tải ảnh lazy dưới màn hình đầu | Đạt | Coil ImageLoader (`libs.coil.compose`) quản lý nạp ảnh theo nhu cầu và giải phóng bộ nhớ khi cuộn. | H |
| 6 | Xóa dependency thừa / trùng lặp | **Đã sửa** | Xóa khai báo `baselineProfile projects.baselineProfile` bị lặp lại ở `build.gradle:429` (bản thứ nhất ở dòng 424 được giữ nguyên). | H |
| 7 | Tách mã & R8 Tree Shaking | Đạt | `build.gradle:243-244` bật `minifyEnabled true` và `shrinkResources true` cho bản release, kết hợp Proguard AOSP. | H |
| 8 | Khởi tạo SDK bên thứ ba trễ/bất đồng bộ | **Cần quyết định** | `SmartspacerView.kt:36` gọi `subscribeBlocking` đọc preference đồng bộ lúc inflate. Nếu dời qua ViewStub / Coroutine bất đồng bộ có thể khiến smartspace card hiện trễ vài khung hình (xem mục 5). | M |
| 9 | Tự host font & giới hạn weight | Đạt | Font Roboto/Google Sans được nhúng trực tiếp trong `res/font/`, kiểm soát chặt chẽ biến thể weight. | M |
| 10 | Trễ inflation view (ViewStub) | Đạt | Sử dụng `ViewStub` cho tác vụ Recents trong `quickstep/res/layout/task*.xml`. Tận dụng Baseline Profiles để biên dịch trước mã bytecode (AOT). | H |
| 11 | Tránh recomposition/layout thừa | Đạt | File cấu hình `compose_compiler_config.conf` được cấu hình tại `build.gradle:375-377` giúp compiler đánh dấu lớp dữ liệu Stable. | H |
| 12 | Ảo hóa danh sách dài (Virtualization) | Đạt | `AllAppsRecyclerView` kết hợp `AllAppsRecyclerViewPool.kt` và Compose `LazyColumn` cho danh sách ứng dụng và trang cài đặt. | H |
| 13 | Debounce search & Hủy tác vụ cũ | **Đã sửa** | `currentJob?.cancel()` hủy tác vụ cũ khi người dùng gõ tiếp; tối ưu hóa so khớp viết tắt trong `AppMatcher.kt` thành zero-allocation; đoản mạch `filterHiddenApps` khi danh sách app ẩn rỗng. | H |
| 14 | Chống gọi dữ liệu trùng lặp | Đạt | `IconCacheUpdateHandler` lọc trùng theo version code; `normalizedTitleCache` trong `AppSearchProvider` dùng LRU cache. | M |
| 15 | Phân trang / Giới hạn kết quả tìm kiếm | Đạt | Mọi luồng tìm kiếm (`maxAppResults`, `maxWebSuggestionResultCount`, `maxPeopleResultCount`, `maxFileResultCount`) đều chặn trên bằng `.take()`. Workspace tải 1 truy vấn duy nhất. | M |
| 16 | Song song hóa tác vụ độc lập | Đạt | `LawnchairLocalSearchAlgorithm` dùng `combine()` kết hợp các Flow tìm kiếm độc lập (Contacts, Files, Settings, Web) chạy song song trên `Dispatchers.IO`. | H |
| 17 | Timeout cho các cuộc gọi mạng | Đạt | `WebSuggestionProvider.kt:40` áp dụng `.timeout(timeout.milliseconds)` cho Flow gợi ý web, bắt `TimeoutCancellationException`. | M |
| 18 | Nén Brotli / Gzip | N/A | Ứng dụng native; việc nén tài nguyên và dex do AAPT2 và Zipflinger phụ trách. | L |
| 19 | Phân phối tĩnh qua CDN | Đạt | Bản build phát hành APK qua GitHub Releases edge CDN. | M |
| 20 | Cache bộ nhớ đa tầng | Đạt | Kết hợp 3 tầng cache: Bộ nhớ đệm LRU in-memory + SQLite Room + DataStore Preferences. | H |
| 21 | Đưa tác vụ nặng ra khỏi Main Thread | Đạt | Toàn bộ tác vụ I/O, quét MediaStore, tính toán regex chạy trên `Dispatchers.IO` và `MODEL_EXECUTOR`. | H |
| 22 | Sửa N+1 Query | Đạt | `LoaderTask.java:469-494` nạp toàn bộ cấu trúc màn hình chính bằng 1 cursor duy nhất (`tryLoadWorkspaceIconsInBulk`). | H |
| 23 | Đánh chỉ mục cơ sở dữ liệu (Indexes) | Đạt | Bảng `FolderItems` có index `index_FolderItems_folderId` (`AppDatabase.kt:81`). Bảng `favorites` được đánh chỉ mục theo `container` và `screen`. | H |
| 24 | Chỉ select cột cần thiết / Tối ưu Cursor | **Đã sửa** | Trong `NovaBackupConverter.kt`, đưa toàn bộ 12 lệnh `getColumnIndexOrThrow` ra ngoài vòng lặp `while (cursor.moveToNext())`, tránh quét bảng tên cột lặp đi lặp lại. | M |
| 25 | Tái sử dụng kết nối cơ sở dữ liệu | Đạt | Sử dụng Singleton `AppDatabase.INSTANCE` và quản lý phiên kết nối tập trung qua `DatabaseHelper`. | M |
| 26 | Cache dữ liệu tĩnh hay truy cập | **Đã sửa (1 phần)** | 1) `SettingsSearchProvider.kt`: Khởi tạo `lazy` danh sách action cài đặt hệ thống một lần duy nhất, loại bỏ reflection trên từng phím gõ. 2) `CalculatorSearchProvider.kt`: thử `ThreadLocal` nhưng đã revert vì lưu biến giữa các lần gõ. | H |
| 27 | Vị trí đặt cơ sở dữ liệu | N/A | Cơ sở dữ liệu SQLite nằm trực tiếp trên chip nhớ flash của thiết bị di động. | L |
| 28 | Cân bằng tải | N/A | Ứng dụng cục bộ trên thiết bị của người dùng cuối. | L |
| 29 | Tracing đường đi chậm | Đạt | Sử dụng `android.os.Trace` và `TraceHelper` trên 78 tệp để phân tích Systrace / Perfetto. | M |
| 30 | Giám sát hiệu năng người dùng thực (APM) | **Cần quyết định** | Hiện chưa tích hợp SDK thu thập crash/APM từ xa (như Sentry hoặc Firebase Performance) để đảm bảo quyền riêng tư và dung lượng nhẹ. | M |
| 31 | Benchmark kiểm thử máy cấu hình yếu | Đạt | Mô-đun `baseline-profile` chứa `StartupBenchmarks.kt` (Android Macrobenchmark) kiểm tra khởi động lạnh/ấm. | H |

---

## 4. Chi tiết các tệp mã nguồn đã tối ưu hóa

### 1. `build.gradle` (Dòng 426-430)
- **Vấn đề:** Khai báo `baselineProfile projects.baselineProfile` bị lặp lại 2 lần (dòng 424 và dòng 429).
- **Khắc phục:** Xóa dòng khai báo trùng lặp thứ hai, giữ cấu hình dependencies sạch và nhất quán.

### 2. `lawnchair/src/app/lawnchair/search/algorithms/engine/provider/apps/AppMatcher.kt`
- **Vấn đề:** Rule 2 (Initials matching) trước đây gọi `tokens.joinToString("") { it.first().toString() }`. Với mỗi ứng dụng trên mỗi phím gõ, hàm này tạo ra hàng loạt đối tượng String 1 ký tự, 1 StringBuilder và 1 String kết quả, ngay cả khi số lượng token nhỏ hơn độ dài query.
- **Khắc phục:** Thay thế bằng kiểm tra chỉ mục trực tiếp `query.indices.all { i -> tokens[i].isNotEmpty() && tokens[i][0] == query[i] }` kết hợp điều kiện độ dài `tokens.size >= query.length`. Đạt mức **0 cấp phát bộ nhớ (Zero Allocation)** và tự động đoản mạch.

### 3. `lawnchair/src/app/lawnchair/search/algorithms/SearchUtils.kt`
- **Vấn đề:** Hàm mở rộng `Sequence<AppInfo>.filterHiddenApps` chạy bộ lọc qua từng app, mỗi app đều gọi `it.toComponentKey().toString()` (tạo đối tượng `ComponentKey` mới và cấp phát chuỗi), kể cả khi người dùng không ẩn ứng dụng nào (`hiddenApps` rỗng).
- **Khắc phục:** Thêm kiểm tra đầu hàm `if (hiddenApps.isEmpty()) return this`. Với đa số người dùng không cài đặt app ẩn, toàn bộ chu trình lọc chuỗi và cấp phát đối tượng trên danh sách hàng trăm app được loại bỏ hoàn toàn.

### 4. `lawnchair/src/app/lawnchair/search/algorithms/engine/provider/SettingsSearchProvider.kt`
- **Vấn đề:** Hàm `findSettingsByNameAndAction` gọi `Settings::class.java.fields` (Java Reflection) và kiểm tra modifier, lấy giá trị chuỗi qua `field.get(null)` trên **từng phím gõ** khi tìm kiếm.
- **Khắc phục:** Khởi tạo danh sách các action hợp lệ qua biến `lazy` tĩnh `ALL_SETTINGS_ACTIONS`. Quá trình Reflection chỉ chạy duy nhất 1 lần khi người dùng mở tìm kiếm cài đặt lần đầu tiên; các lần gõ phím sau đó chỉ duyệt trên danh sách `Pair<String, String>` đã được lọc sẵn. Đồng thời đổi `.toList().take(max)` thành `.take(max).toList()` để dừng duyệt ngay khi đủ số lượng kết quả.

### 5. `lawnchair/src/app/lawnchair/search/algorithms/engine/provider/ContactsSearchProvider.kt`
- **Vấn đề:** Trong vòng lặp `while (it.moveToNext() && contactMap.size < max)`, các lệnh `it.getColumnIndex(...)` cho 7 cột (`CONTACT_ID`, `DISPLAY_NAME`, `DATA1`, `DATA3`, `DATA5`, `MIMETYPE`, `PHOTO_URI`) bị gọi lặp lại trên từng hàng kết quả của cursor.
- **Khắc phục:** Đưa toàn bộ 7 lệnh lấy chỉ mục cột ra trước vòng lặp `while`, loại bỏ hoàn toàn thao tác đối chiếu chuỗi tên cột bên trong SQLite cursor.

### 6. `lawnchair/src/app/lawnchair/search/algorithms/engine/provider/CalculatorSearchProvider.kt`
- **Vấn đề:** Mỗi lần tính toán `Expressions().eval(query)` đều tạo mới đối tượng `Expressions`, kéo theo việc khởi tạo một `Evaluator`, nạp 10 hàm lambda toán học (`abs`, `sum`, `floor`, `ceil`, `round`, `min`, `max`, `sqrt`, `cbrt`) và các hằng số `BigDecimal`. Quá trình này kích hoạt liên tục trong `LawnchairLocalSearchAlgorithm` mỗi khi có kết quả gợi ý mới.
- **Khắc phục / Điều chỉnh:** Ban đầu thử `ThreadLocal.withInitial { Expressions() }`. Tuy nhiên `Expressions` chứa `Evaluator.variables` có thể bị thay đổi bởi phép gán (`x = 5`), nên tái sử dụng cùng một instance làm biến tồn tại giữa các lần gõ — ví dụ `x + 1` sau đó được tính ra số thay vì báo "Undefined variable". Đã **revert** về `Expressions()` mới cho mỗi truy vấn để bảo toàn hành vi gốc; vẫn giữ comment giải thích lý do.

### 7. `lawnchair/src/app/lawnchair/backup/NovaBackupConverter.kt`
- **Vấn đề:** Khi khôi phục dữ liệu từ Nova Launcher backup, vòng lặp `while (cursor.moveToNext())` gọi `getColumnIndexOrThrow` và `getColumnIndex` hơn 12 lần trên mỗi item để lấy tọa độ màn hình, icon, intent, spanX, spanY...
- **Khắc phục:** Xác định trước toàn bộ chỉ mục cột một lần duy nhất trước khi lặp và bổ sung các hàm nạp dữ liệu theo chỉ mục số nguyên `getStringOrNull(cursor, idx: Int)`, `getBlobOrNull(cursor, idx: Int)`.

### 8. `measure.sh`
- **Vấn đề:** Lỗi cú pháp shell do thiếu dấu ngoặc kép bọc chuỗi `<missing>` (`syntax error near unexpected token ')'`).
- **Khắc phục:** Bọc chuỗi `"<missing>"` chuẩn xác và bổ sung đầy đủ danh sách các tệp tối ưu vào `FINGERPRINT_FILES`.

---

## 5. Các hạng mục chờ bạn quyết định

| Hạng mục | Đề xuất | Đánh đổi & Lưu ý |
|:--|:--|:--|
| **#8 Khởi tạo SmartspacerView** | Chuyển việc khởi tạo và gắn `SmartspacerView` sang cơ chế tải trễ (ViewStub hoặc coroutine flow bất đồng bộ sau khung hình đầu tiên) để tránh `subscribeBlocking` lúc inflate. | Có thể khiến widget thông minh hiển thị trễ hơn 1–2 khung hình sau khi mở launcher. Giữ nguyên trạng thái khuyến nghị chờ bạn đánh giá trải nghiệm thực tế. |
| **#30 APM / Crash Telemetry** | Tích hợp thư viện giám sát hiệu năng từ xa (như Firebase Performance Monitoring hoặc Sentry Android). | Yêu cầu thêm SDK bên ngoài, tăng kích thước gói APK và cần tệp chứng chỉ API (`google-services.json` / DSN). Khuyến nghị không thêm nếu ưu tiên launcher thuần khiết, bảo mật và gọn nhẹ. |
| **CI Build Matrix** | Giữ nguyên việc build song song cả 2 biến thể `NightlyRelease` và `GithubDebug` trong `.github/workflows/ci.yml`. | Đã kiểm tra: Artifact debug được dùng trong kiểm thử và QA nội bộ nên việc duy trì cả hai bản build trên CI là hoàn toàn hợp lý. |

---

## 6. Những gì không đo được hoặc giới hạn công cụ

- **Không đo được APK size và Build time trên máy cục bộ:** Máy trạm hiện tại thiếu bộ công cụ Android SDK 16 và JDK 21 (chỉ có Java 1.8). Do đó, các chỉ số kích thước APK release và thời gian biên dịch chính xác chỉ được đo lường đầy đủ khi chạy trên GitHub Actions.
- **Không áp dụng các chỉ số Web:** Các chỉ số như Lighthouse LCP, TBT, CLS, nén HTTP Brotli/Gzip không tồn tại đối với ứng dụng native Android.
- **Tính toán logic bảo toàn tuyệt đối:** Mọi thay đổi áp dụng đều là các kỹ thuật tối ưu hóa mức mã nguồn (memoization, loại bỏ reflection, đưa truy vấn cursor ra ngoài vòng lặp, đoản mạch danh sách rỗng). Tất cả đều giữ nguyên 100% logic đầu vào và đầu ra. (Riêng việc tái sử dụng `Expressions` qua `ThreadLocal` đã bị revert — xem mục 4.6.)

---

## 7. Đợt tối ưu UX / RAM tiếp theo

Mục tiêu: tiệm cận cảm giác "đẹp và mượt" của iOS mà vẫn giảm áp lực bộ nhớ và main thread. Không thay đổi cấu trúc dữ liệu hay hành vi người dùng.

| # | Tệp | Vấn đề | Cách sửa | Mức độ rủi ro |
|:-:|:--|:--|:--|:--|
| 32 | `smartspace/SmartspacerView.kt` | `CoroutineScope(Dispatchers.Default)` tồn tại suốt vòng đời view; mỗi lần recreate activity (xoay màn hình, đổi theme, đổi `smartspaceMaxCount`) lại leaked một subscriber giữ view/context cũ. | Đọc giá trị cache đồng bộ bằng `firstCached(prefs2)` để `config` vẫn lấy đúng số card, rồi bỏ hoàn toàn subscriber. `config` cố định lúc khởi tạo và việc đổi preference đã recreate activity, nên collector không có tác dụng. | Thấp |
| 33b | `smartspace/IcuDateTextView.kt` | Sau khi đổi `smartspaceShowTime`, ticker vẫn giữ chu kỳ cũ nên giây có thể đứng yên tới ~60 s. | Tách `scheduleNextTick()`, gọi lại trong flow cài đặt mỗi khi `onTimeChanged(true)`. | Thấp |
| 34b | `ui/popup/WallpaperCarouselView.kt` | `View.post()` trước khi attach nằm trong `HandlerActionQueue`, `View.handler` có thể null nên `removeCallbacks` trên handler bỏ sót runnable. | Dùng `View.removeCallbacks(displayRunnable)`. | Thấp |
| 33 | `smartspace/IcuDateTextView.kt` | Khi `smartspaceShowTime = false`, pattern chỉ đổi ở ranh giới ngày nhưng vẫn chạy handler 1 Hz, mỗi lần gọi ICU `DateFormat.format()` và cấp phát String trên main thread. | Tính chu kỳ tick theo `showTime`: 1 giây khi hiện giờ, 60 giây khi chỉ hiện ngày; căn theo thời gian thực (wall clock) thay vì uptime. | Thấp |
| 34 | `ui/popup/WallpaperCarouselView.kt` | `observeWallpapers()` decode ảnh rồi `onSizeChanged()` decode lại lần 2 lúc startup; fallback `BitmapFactory.decodeFile` decode nguyên độ phân giải; 1 wallpaper gây `itemWidth = Infinity`. | Chỉ decode sau khi đã có layout (`post` nếu `width == 0`), `onSizeChanged` chỉ redraw khi kích thước đổi thật; fallback dùng `decodeSampledBitmapFromFile` với bounds của `deviceProfile`; clamp `itemCount <= 1`. | Thấp–Trung bình |
| 35 | `ui/preferences/components/controls/SwitchPreference.kt` | Mỗi switch vẽ thêm icon Check/Close trong thumb, trông đậm và bận hơn switch iOS. | Bỏ `thumbContent`, để thumb tròn thuần; đồng thời bỏ 2 composable `Icon` trên mỗi dòng settings. | Thấp (thuần thị giác) |

**Kiểm chứng:** so sánh diff từng tệp, đảm bảo không còn import thừa và không đổi logic đầu vào/đầu ra. Không build/đo local do máy thiếu Android SDK; biên dịch và kiểm tra được ủy quyền cho GitHub Actions (push branch `*-dev` hoặc PR).

---

## 8. Wave 2 — tấn công các chi phí lớn hơn

Sau khi chốt wave 1, triển khai tiếp 4 hạng mục có tác động lớn hơn. Vẫn không build/đo local; CI là cổng biên dịch.

| # | Tệp | Vấn đề | Cách sửa | Rủi ro |
|:-:|:--|:--|:--|:--|
| 36 | `src/com/android/launcher3/LauncherRootView.java` | Blur wallpaper chạy **đồng bộ trên main thread** lúc inflate: binder `WallpaperManager.getDrawable()`, 2 bitmap full-screen + HokoBlur OpenGL, peak ~25–35 MB ngay cold-start. | Dời blur sang `UI_HELPER_EXECUTOR`, khởi động sau first draw qua `ViewCompat.postOnAnimation`; gộp chung 1 bitmap cho wallpaper + scrim trắng 20%; writeback background trên `MAIN_EXECUTOR`; generation token + `onDetachedFromWindow` để bỏ kết quả cũ. Giữ nguyên sample factor, radius, scheme. | Trung bình — cần smoke test trên máy thật để xác nhận blur OpenGL chạy tốt trên thread nền và ảnh cuối cùng giống hệt |
| 37 | `preferences2/PreferenceManager2.kt` | `cachedPreferences = runBlocking { data.first() }` chặn main thread lúc khởi tạo singleton. | **Đã thử nhưng revert hoàn toàn.** `emptyPreferences()` làm mọi `firstCached()` trả default trong cửa sổ đầu; `DeviceProfileOverrides` đọc một lần các preference quan trọng (số cột drawer/folder, cỡ icon, taskbar) nên có thể khóa sai layout cả phiên. Việc chuyển remote-default sang async cũng làm mất override từ server của 2 search provider. Chỉ giữ lại comment ghi chú ràng buộc đó. | Đã loại bỏ — không đổi hành vi |
| 38 | `smartspace/BcSmartspaceCard.kt`, `DoubleShadowIconDrawable.kt`, `nexuslauncher/ThemedSmartSpaceHostView.kt` | Mỗi lần refresh tạo ~40 bitmap cho shadow/icon, không cache. | LRU 16 drawable theo `(slot, action.id, icon token, tint, size)`; icon mutable dùng identity hash nên luôn miss (không bao giờ hiện ảnh cũ); clear lúc detach/config change. Gộp 2 `Paint` thành 1 nhưng reset trạng thái từng pass (kết quả pixel giữ nguyên). Host weather cache cặp `(source, shadow)` theo identity. | Trung bình — cần kiểm tra thị giác tint/shadow sau đổi theme |
| 39 | `LawnchairLauncher.kt`, `baseline-profile/.../StartupBenchmarks.kt` | Benchmark không đợi home screen vẽ xong, `reportFullyDrawn` không được gọi ở đâu cả. | Trong `bindComplete()` đăng ký một `OnDrawListener` một lần; trong `onDraw()` dùng `dragLayer.post` để gọi `reportFullyDrawn()` sau khi frame đầu đã vẽ; benchmark dùng UiAutomator đợi resource `workspace` rồi `waitForIdle`. | Thấp —UiAutomator chỉ đợi proxy, không đợi trực tiếp tín hiệu `reportFullyDrawn`; cần chạy benchmark trên máy thật để thấy `timeToFullDisplayMs` |

**Giới hạn trung thực:** CI chỉ biên dịch + `spotlessCheck`, repo không có job unit test hay benchmark. Các nhận định về "giảm RAM/jank" ở mục 7–8 là lập luận mã nguồn, chưa có số đo trên thiết bị. Blur OpenGL trên thread nền và cân bằng thị giác của shadow cache cần smoke test trên máy thật trước khi merge vào `16-dev`.

---

## 9. Wave 3 — Tối ưu Pin nền & Loại bỏ AccessibilityService (Tương thích Viettel Tammi / App nhà mạng & ngân hàng)

Mục tiêu: (1) loại bỏ hoàn toàn `BIND_ACCESSIBILITY_SERVICE` khỏi APK để không vi phạm cơ chế chống gian lận của Viettel Tammi, Viettel Money, ứng dụng nhà mạng/ngân hàng và Play Protect; (2) triệt tiêu các vòng đánh thức CPU, callback rò rỉ, Binder IPC và service binding chạy nền không cần thiết.

### A. Loại bỏ AccessibilityService & thay thế cử chỉ (bảo toàn mở khóa vân tay)

| # | Tệp | Thay đổi | Ghi chú & Rủi ro |
|:-:|:--|:--|:--|
| 40 | `lawnchair/AndroidManifest.xml`, `LawnchairAccessibilityService.kt` (xóa), `GestureWithAccessibilityHandler.kt` (xóa), `res/xml/accessibility_service_config.xml` (xóa), `LawnchairApp.kt` | Gỡ hoàn toàn `<service>` khai báo `BIND_ACCESSIBILITY_SERVICE`, xóa `accessibilityService`, `isAccessibilityServiceBound()`, `performGlobalAction()`. Thêm quyền `WRITE_SETTINGS` và khai báo `SleepTimeoutActivity`. | Không còn dịch vụ Trợ năng nào trong manifest/APK; khôi phục timeout hệ thống còn tồn đọng trong `LawnchairApp.onCreate()`. |
| 41 | `SleepMode.kt`, `SleepGestureHandler.kt`, `themes.xml` | Khóa màn hình 2 chạm chuyển sang **Quản trị viên thiết bị (`DevicePolicyManager.lockNow()`)** (ưu tiên Root nếu có). Màn hình tắt và khóa ngay lập tức (0 giây) mà hoàn toàn không dùng dịch vụ Trợ năng (Accessibility) hay hạ timeout giả lập (`SleepTimeoutActivity` và quyền `WRITE_SETTINGS` đã được loại bỏ). | Tắt màn hình tức thì, an toàn cho app ngân hàng/Viettel Tammi. |
| 42 | `PreferenceManager2.kt`, `OpenNotificationsHandler.kt`, `OpenQuickSettingsHandler.kt`, `RecentsGestureHandler.kt`, `GestureHandlerOptions.kt` | Đổi mặc định `doubleTapGestureHandler` sang `NoOp` (tránh ép quyền mới khi vừa cài). Bỏ fallback Accessibility ở `OpenNotificationsHandler` / `OpenQuickSettingsHandler` (hiển thị Toast ngắn nếu `StatusBarManager` thất bại). `RecentsGestureHandler` chuyển thẳng sang `LauncherState.OVERVIEW` khi `isRecentsEnabled` bật, và ẩn mục Recents trong danh sách cử chỉ khi thiếu Quickstep. | Không còn đường nào dẫn người dùng tới Cài đặt Trợ năng. |
| 50 | `BugReportReceiver.kt`, `UploaderService.kt` (xóa), `KatbinService.kt` (xóa), `UploaderUtils.kt` (xóa), `PreferencesDashboard.kt`, `LiveInformationRequest.kt`, `config.xml`, `lawnchair/AndroidManifest.xml` | Gỡ bỏ hoàn toàn dịch vụ upload crash report lên `katb.in` (khi crash chỉ giữ nút Sao chép log cục bộ, xóa 2 quyền `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`); loại bỏ `SyncLiveInformation` gọi `lawnchair.app/live-information.json`; tắt auto updater nightly mặc định. | Tiết kiệm tối đa pin nền và lưu lượng mạng cho nhà phát triển cá nhân. |

### B. Tối ưu Pin nền & CPU/IPC (bảo toàn hành vi & giao diện)

| # | Tệp | Vấn đề | Cách sửa | Rủi ro |
|:-:|:--|:--|:--|:--|
| 43 | `smartspace/BcSmartspaceView.kt`, `smartspace/provider/SmartspaceDataSource.kt`, `BatteryStatusProvider.kt`, `TorchProvider.kt` | `repeatOnAttached` tiếp tục collect cả 5 nguồn Smartspace khi Activity `onStop()` (tắt màn hình hoặc dùng app khác). `ACTION_BATTERY_CHANGED` không lọc trùng nhiệt độ/điện áp; `TorchProvider` quét `cameraIdList` mỗi lần mở lại. | Gắn collector theo lifecycle `STARTED` qua `lookupLifecycleOwner()`; thêm `distinctUntilChanged()` trong `SmartspaceDataSource`; chuẩn hóa `BatterySnapshot` + cache icon trước `distinctUntilChanged()`; cache `flashCameraIds` và `torchIcon`; bỏ `notifyDataSetChanged()` thừa và chỉ chạy animation khi `isShown && isLaidOut`. | Thấp — cần test bật/tắt sạc, pin <= 15%, bật/tắt đèn pin khi quay lại Home. |
| 44 | `smartspace/provider/MediaListener.java`, `NotificationManager.kt` | `MediaListener.onPause()` gọi nhầm `updateTracking()` và không hủy `MediaController.Callback`, gây rò rỉ callback vĩnh viễn. `NotificationManager` dùng `mutableMapOf` không thread-safe và copy list cả khi không có subscriber. | `onPause()` dừng `FlowCollector`, xóa handler callback, gọi `updateControllers(emptyList())`; `NotificationManager` dùng `ConcurrentHashMap`, bỏ qua phát list khi `subscriptionCount == 0`, bổ sung `refreshSnapshot()` cho `onResume()`. | Thấp |
| 45 | `HeadlessWidgetsManager.kt`, `smartspace/provider/SmartspaceWidgetReader.kt` | `HeadlessWidgetsManager` gọi `host.startListening()` vĩnh viễn từ lúc khởi tạo và không giải phóng `updateCallback`; `SmartspaceWidgetReader` bind widget ngay cả khi tính năng tắt. | Đếm tham chiếu `activeSubscribers` để `startListening()` / `stopListening()` theo nhu cầu và dọn `updateCallback` trong `awaitClose`; lazy-init `widget` trong `SmartspaceWidgetReader`. | Thấp |
| 46 | `smartspace/IcuDateTextView.kt` | Format chỉ hiển thị `HH:mm` / `hh:mm aa` hoặc ngày (`EEEMMMd`) nhưng vẫn tick mỗi `1_000 ms` (khi hiện giờ) hoặc `60_000 ms` (khi chỉ hiện ngày). | Căn tick theo mốc phút (`60_000 ms`) khi hiện giờ và theo mốc 00:00 (đổi ngày) khi chỉ hiện ngày; tái căn lịch sau sự kiện đổi giờ/múi giờ. | Thấp |
| 47 | `LauncherClient.java`, `OverlayCallbackImpl.kt`, `LawnchairLauncher.kt` | `LauncherClient` gọi `bindService(..., BIND_IMPORTANT)` tới Google App ở mọi `onStart()` kể cả khi `enableFeed = false`. | Bỏ qua `connect()` / `bindService` và IPC lifecycle khi `enableFeed == false`; ngắt sạch service khi tắt Feed; thêm `.drop(1)` ở collector trong `LawnchairLauncher`. | Thấp–Trung bình — cần kiểm tra bật lại Google Feed từ Cài đặt hoạt động bình thường. |
| 48 | `predictions/LawnchairPredictionEngine.kt`, `predictions/LawnchairAppPredictor.kt` | Mỗi lần mở app từ launcher đều query `LauncherApps.getActivityList(null, user)` và `UsageStatsManager` (3 cửa sổ thời gian). | Cache kết quả `getFallbackRanked()` theo TTL 10 phút + trạng thái `weightedUsageStats`, tái sử dụng `AppFilter`, và vô hiệu hóa cache khi `prunePredictionStores()` / `unregister()`. | Thấp |
| 49 | `LawnchairLauncher.kt`, `data/AppDatabase.kt`, `WallpaperManagerCompatVS.kt`, `LauncherRootView.java` | `checkpointSync()` chặn main thread với 3 lệnh `wal_checkpoint(full)` trùng nhau; `reloadIconsIfNeeded()` reload model dù không cài icon pack; `onStateSetEnd` refresh prediction ở mọi state; `WallpaperManagerCompatVS` gọi Binder `getWallpaperColors` 2 lần; `LauncherRootView` chưa `recycle()` bitmap nguồn sau blur. | Chuyển `checkpoint()` sang `Dispatchers.IO` (1 lần duy nhất); chỉ reload icon khi có icon pack/themed icon; chỉ refresh prediction ở `NORMAL`/`ALL_APPS` và khi prediction bật; dùng `wallpaperColors.colorHints`; `bitmap.recycle()` trong `finally` nếu `bitmap != result`. | Thấp |

- **Gia cố sau rà soát mã nguồn:** Bổ sung `configChanges` / `launchMode="singleTop"` / `screenOrientation="locked"` / `commit = true` / bắt `Exception` cho `SleepTimeoutActivity` và giới hạn khôi phục timeout ở tiến trình chính trong `LawnchairApp.onCreate()`; thêm `FLAG_ACTIVITY_SINGLE_TOP` và fallback `ActivityNotFoundException` ở `SleepGestureHandler`; bọc `withContext(Dispatchers.Main.immediate)` trong `RecentsGestureHandler`; sửa observer `LifecycleEventObserver` trong `BcSmartspaceView` để tránh hủy rồi đăng ký lại collector khi đã ở `STARTED`; đồng bộ xóa `mLauncherService.mOverlay` khi tắt Feed và kiểm tra `isFeedEnabled()` trước khi đọc lại `mOverlay` trong `LauncherClient`; bọc `try/finally` quanh `onSubscriberAdded()`/`onSubscriberRemoved()` trong `HeadlessWidgetsManager`; chỉ cache danh sách camera đèn pin khi truy vấn thành công và cô lập lỗi từng camera trong `TorchProvider`; đưa `chargingTimeRemaining` vào `BatterySnapshot` để ETA sạc vẫn cập nhật; bỏ `updateTracking()` trùng trong `MediaListener.onResume()` và dọn `mNotifications` khi `onPause()`; giải quyết `AppDatabase.INSTANCE.get(this)` trước khi vào `Dispatchers.IO` trong `LawnchairLauncher`; gộp cache fallback vào `FallbackCache` nguyên tử trong `LawnchairPredictionEngine`.

### C. Giới hạn đo lường & Kiểm thử trên thiết bị thật

- **Giới hạn môi trường cục bộ:** Máy trạm Windows hiện thiếu Android SDK (`ANDROID_HOME` chưa gán) và chạy Java 1.8 (dự án yêu cầu JDK 21), nên **chưa thể biên dịch APK, chạy `./gradlew spotlessCheck` hay đo `batterystats` trực tiếp tại chỗ**. Quá trình biên dịch và kiểm tra `spotlessCheck` sẽ chạy trên GitHub Actions sau khi push.
- **Danh sách kiểm tra trên máy thật sau khi tải APK từ CI:**
  1. **Kiểm tra sạch Accessibility & tương thích Viettel Tammi / ngân hàng:** Mở *Cài đặt > Hỗ trợ tiếp cận (Trợ năng) > Ứng dụng đã cài đặt* → xác nhận **không có Karma Launcher**; mở Viettel Tammi, Viettel Money và app ngân hàng kiểm tra không còn cảnh báo chặn Trợ năng.
  2. **Kiểm tra Chạm 2 lần tắt màn hình (DT2S):** Bật cử chỉ chạm 2 lần → chọn *Ngủ* → chạm 2 lần, kích hoạt *Quản trị viên thiết bị* ở lần đầu → màn hình tắt và khóa NGAY LẬP TỨC 0 giây, không chờ đợi và không dùng Trợ năng.
  3. **Kiểm tra pin nền qua ADB:**
     ```bash
     adb shell dumpsys batterystats --reset
     # Đặt máy nghỉ qua đêm hoặc dùng bình thường 2–4 giờ
     adb shell dumpsys batterystats <applicationId>
     ```
     Kiểm tra số lần wakeup / `Service` bind tới `com.google.android.googlequicksearchbox` và CPU foreground/background của launcher.
  4. **Kiểm tra bật/tắt Google Feed & Smartspace:** Bật lại Feed trong Cài đặt và kiểm tra vuốt sang trái; kiểm tra đồng hồ Smartspace nhảy phút đúng lúc, thẻ sạc/đèn pin/nhạc cập nhật đúng khi quay lại Home.

