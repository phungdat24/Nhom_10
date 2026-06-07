# AuctionSystem — Hệ thống đấu giá trực tuyến (Nhóm 10)

## Checklist

- [x] Mô tả dự án
- [x] Công nghệ và yêu cầu cài đặt
- [x] Cấu trúc dự án (directory tree)
- [x] Hướng dẫn khởi chạy theo thứ tự Server → Client
- [x] Câu lệnh chạy trên Windows / macOS / Linux
- [x] Lưu ý quan trọng khi chạy đa nền tảng
- [x] Danh sách chức năng đã hoàn thành
- [x] Chú ý khi chạy test / debug

---

## 1. Mô tả dự án

`AuctionSystem` là ứng dụng **đấu giá trực tuyến** được xây dựng theo mô hình **Client–Server**, bao gồm:

- **Server:** Chịu trách nhiệm quản lý toàn bộ các phiên đấu giá, lưu trữ dữ liệu người dùng bảo mật, thông tin sản phẩm, quản lý trạng thái các phòng đấu giá, xử lý các giao dịch đặt giá và hệ thống tự động nâng giá đấu giá (**auto-bidding**).
- **Client:** Giao diện đồ họa (**GUI**) được xây dựng bằng **JavaFX**, cho phép các phân hệ người dùng gồm **Người bán (Seller)**, **Người đấu giá (Bidder)** và **Quản trị viên (Admin)** tương tác với hệ thống: đăng ký / đăng nhập tài khoản, tham gia phòng đấu giá trực tuyến, đặt giá, quản lý số dư ví cá nhân và cấu hình thuộc tính tự động đặt giá.

### Phạm vi hệ thống

- Hỗ trợ quy trình tạo sản phẩm, thiết lập phòng đấu giá và tự động cập nhật trạng thái phòng phiên theo thời gian thực: `PENDING` → `ACTIVE` → `FINISHED`.
- Cho phép đặt giá thủ công hoặc kích hoạt tính năng tự động đấu giá (**auto-bid**).
- Quản lý dòng tiền của ví điện tử cá nhân: nạp tiền, rút tiền.
- Hiển thị lịch sử giá của từng phiên đấu giá.

---

## 2. Công nghệ và yêu cầu

### Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ phát triển | Java |
| Công cụ quản lý build | Apache Maven |
| Giao diện người dùng (GUI Client) | JavaFX |
| Cơ sở dữ liệu | SQLite (`myDatabase.db`, `database_test.db`) |
| Hệ thống logging | SLF4J tích hợp Logback |
| Kiểm thử tự động (Unit tests) | JUnit 5 |

### Yêu cầu phần mềm và môi trường khuyến nghị

- **JDK:** Phiên bản **JDK ≥ 17**.
- **Apache Maven:** Phiên bản **Maven ≥ 3.6** và đã khai báo biến môi trường `PATH`.
- **Hệ điều hành hỗ trợ:** Windows, macOS và Linux.

---

## 3. Cấu trúc dự án (tóm tắt)

Sơ đồ cây thư mục rút gọn tập trung hiển thị các module và điểm khởi chạy chính:

```text
Nhom_10/
├── .github/
│   └── workflows/
│       └── maven-ci.yml                 # GitHub Actions: build, Checkstyle, test
├── auction_images/                      # Ảnh phục vụ dữ liệu demo
├── pom.xml                              # Cấu hình Maven
├── mvnw                                 # Maven Wrapper cho macOS/Linux
├── mvnw.cmd                             # Maven Wrapper cho Windows
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java
│   │   │   └── com/nhomX/example/
│   │   │       ├── Main.java            # Entry point JavaFX Client
│   │   │       ├── controller/          # Controller JavaFX theo MVC
│   │   │       │   ├── admin/
│   │   │       │   ├── client/
│   │   │       │   └── shared/
│   │   │       ├── dto/                 # Data Transfer Objects
│   │   │       ├── exception/           # Custom exceptions
│   │   │       ├── factory/             # ItemFactory
│   │   │       ├── manager/             # Cache và session managers
│   │   │       ├── model/               # Domain models
│   │   │       ├── networking/          # Server, ClientHandler, Scheduler, Message
│   │   │       ├── repository/          # Repository interfaces và implementations
│   │   │       ├── service/             # Business services, email service
│   │   │       └── utils/               # DB, scene switcher, formatter, validator
│   │   └── resources/
│   │       ├── logback.xml               # Cấu hình logging
│   │       └── com/nhomX/example/
│   │           ├── css/                  # Stylesheet JavaFX
│   │           ├── fxml/                 # View JavaFX: admin, client
│   │           └── images/               # Ảnh giao diện và sản phẩm
│   └── test/
│       └── java/com/nhomX/example/
│           ├── networking/
│           │   └── AuctionFlowTest.java
│           └── repository/
│               ├── AuctionRuleTest.java
│               ├── ConcurrentBidTest.java
│               └── DatabaseBackedTest.java
└── auction.db                            # Tự sinh khi Server chạy lần đầu
```


