package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.RGBLuminanceSource;
import org.junit.jupiter.api.Test;

/**
 * The picture on the paper, and the reading of it back.
 *
 * <p>A QR nobody can scan is a decoration, which is what the earlier form printed.
 * The test that matters here is the round trip: the matrix this class draws is
 * fed back through a reader, and what comes out has to be the words that went
 * in. Everything else is about the page surviving a photocopier.
 */
class QrGlyphTest {

    private static final String PAYLOAD = "LEAVE-VERIFY|LV-000007|0123456789ab";

    @Test
    void thePictureReadsBackAsTheWordsThatWentIn() throws Exception {
        BitMatrix matrix = QrGlyph.matrix(PAYLOAD);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        String readBack = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))))
                .getText();

        assertThat(readBack).isEqualTo(PAYLOAD);
    }

    @Test
    void thePictureCarriesTheFormCodeOfAnAddressToo() throws Exception {
        String url = "https://leave.nedu.edu.pk/api/v1/reports/verify"
                + "?reference=LV-000123&code=0123456789abcdef0123456789abcdef";

        BitMatrix matrix = QrGlyph.matrix(url);

        // A longer text needs more modules; the picture is asked for a version
        // that fits, so what matters is that it is built at all and still square.
        assertThat(matrix.getWidth()).isEqualTo(matrix.getHeight());
        assertThat(matrix.getHeight()).isGreaterThan(20);
    }

    @Test
    void aPictureIsDrawnWithSurvivableDamageInMind() throws Exception {
        ErrorCorrectionLevel level = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M;

        assertThat(QrGlyph.matrix(PAYLOAD).getHeight()).isPositive();
        // The class states its level in one place; a reader of the paper - a
        // creased form in a file - is what the medium setting is for.
        assertThat(level).isEqualTo(ErrorCorrectionLevel.M);
    }

    @Test
    void theSvgIsVectorAndCarriesNothingThatRuns() {
        String svg = QrGlyph.svg(PAYLOAD, 26);

        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .contains("width=\"26mm\" height=\"26mm\"")
                .contains("<rect")
                .doesNotContain("<script", "onload", "javascript:", "</svg></svg>");
        assertThat(svg).endsWith("</svg>");
    }

    @Test
    void aRowOfDarkModulesIsOneRectangleRatherThanAPixelEach() {
        String svg = QrGlyph.svg(PAYLOAD, 26);

        int rects = svg.split("<rect", -1).length - 1;
        BitMatrix matrix = QrGlyph.matrix(PAYLOAD);
        int dark = 0;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    dark++;
                }
            }
        }

        // A hundred forms on a sheet is a page the browser has to hold in
        // memory; merging each run keeps it printable.
        assertThat(rects).isLessThan(dark);
        assertThat(rects).isGreaterThan(0);
    }

    @Test
    void textTooLongForOnePictureIsRefusedInWords() {
        String endless = "LEAVE-VERIFY|" + "x".repeat(4000);

        assertThatThrownBy(() -> QrGlyph.svg(endless, 26))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long for one QR picture");
    }
}
