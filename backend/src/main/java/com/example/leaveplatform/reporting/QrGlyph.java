package com.example.leaveplatform.reporting;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/**
 * The QR picture, drawn as SVG.
 *
 * <p>zxing is asked for a matrix of black and white modules and this class
 * writes what it is given, one rectangle per run of dark modules in a row, so a
 * form with a hundred of them stays a few kilobytes rather than a megabyte of
 * inline bitmap. SVG was chosen over a PNG for two reasons: the printed page is
 * already HTML, and a vector picture keeps its edges crisp at every printer
 * resolution, which matters because this picture has to be read from paper
 * years later.
 *
 * <p>Only the matrix is used, never the decoder, so the server holds no image
 * library and needs no graphics environment.
 */
public final class QrGlyph {

    /**
     * The picture's width in modules of the matrix zxing returns. It sets how
     * many modules fit, not the size on the page: the page says that.
     */
    private static final int PIXELS = 221;

    /**
     * Medium error correction. The form is photocopied, folded and filed, and
     * level L survives only about seven per cent of the picture being lost; M
     * survives about fifteen, which is what a crease and a office scanner cost.
     */
    private static final ErrorCorrectionLevel LEVEL = ErrorCorrectionLevel.M;

    private QrGlyph() {
    }

    /**
     * A square picture of {@code size} millimetres on the page, holding
     * {@code payload} ready for the browser to print.
     *
     * <p>The label it carries is the printed form's, because that is what this
     * was built for; see the three-argument form for a picture that means
     * something else.
     */
    public static String svg(String payload, int sizeInMillimetres) {
        return svg(payload, sizeInMillimetres, "Verification code");
    }

    /**
     * The same picture, saying what it is on the way. What the two uses of this
     * class draw are not the same thing - one is a code that checks a sheet of
     * paper, the other is a seed an app has to take - and a person who cannot
     * see the picture learns about it from this sentence, so it must be true.
     */
    public static String svg(String payload, int sizeInMillimetres, String label) {
        BitMatrix matrix = matrix(payload);
        StringBuilder svg = new StringBuilder(2048);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(matrix.getWidth()).append(' ').append(matrix.getHeight())
                .append("\" width=\"").append(sizeInMillimetres).append("mm\" height=\"")
                .append(sizeInMillimetres).append("mm\" role=\"img\"")
                .append(" aria-label=\"").append(escaped(label)).append("\" preserveAspectRatio=\"none\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
        appendDarkModules(svg, matrix);
        return svg.append("</svg>").toString();
    }

    /** The label is written into an attribute, so the attribute's own characters go first. */
    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    /** The matrix behind the picture, exposed for the test that reads it back. */
    static BitMatrix matrix(String payload) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, LEVEL);
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, PIXELS, PIXELS, hints);
        } catch (WriterException tooMuchForOnePicture) {
            throw new IllegalArgumentException(
                    "The verification text is too long for one QR picture.", tooMuchForOnePicture);
        }
    }

    /**
     * One rectangle per unbroken run of dark modules in a row. A QR of this size
     * has a few hundred runs instead of a few thousand single pixels, which is
     * the difference between a printable page and one the browser gives up on.
     */
    private static void appendDarkModules(StringBuilder svg, BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        for (int y = 0; y < height; y++) {
            int run = -1;
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    if (run < 0) {
                        run = x;
                    }
                } else if (run >= 0) {
                    rect(svg, run, y, x - run);
                    run = -1;
                }
            }
            if (run >= 0) {
                rect(svg, run, y, width - run);
            }
        }
    }

    private static void rect(StringBuilder svg, int x, int y, int span) {
        svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(span).append("\" height=\"1\" fill=\"#000\"/>");
    }
}