## 4. Hướng dẫn khởi chạy (Thứ tự: Server → Client)

Để hệ thống vận hành ổn định và tương thích trên Windows / macOS / Linux, nên sử dụng Maven để tự động xử lý các vấn đề liên quan đến classpath.

### Bước 1. Đóng gói và biên dịch dự án

Mở Terminal / CMD tại thư mục gốc của dự án và chạy:

```bash
mvn clean package
```

### Bước 2. Khởi tạo dữ liệu mẫu

Để nạp sẵn danh sách tài khoản Admin, người dùng và các phiên đấu giá mẫu vào cơ sở dữ liệu, chạy:

```bash
mvn exec:java -Dexec.mainClass="com.nhomX.example.DatabaseTest"
```

### Bước 3. Chạy hệ thống Server trước

```bash
mvn exec:java -Dexec.mainClass="com.nhomX.example.networking.AuctionServer"
```

Đợi đến khi Terminal hiển thị log:

```text
SERVER: Đang đợi kết nối tại cổng 8080...
```

### Bước 4. Chạy giao diện Client sau khi Server đã bật

Mở **một cửa sổ Terminal / CMD mới** tại thư mục gốc dự án và chạy:

```bash
mvn javafx:run
```

---

## 5. Câu lệnh thực thi thủ công (Cross-platform)

Trong trường hợp không dùng Maven để chạy trực tiếp mà muốn thực thi mã đã biên dịch bằng lệnh `java`, cần lưu ý sự khác biệt về dấu phân cách thư viện (**classpath separator**) giữa các hệ điều hành.

### Windows — dùng dấu chấm phẩy `;`

```dos
java -cp "target/classes;target/dependency/*" com.nhomX.example.Main
```

### macOS / Linux — dùng dấu hai chấm `:`

Trên một số máy macOS / Linux đời mới, nên bổ sung cờ cấp quyền truy cập để tránh cảnh báo:

```bash
java --enable-native-access=ALL-UNNAMED -cp "target/classes:target/dependency/*" com.nhomX.example.Main
```

---

## 6. Chạy Unit tests

Thực thi toàn bộ các bài kiểm tra tự động tích hợp trong mã nguồn:

```bash
mvn test
```

---

## 7. Danh sách chức năng đã hoàn thành

- **Quản lý người dùng:** Đăng ký, đăng nhập, phân quyền (`role`) và quản lý phiên (`session`).
- **Sản phẩm và phòng đấu giá:** Thêm sản phẩm, tạo phòng, tự động cập nhật trạng thái (`PENDING`, `ACTIVE`, `FINISHED`) và lấy thông tin phòng.
- **Nghiệp vụ đấu giá:** Đặt giá thủ công, truy xuất giá hiện tại, ghi nhận giao dịch giá mới nhất, hiển thị lịch sử đặt giá và đếm số người tham gia trực tiếp.
- **Auto-bid:** Cấu hình tự động đặt giá; hỗ trợ lưu, lấy, kiểm tra và hủy cấu hình tự động.
- **Ví và giao dịch:** Cập nhật số dư tài khoản, nạp / rút tiền ví điện tử và kết xuất lịch sử dòng tiền.
- **Kết nối cơ sở dữ liệu:** Áp dụng mẫu thiết kế **Singleton** cho kết nối JDBC SQLite (`ConnectDatabase`).

---

## 8. Troubleshooting — Vấn đề thường gặp

### Lỗi font chữ tiếng Việt trên CMD Windows (Mojibake)

Chuyển bảng mã sang UTF-8 trước khi chạy lệnh Java / Maven:

```dos
chcp 65001
```

### Lỗi khóa SQLite: `database is locked`

Lỗi xảy ra khi chưa tắt tiến trình Server / Client cũ mà đã chạy lệnh mới hoặc khi đang mở file `.db` bằng phần mềm xem cơ sở dữ liệu.

Cách khắc phục:

1. Dừng các tiến trình ngầm bằng tổ hợp phím `Ctrl + C` trong Terminal.
2. Đóng phần mềm đang mở file cơ sở dữ liệu.
3. Chạy lại Server trước, sau đó mới chạy Client.

### Lỗi trùng cổng: `Port 8080 is already in use`

Cổng `8080` đang bị một tiến trình khác chiếm dụng.

Cách khắc phục:

- Tắt ứng dụng hoặc tiến trình đang sử dụng cổng `8080`; hoặc
- Đổi số cổng mạng trong file `AuctionServer.java`, đồng thời cập nhật cấu hình phía Client nếu cần.

---

## 9. Link báo cáo và video demo
https://drive.google.com/drive/folders/1O7m7HRqchwUtjQ6zfV3i6xJivEHbNUpZ?usp=sharing