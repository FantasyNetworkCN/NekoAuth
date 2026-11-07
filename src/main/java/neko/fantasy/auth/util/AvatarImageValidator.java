package neko.fantasy.auth.util;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 头像图片结构校验。
 *
 * <p>这里不按像素数限制图片，但必须确认整个文件符合对应图片格式、没有截断或尾随脚本，
 * 避免仅伪造文件头就把任意内容写入头像目录。校验过程只解析结构，不解码像素，避免图片本身
 * 触发大规模内存分配。</p>
 */
public final class AvatarImageValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private AvatarImageValidator() {
    }

    public static Format validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return validPng(bytes) ? Format.PNG : null;
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return validJpeg(bytes) ? Format.JPEG : null;
        }
        if (isGifHeader(bytes)) {
            return validGif(bytes) ? Format.GIF : null;
        }
        if (isWebpHeader(bytes)) {
            return validWebp(bytes) ? Format.WEBP : null;
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return validBmp(bytes) ? Format.BMP : null;
        }
        return null;
    }

    private static boolean validPng(byte[] bytes) {
        int position = PNG_SIGNATURE.length;
        boolean firstChunk = true;
        boolean sawData = false;
        while (position < bytes.length) {
            if (position + 12 > bytes.length) {
                return false;
            }
            long length = uint32Be(bytes, position);
            if (length > Integer.MAX_VALUE) {
                return false;
            }
            int dataStart = position + 8;
            int dataEnd = dataStart + (int) length;
            int crcOffset = dataEnd;
            if (dataEnd < dataStart || crcOffset + 4 > bytes.length) {
                return false;
            }
            String type = chunkType(bytes, position + 4);
            if (type == null) {
                return false;
            }

            CRC32 crc = new CRC32();
            crc.update(bytes, position + 4, 4 + (int) length);
            if (crc.getValue() != uint32Be(bytes, crcOffset)) {
                return false;
            }

            if (firstChunk) {
                if (!"IHDR".equals(type) || length != 13 || !validPngHeader(bytes, dataStart)) {
                    return false;
                }
                firstChunk = false;
            } else if ("IHDR".equals(type)) {
                return false;
            } else if ("PLTE".equals(type)) {
                if (length == 0 || length > 768 || length % 3 != 0) {
                    return false;
                }
            } else if ("IDAT".equals(type)) {
                if (length == 0) {
                    return false;
                }
                sawData = true;
            } else if ("IEND".equals(type)) {
                return length == 0 && sawData && crcOffset + 4 == bytes.length;
            } else if (Character.isUpperCase(type.charAt(0))) {
                // 未知关键块按 PNG 规范必须拒绝；辅助块允许扩展。
                return false;
            }
            position = crcOffset + 4;
        }
        return false;
    }

    private static boolean validPngHeader(byte[] bytes, int dataStart) {
        long width = uint32Be(bytes, dataStart);
        long height = uint32Be(bytes, dataStart + 4);
        int bitDepth = bytes[dataStart + 8] & 0xff;
        int colorType = bytes[dataStart + 9] & 0xff;
        int compression = bytes[dataStart + 10] & 0xff;
        int filter = bytes[dataStart + 11] & 0xff;
        int interlace = bytes[dataStart + 12] & 0xff;
        if (width == 0 || height == 0 || compression != 0 || filter != 0 || interlace > 1) {
            return false;
        }
        return switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4
                    || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
    }

    private static boolean validJpeg(byte[] bytes) {
        int position = 2;
        boolean sawFrame = false;
        boolean sawScan = false;
        while (position < bytes.length) {
            if ((bytes[position] & 0xff) != 0xff) {
                return false;
            }
            while (position < bytes.length && (bytes[position] & 0xff) == 0xff) {
                position++;
            }
            if (position >= bytes.length) {
                return false;
            }
            int marker = bytes[position++] & 0xff;
            if (marker == 0x00 || marker == 0xd8) {
                return false;
            }
            if (marker == 0xd9) {
                return sawFrame && sawScan && position == bytes.length;
            }
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                continue;
            }
            if (position + 2 > bytes.length) {
                return false;
            }
            int segmentLength = uint16Be(bytes, position);
            if (segmentLength < 2 || position + segmentLength > bytes.length) {
                return false;
            }
            int dataStart = position + 2;
            if (isJpegStartOfFrame(marker)) {
                if (segmentLength < 8) {
                    return false;
                }
                int precision = bytes[dataStart] & 0xff;
                int height = uint16Be(bytes, dataStart + 1);
                int width = uint16Be(bytes, dataStart + 3);
                int components = bytes[dataStart + 5] & 0xff;
                if (precision == 0 || height == 0 || width == 0 || components == 0
                        || segmentLength < 8 + components * 3) {
                    return false;
                }
                sawFrame = true;
            }
            int segmentEnd = position + segmentLength;
            if (marker == 0xda) {
                sawScan = true;
                int nextMarker = nextJpegMarker(bytes, segmentEnd);
                if (nextMarker < 0) {
                    return false;
                }
                position = nextMarker;
            } else {
                position = segmentEnd;
            }
        }
        return false;
    }

    private static boolean isJpegStartOfFrame(int marker) {
        return (marker >= 0xc0 && marker <= 0xc3)
                || (marker >= 0xc5 && marker <= 0xc7)
                || (marker >= 0xc9 && marker <= 0xcb)
                || (marker >= 0xcd && marker <= 0xcf);
    }

    private static int nextJpegMarker(byte[] bytes, int offset) {
        int position = offset;
        while (position + 1 < bytes.length) {
            if ((bytes[position] & 0xff) != 0xff) {
                position++;
                continue;
            }
            int next = position + 1;
            while (next < bytes.length && (bytes[next] & 0xff) == 0xff) {
                next++;
            }
            if (next >= bytes.length) {
                return -1;
            }
            int code = bytes[next] & 0xff;
            if (code == 0x00 || (code >= 0xd0 && code <= 0xd7)) {
                position = next + 1;
                continue;
            }
            return position;
        }
        return -1;
    }

    private static boolean validGif(byte[] bytes) {
        if (bytes.length < 13) {
            return false;
        }
        int packed = bytes[10] & 0xff;
        int position = 13;
        if ((packed & 0x80) != 0) {
            position += 3 * (1 << ((packed & 0x07) + 1));
        }
        boolean sawImage = false;
        while (position < bytes.length) {
            int block = bytes[position++] & 0xff;
            if (block == 0x3b) {
                return sawImage && position == bytes.length;
            }
            if (block == 0x21) {
                if (position >= bytes.length) {
                    return false;
                }
                position++;
                position = skipGifSubBlocks(bytes, position);
                if (position < 0) {
                    return false;
                }
                continue;
            }
            if (block != 0x2c || position + 9 > bytes.length) {
                return false;
            }
            int width = uint16Le(bytes, position + 4);
            int height = uint16Le(bytes, position + 6);
            int imagePacked = bytes[position + 8] & 0xff;
            if (width == 0 || height == 0) {
                return false;
            }
            position += 9;
            if ((imagePacked & 0x80) != 0) {
                position += 3 * (1 << ((imagePacked & 0x07) + 1));
            }
            if (position >= bytes.length) {
                return false;
            }
            int minimumCodeSize = bytes[position++] & 0xff;
            if (minimumCodeSize < 2 || minimumCodeSize > 8) {
                return false;
            }
            position = skipGifSubBlocks(bytes, position);
            if (position < 0) {
                return false;
            }
            sawImage = true;
        }
        return false;
    }

    private static int skipGifSubBlocks(byte[] bytes, int offset) {
        int position = offset;
        while (position < bytes.length) {
            int size = bytes[position++] & 0xff;
            if (size == 0) {
                return position;
            }
            if (position + size > bytes.length) {
                return -1;
            }
            position += size;
        }
        return -1;
    }

    private static boolean validBmp(byte[] bytes) {
        if (bytes.length < 26) {
            return false;
        }
        long declaredSize = uint32Le(bytes, 2);
        if (declaredSize != 0 && declaredSize != bytes.length) {
            return false;
        }
        long pixelOffset = uint32Le(bytes, 10);
        long dibSize = uint32Le(bytes, 14);
        if (dibSize < 12 || 14 + dibSize > bytes.length
                || pixelOffset < 14 + dibSize || pixelOffset >= bytes.length) {
            return false;
        }
        if (dibSize == 12) {
            return uint16Le(bytes, 18) > 0
                    && uint16Le(bytes, 20) > 0
                    && uint16Le(bytes, 22) == 1
                    && validBmpBpp(uint16Le(bytes, 24));
        }
        if (dibSize < 40) {
            return false;
        }
        int width = int32Le(bytes, 18);
        int height = int32Le(bytes, 22);
        int planes = uint16Le(bytes, 26);
        int bitsPerPixel = uint16Le(bytes, 28);
        long compression = uint32Le(bytes, 30);
        return width != 0 && height != 0 && planes == 1
                && validBmpBpp(bitsPerPixel) && compression <= 6;
    }

    private static boolean validBmpBpp(int bitsPerPixel) {
        return bitsPerPixel == 1 || bitsPerPixel == 4 || bitsPerPixel == 8
                || bitsPerPixel == 16 || bitsPerPixel == 24 || bitsPerPixel == 32;
    }

    private static boolean validWebp(byte[] bytes) {
        if (bytes.length < 20 || uint32Le(bytes, 4) != bytes.length - 8L) {
            return false;
        }
        int position = 12;
        boolean sawImage = false;
        while (position < bytes.length) {
            if (position + 8 > bytes.length) {
                return false;
            }
            String type = ascii(bytes, position, 4);
            long length = uint32Le(bytes, position + 4);
            int dataStart = position + 8;
            int dataEnd = dataStart + (int) length;
            if (length > Integer.MAX_VALUE || dataEnd < dataStart || dataEnd > bytes.length) {
                return false;
            }
            switch (type) {
                case "VP8 " -> {
                    if (!validWebpLossy(bytes, dataStart, dataEnd)) {
                        return false;
                    }
                    sawImage = true;
                }
                case "VP8L" -> {
                    if (!validWebpLossless(bytes, dataStart, dataEnd)) {
                        return false;
                    }
                    sawImage = true;
                }
                case "VP8X" -> {
                    if (length != 10) {
                        return false;
                    }
                }
                case "ANIM" -> {
                    if (length != 6) {
                        return false;
                    }
                }
                case "ANMF" -> {
                    if (!validWebpAnimationFrame(bytes, dataStart, dataEnd)) {
                        return false;
                    }
                    sawImage = true;
                }
                case "ALPH" -> {
                    if (length == 0) {
                        return false;
                    }
                }
                default -> {
                    // ICCP / EXIF / XMP 等辅助块只要边界合法即可。
                }
            }
            position = dataEnd;
            if ((length & 1L) != 0) {
                if (position >= bytes.length || bytes[position] != 0) {
                    return false;
                }
                position++;
            }
        }
        return sawImage && position == bytes.length;
    }

    private static boolean validWebpLossy(byte[] bytes, int start, int end) {
        if (end - start < 10) {
            return false;
        }
        int frameTag = (bytes[start] & 0xff)
                | ((bytes[start + 1] & 0xff) << 8)
                | ((bytes[start + 2] & 0xff) << 16);
        boolean keyFrame = (frameTag & 1) == 0;
        int version = (frameTag >>> 1) & 0x07;
        int firstPartitionSize = frameTag >>> 5;
        if (!keyFrame || version > 3 || firstPartitionSize > end - start - 10
                || (bytes[start + 3] & 0xff) != 0x9d
                || (bytes[start + 4] & 0xff) != 0x01
                || (bytes[start + 5] & 0xff) != 0x2a) {
            return false;
        }
        int width = uint16Le(bytes, start + 6) & 0x3fff;
        int height = uint16Le(bytes, start + 8) & 0x3fff;
        return width > 0 && height > 0;
    }

    private static boolean validWebpLossless(byte[] bytes, int start, int end) {
        if (end - start < 5 || (bytes[start] & 0xff) != 0x2f) {
            return false;
        }
        int b1 = bytes[start + 1] & 0xff;
        int b2 = bytes[start + 2] & 0xff;
        int b3 = bytes[start + 3] & 0xff;
        int b4 = bytes[start + 4] & 0xff;
        int width = 1 + (b1 | ((b2 & 0x3f) << 8));
        int height = 1 + (((b2 & 0xc0) >> 6) | (b3 << 2) | ((b4 & 0x0f) << 10));
        int version = (b4 >> 1) & 0x07;
        return width > 0 && height > 0 && version == 0;
    }

    private static boolean validWebpAnimationFrame(byte[] bytes, int start, int end) {
        if (end - start < 17) {
            return false;
        }
        int width = 1 + uint24Le(bytes, start + 6);
        int height = 1 + uint24Le(bytes, start + 9);
        if (width <= 0 || height <= 0) {
            return false;
        }
        int position = start + 16;
        boolean sawFrameImage = false;
        while (position < end) {
            if (position + 8 > end) {
                return false;
            }
            String type = ascii(bytes, position, 4);
            long length = uint32Le(bytes, position + 4);
            int dataStart = position + 8;
            int dataEnd = dataStart + (int) length;
            if (length > Integer.MAX_VALUE || dataEnd < dataStart || dataEnd > end) {
                return false;
            }
            if ("VP8 ".equals(type)) {
                if (!validWebpLossy(bytes, dataStart, dataEnd)) {
                    return false;
                }
                sawFrameImage = true;
            } else if ("VP8L".equals(type)) {
                if (!validWebpLossless(bytes, dataStart, dataEnd)) {
                    return false;
                }
                sawFrameImage = true;
            } else if ("ALPH".equals(type) && length == 0) {
                return false;
            }
            position = dataEnd;
            if ((length & 1L) != 0) {
                if (position >= end || bytes[position] != 0) {
                    return false;
                }
                position++;
            }
        }
        return sawFrameImage && position == end;
    }

    private static boolean isGifHeader(byte[] bytes) {
        if (bytes.length < 6) {
            return false;
        }
        String signature = ascii(bytes, 0, 6);
        return "GIF87a".equals(signature) || "GIF89a".equals(signature);
    }

    private static boolean isWebpHeader(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String chunkType(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) {
            return null;
        }
        for (int i = offset; i < offset + 4; i++) {
            int value = bytes[i] & 0xff;
            if (!((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z'))) {
                return null;
            }
        }
        return ascii(bytes, offset, 4);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static int uint16Be(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int uint16Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int int32Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | (bytes[offset + 3] << 24);
    }

    private static int uint24Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private static long uint32Be(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24)
                | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xffL);
    }

    private static long uint32Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((long) (bytes[offset + 1] & 0xff) << 8)
                | ((long) (bytes[offset + 2] & 0xff) << 16)
                | ((long) (bytes[offset + 3] & 0xff) << 24);
    }

    public enum Format {
        PNG("png"),
        JPEG("jpg"),
        GIF("gif"),
        WEBP("webp"),
        BMP("bmp");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }
}
