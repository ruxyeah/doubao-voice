package com.doubao.voice.protocol.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip压缩/解压工具类
 */
public final class GzipUtils {

    private static final int BUFFER_SIZE = 4096;

    private GzipUtils() {
    }

    /**
     * Gzip压缩
     *
     * @param data 原始数据
     * @return 压缩后数据
     * @throws IOException 压缩失败
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        }
    }

    /**
     * Gzip解压
     *
     * @param compressedData 压缩数据
     * @return 解压后数据
     * @throws IOException 解压失败
     */
    public static byte[] decompress(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /**
     * 判断是否为Gzip压缩数据
     *
     * @param data 数据
     * @return 是否为Gzip格式
     */
    public static boolean isGzipped(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        // Gzip魔数：0x1f 0x8b
        return (data[0] == (byte) 0x1f) && (data[1] == (byte) 0x8b);
    }
}
