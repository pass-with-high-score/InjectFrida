# Luồng xử lý (Workflow) Inject Frida Gadget vào APK

Tài liệu này mô tả nguyên lý hoạt động và các bước kỹ thuật cần thiết để xây dựng tính năng tự động nhúng Frida Gadget vào một ứng dụng Android (APK) trực tiếp trên thiết bị di động (không cần root). 

Đây là một quy trình phức tạp đòi hỏi tích hợp nhiều thư viện xử lý file DEX/APK.

## Bước 1: Giải nén và Dịch ngược APK (Decompilation)
*   **Mục tiêu**: Lấy mã nguồn (dạng Smali) và cấu trúc thư mục của file APK gốc.
*   **Chi tiết triển khai**: Cần tích hợp một engine dịch ngược (tương tự Apktool) để bung file `.apk`. Quá trình này sẽ parse file `classes.dex` thành các file `.smali` để có thể chỉnh sửa, đồng thời giải nén thư mục `lib/` và `assets/`.

## Bước 2: Tích hợp Frida Gadget Binary
*   **Mục tiêu**: Chuẩn bị thư viện nhị phân `.so` của Frida Gadget.
*   **Chi tiết triển khai**:
    1. Ứng dụng cần phân tích thư mục `lib/` của APK gốc để xác định các kiến trúc được hỗ trợ (ví dụ: `arm64-v8a`, `armeabi-v7a`).
    2. Tải hoặc trích xuất file `frida-gadget-<version>-android-<arch>.so` tương ứng.
    3. Đổi tên file thành `libfrida-gadget.so`.
    4. Sao chép file này vào đúng thư mục kiến trúc bên trong APK đã giải nén (ví dụ: `lib/arm64-v8a/libfrida-gadget.so`).

## Bước 3: Xác định Điểm khởi chạy (Entry Point)
*   **Mục tiêu**: Tìm đúng file class sẽ được hệ thống chạy đầu tiên khi khởi động ứng dụng.
*   **Chi tiết triển khai**:
    1. Parse file `AndroidManifest.xml` (có thể cần decode từ định dạng nhị phân AXML).
    2. Tìm thẻ `<application>`. Nếu thẻ này khai báo thuộc tính `android:name`, đó chính là class Entry Point (ví dụ: `com.example.app.MyApplication`).
    3. Nếu không có `android:name`, tiếp tục quét các thẻ `<activity>` để tìm Activity chính (có chứa `<intent-filter>` với `android.intent.action.MAIN` và `android.intent.category.LAUNCHER`).

## Bước 4: Chèn mã khởi động (Smali Patching)
*   **Mục tiêu**: Chỉnh sửa mã nguồn để ứng dụng tự động load thư viện Frida ngay khi khởi động.
*   **Chi tiết triển khai**:
    1. Mở file `.smali` tương ứng với class Entry Point tìm được ở Bước 3.
    2. Tìm hàm khởi tạo tĩnh `<clinit>` hoặc hàm vòng đời sớm nhất (như `onCreate()`).
    3. Tiêm (inject) đoạn mã Smali sau vào đầu khối lệnh để gọi `System.loadLibrary("frida-gadget")`:
        ```smali
        const-string v0, "frida-gadget"
        invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
        ```

## Bước 5: Đóng gói lại (Recompilation)
*   **Mục tiêu**: Gói toàn bộ cấu trúc thư mục đã sửa đổi thành một file APK mới.
*   **Chi tiết triển khai**: Sử dụng công cụ/thư viện biên dịch (compiler) để chuyển đổi lại các file `.smali` thành file `classes.dex`. Sau đó, nén toàn bộ thư mục cùng với `AndroidManifest.xml` và `resources.arsc` thành file `.apk`.

## Bước 6: Căn chỉnh (Zipalign) và Ký tên (Signing)
*   **Mục tiêu**: Ký điện tử cho file APK để hệ điều hành Android chấp nhận cài đặt.
*   **Chi tiết triển khai**:
    1. **Zipalign**: Sử dụng thuật toán zipalign để căn chỉnh dữ liệu không nén (uncompressed data) theo biên 4-byte, tối ưu hóa RAM khi chạy.
    2. **Sign**: Do file APK đã bị sửa đổi, chữ ký gốc sẽ không hợp lệ. Ứng dụng Injector cần tự tạo một Keystore nội bộ và sử dụng thuật toán ký (tương đương `apksigner` v2/v3) để ký lại file APK đầu ra.

## Bước 7: Cài đặt và Kết nối
*   File APK đầu ra (Patched APK) lúc này đã chứa sẵn Frida Gadget.
*   Khi người dùng cài đặt và mở ứng dụng này lên, Gadget sẽ được kích hoạt cùng với tiến trình của ứng dụng và mặc định lắng nghe kết nối trên cổng `27042` (trừ khi có file cấu hình tùy biến).
*   Công cụ Injector lúc này có thể kết nối với ứng dụng mục tiêu thông qua mạng loopback nội bộ để thực hiện các thao tác tiếp theo.
