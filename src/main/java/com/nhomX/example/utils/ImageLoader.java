package com.nhomX.example.utils;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.manager.SessionManager;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ImageLoader {
    private static final Logger logger = LoggerFactory.getLogger(ImageLoader.class);
    private static final String PLACEHOLDER = "/com/nhomX/example/images/default_item.png";

    /**
     * Load ảnh bất đồng bộ từ Server vào ImageView. Hiển thị placeholder trong lúc chờ.
     *
     * @param fileName Tên file ảnh (VD: "item_abc123_0.jpg")
     * @param imageView ImageView sẽ hiển thị ảnh
     */
    public static void loadAsync(String fileName, ImageView imageView) {
        if (fileName == null || fileName.isBlank()) {
            setPlaceholder(imageView);
            return;
        }

        // Hiển thị placeholder ngay lập tức trong lúc chờ mạng
        setPlaceholder(imageView);

        var client = SessionManager.getInstance().getAuctionClient();
        if (client == null)
            return;
        client.requestImage(fileName, imageBytes -> {
            // Callback này đã được AuctionClient đảm bảo chạy trên UI thread
            if (imageBytes != null) {
                Image img = new Image(new ByteArrayInputStream(imageBytes));
                imageView.setImage(img);
            }
        });
    }

    private static void setPlaceholder(ImageView imageView) {
        try {
            var stream = ImageLoader.class.getResourceAsStream(PLACEHOLDER);
            if (stream == null) {
                // [REFACTOR] Log cảnh báo thay vì im lặng — dễ debug khi thiếu file resource
                logger.warn("IMAGE LOADER: Không tìm thấy placeholder tại: {}", PLACEHOLDER);
                return;
            }
            imageView.setImage(new Image(stream));
        } catch (Exception e) {
            logger.error("IMAGE LOADER: Không thể load placeholder", e);
        }
    }
}
