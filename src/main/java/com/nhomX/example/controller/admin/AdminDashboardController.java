package com.nhomX.example.controller.admin;

import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.dto.DashboardDataDTO;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class AdminDashboardController implements Initializable, ServerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

    // ── KPI Cards ────────────────────────────────────────────────────────
    @FXML
    private VBox cardUsers;
    @FXML
    private VBox cardLive;
    @FXML
    private VBox cardRevenue;
    @FXML
    private Label labelTotalUsers;
    @FXML
    private Label labelLiveAuctions;
    @FXML
    private Label labelTotalRevenue;
    @FXML
    private Label labelUserGrowth;

    // ── AreaChart ────────────────────────────────────────────────────────
    @FXML
    private AreaChart<String, Number> revenueChart;
    @FXML
    private CategoryAxis chartXAxis;
    @FXML
    private NumberAxis chartYAxis;

    // ── Category bars ────────────────────────────────────────────────────
    @FXML
    private Label labelCatJewelry;
    @FXML
    private Label labelCatElec;
    @FXML
    private Label labelCatArt;
    @FXML
    private HBox barBgJewelry;
    @FXML
    private HBox barBgElec;
    @FXML
    private HBox barBgArt;
    @FXML
    private Region barFillJewelry;
    @FXML
    private Region barFillElec;
    @FXML
    private Region barFillArt;
    @FXML
    private Label labelOverallGrowth;

    // ── PieChart ─────────────────────────────────────────────────────────
    @FXML
    private PieChart statusPieChart;
    @FXML
    private Label labelPieLive;
    @FXML
    private Label labelPiePending;
    @FXML
    private Label labelPieClosed;

    // ── Transactions ─────────────────────────────────────────────────────
    @FXML
    private GridPane transactionGrid;

    // ── Misc ─────────────────────────────────────────────────────────────
    private final NumberFormat vndFmt = NumberFormat.getInstance(new Locale("vi", "VN"));
    private AuctionClient client;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        styleChart();
        setupCardHoverEffects();

        // Gắn listener và yêu cầu dữ liệu từ Server
        client = SessionManager.getInstance().getAuctionClient();
        if (client == null) {
            logger.error("CLIENT: Chưa khởi tạo AuctionClient cho Admin Dashboard");
            return;
        }
        client.addListener(this);
        client.sendToServer(new Message("GET_DASHBOARD_DATA"));
    }

    // ── ServerEventListener ──────────────────────────────────────────────

    @Override
    public void onDashboardDataReceived(DashboardDataDTO dto) {
        // Luôn được gọi trên JavaFX Application Thread
        // (AuctionClient đã wrap Platform.runLater trước khi gọi listener)
        updateKpiCards(dto);
        updateRevenueChart(dto.revenueByDay);
        updateCategoryBars(dto.finishedByCategory);
        updatePieChart(dto.countLive, dto.countPending, dto.countClosed);
        updateTransactionGrid(dto.recentTransactions);
    }

    // ── KPI Cards ────────────────────────────────────────────────────────

    private void updateKpiCards(DashboardDataDTO dto) {
        labelTotalUsers.setText(vndFmt.format(dto.totalUsers));
        labelLiveAuctions.setText(String.valueOf(dto.liveAuctions));
        labelTotalRevenue.setText(formatShortVnd(dto.totalRevenue));
        // Online count badge (không có riêng label trong FXML, reuse growth label)
        labelUserGrowth.setText("● " + dto.onlineUsers + " online");
    }

    // ── AreaChart ────────────────────────────────────────────────────────

    private void updateRevenueChart(Map<String, Long> revenueByDay) {
        revenueChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Doanh thu");

        for (Map.Entry<String, Long> entry : revenueByDay.entrySet()) {
            XYChart.Data<String, Number> dataPoint =
                    new XYChart.Data<>(entry.getKey(), entry.getValue());
            series.getData().add(dataPoint);
        }

        revenueChart.getData().add(series);

        // Gắn Tooltip vào từng data point SAU KHI chart đã render
        Platform.runLater(() -> {
            attachChartTooltips(series);
            styleChart();
        });
    }

    /**
     * Gắn Tooltip hiển thị số tiền chính xác khi hover vào data point. Phải gọi sau khi Scene đã
     * layout xong (dùng Platform.runLater).
     */
    private void attachChartTooltips(XYChart.Series<String, Number> series) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            Node node = data.getNode();
            if (node == null)
                continue;

            long amount = data.getYValue().longValue();
            Tooltip tip = new Tooltip(data.getXValue() + "\n" + vndFmt.format(amount) + " đ");
            tip.setStyle("-fx-background-color: #252220; " + "-fx-text-fill: #F0EAD6; "
                    + "-fx-font-size: 12px; " + "-fx-background-radius: 8; "
                    + "-fx-padding: 8 12 8 12;");
            Tooltip.install(node, tip);

            // Hiệu ứng phóng to nhẹ khi hover
            node.setOnMouseEntered(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
                st.setToX(1.4);
                st.setToY(1.4);
                st.play();
            });
            node.setOnMouseExited(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
        }
    }

    // ── Category Bars ────────────────────────────────────────────────────

    private void updateCategoryBars(Map<String, Integer> finishedByCategory) {
        // Lấy số lượng theo key category từ DB
        int jewelry = finishedByCategory.getOrDefault("JEWELRY", 0);
        int elec = finishedByCategory.getOrDefault("ELECTRONICS", 0);
        int art = finishedByCategory.getOrDefault("ART", 0);
        int max = Math.max(1, Math.max(jewelry, Math.max(elec, art)));

        labelCatJewelry.setText(vndFmt.format(jewelry));
        labelCatElec.setText(vndFmt.format(elec));
        labelCatArt.setText(vndFmt.format(art));

        // Chiều rộng tối đa của thanh bar = chiều rộng của HBox container
        // Dùng binding layout bounds để cập nhật sau khi render xong
        bindCategoryBar(barBgJewelry, barFillJewelry, jewelry, max);
        bindCategoryBar(barBgElec, barFillElec, elec, max);
        bindCategoryBar(barBgArt, barFillArt, art, max);

        // Tính growth tổng thể (giả sử so với kỳ trước — cần thêm data nếu muốn chính xác)
        int total = jewelry + elec + art;
        labelOverallGrowth.setText("+" + total + " hoàn tất");
    }

    private void bindCategoryBar(HBox background, Region fill, int value, int max) {
        fill.prefWidthProperty().unbind();
        fill.prefWidthProperty().bind(background.widthProperty().multiply(value / (double) max));
    }

    // ── PieChart ─────────────────────────────────────────────────────────

    private void updatePieChart(int live, int pending, int closed) {
        int total = Math.max(1, live + pending + closed);

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("Live", live), new PieChart.Data("Chờ Duyệt", pending),
                new PieChart.Data("Đã Đóng", closed));
        statusPieChart.setData(pieData);

        // Tô màu đúng theo thiết kế + gắn Exploded Slice khi click
        String[] colors = {"#2ECC71", "#9CA3AF", "#888070"};
        for (int i = 0; i < pieData.size(); i++) {
            PieChart.Data slice = pieData.get(i);
            final int idx = i;
            // Đợi Node được render rồi mới tô màu
            Platform.runLater(() -> {
                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + colors[idx] + ";");
                    slice.getNode().setOnMouseClicked(e -> toggleExplodedSlice(slice));
                }
            });
        }

        // Cập nhật label phần trăm
        labelPieLive.setText(String.format("%.0f%%", live * 100.0 / total));
        labelPiePending.setText(String.format("%.0f%%", pending * 100.0 / total));
        labelPieClosed.setText(String.format("%.0f%%", closed * 100.0 / total));
    }

    /**
     * Toggle Exploded trạng thái của một slice. Nếu đang exploded → thu về; ngược lại → tách ra.
     */
    private void toggleExplodedSlice(PieChart.Data slice) {
        boolean currentlyExploded =
                slice.getNode().getTranslateX() != 0 || slice.getNode().getTranslateY() != 0;
        if (currentlyExploded) {
            // Thu về trung tâm
            slice.getNode().setTranslateX(0);
            slice.getNode().setTranslateY(0);
        } else {
            // Tính vector hướng ra ngoài từ tâm chart
            double angle = getSliceMidAngle(slice);
            double dist = 12.0; // pixel tách ra
            slice.getNode().setTranslateX(Math.cos(Math.toRadians(angle)) * dist);
            slice.getNode().setTranslateY(Math.sin(Math.toRadians(angle)) * dist);
        }
    }

    /** Tính góc giữa của một slice (dùng để tính vector explode). */
    private double getSliceMidAngle(PieChart.Data slice) {
        double total =
                statusPieChart.getData().stream().mapToDouble(PieChart.Data::getPieValue).sum();
        if (total <= 0)
            return 0;

        double start = 0;
        for (PieChart.Data data : statusPieChart.getData()) {
            double sweep = (data.getPieValue() / total) * 360.0;
            if (data == slice)
                return start + sweep / 2.0;
            start += sweep;
        }
        return 0;
    }

    // ── Transaction Grid ─────────────────────────────────────────────────

    private void updateTransactionGrid(List<Auction> transactions) {
        // Xóa các row cũ (giữ lại row 0 = header)
        transactionGrid.getChildren().removeIf(
                node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 0);
        transactionGrid.getRowConstraints().clear();
        // Thêm lại header row constraint
        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(22);
        transactionGrid.getRowConstraints().add(headerRow);

        for (int i = 0; i < transactions.size(); i++) {
            Auction auction = transactions.get(i);
            int row = i + 1; // row 0 = header

            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(42);
            transactionGrid.getRowConstraints().add(rc);

            // Col 0: Mã phiên
            addGridCell(transactionGrid,
                    "#" + auction.getId().substring(0, Math.min(8, auction.getId().length())),
                    "-fx-font-weight: bold; -fx-font-size: 11px;", 0, row);

            // Col 1: Tên sản phẩm
            addGridCell(transactionGrid,
                    auction.getItem() != null ? auction.getItem().getTitle() : "—",
                    "-fx-font-size: 11px;", 1, row);

            // Col 2: Người thắng
            String winnerName =
                    (auction.getWinner() != null && auction.getWinner().getFullName() != null)
                            ? auction.getWinner().getFullName()
                            : "—";
            addGridCell(transactionGrid, winnerName, "-fx-font-size: 11px;", 2, row);

            // Col 3: Giá cuối
            addGridCell(transactionGrid, vndFmt.format(auction.getHighestBid()) + " đ",
                    "-fx-font-size: 11px; -fx-font-weight: bold;", 3, row);

            // Col 4: Badge trạng thái
            Label badge = new Label("HOÀN TẤT");
            badge.setStyle("-fx-background-color: rgba(46,204,113,0.16); "
                    + "-fx-background-radius: 9; " + "-fx-text-fill: #2ECC71; "
                    + "-fx-font-size: 8px; " + "-fx-font-weight: bold; " + "-fx-padding: 4 6 4 6;");
            GridPane.setColumnIndex(badge, 4);
            GridPane.setRowIndex(badge, row);
            transactionGrid.getChildren().add(badge);
        }
    }

    private void addGridCell(GridPane grid, String text, String style, int col, int row) {
        Label lbl = new Label(text);
        lbl.setStyle(style);
        lbl.setWrapText(true);
        GridPane.setColumnIndex(lbl, col);
        GridPane.setRowIndex(lbl, row);
        grid.getChildren().add(lbl);
    }

    // ── Hover effects cho KPI Cards ──────────────────────────────────────

    private void setupCardHoverEffects() {
        for (VBox card : List.of(cardUsers, cardLive, cardRevenue)) {
            DropShadow glowShadow = new DropShadow(28, Color.web("#C9A84C", 0.35));

            card.setOnMouseEntered(e -> {
                card.setStyle(card.getStyle().replace("-fx-background-color: #161616",
                        "-fx-background-color: #1E1C1A"));
                card.setEffect(glowShadow);
            });
            card.setOnMouseExited(e -> {
                card.setStyle(card.getStyle().replace("-fx-background-color: #1E1C1A",
                        "-fx-background-color: #161616"));
                card.setEffect(null);
            });
        }
    }

    // ── Styling AreaChart ────────────────────────────────────────────────

    private void styleChart() {
        revenueChart.setStyle("-fx-background-color: transparent;");
        setChartNodeStyle(".chart-series-area-fill", "-fx-fill: linear-gradient(to bottom, "
                + "rgba(201,168,76,0.35), rgba(201,168,76,0.0));");
        setChartNodeStyle(".chart-series-area-line", "-fx-stroke: #C9A84C; -fx-stroke-width: 2px;");
        setChartNodeStyle(".chart-plot-background", "-fx-background-color: transparent;");
        setChartNodeStyle(".chart-vertical-grid-lines", "-fx-stroke: transparent;");
        setChartNodeStyle(".chart-horizontal-grid-lines", "-fx-stroke: rgba(255,255,255,0.06);");
    }

    private void setChartNodeStyle(String selector, String style) {
        Node node = revenueChart.lookup(selector);
        if (node != null)
            node.setStyle(style);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Format VNĐ rút gọn: 2_400_000_000 → "2.4B VNĐ" */
    private String formatShortVnd(long amount) {
        if (amount >= 1_000_000_000)
            return String.format("%.1fB VNĐ", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)
            return String.format("%.1fM VNĐ", amount / 1_000_000.0);
        return vndFmt.format(amount) + " đ";
    }

    public void onClose() {
        if (client != null)
            client.removeListener(this);
    }
}
