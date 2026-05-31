package tech.nabor.ui.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;


public final class QRCodeUtil {

    private QRCodeUtil() {
    }

    public static Image generate(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            WritableImage image = new WritableImage(size, size);
            PixelWriter pixels = image.getPixelWriter();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels.setColor(x, y, matrix.get(x, y) ? Color.web("#0F2A5E") : Color.WHITE);
                }
            }
            return image;
        } catch (WriterException e) {
            throw new IllegalStateException("Échec de génération du QR code", e);
        }
    }
}
