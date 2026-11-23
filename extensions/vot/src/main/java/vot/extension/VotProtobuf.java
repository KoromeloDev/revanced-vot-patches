package vot.extension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Минимальный кодер/декодер protobuf для вызовов API Yandex VOT
 * (формат совпадает с @vot.js/core, версия 3.2.x).
 */
final class VotProtobuf {
    private VotProtobuf() {
    }

    // ---- writer ----

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private void varint(long value) {
            while ((value & ~0x7FL) != 0) {
                out.write((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            out.write((int) value);
        }

        private void tag(int field, int wire) {
            varint(((long) field << 3) | wire);
        }

        Writer string(int field, String value) {
            if (value == null || value.isEmpty()) return this;
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            tag(field, 2);
            varint(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        Writer bool(int field, boolean value) {
            if (!value) return this;
            tag(field, 0);
            varint(1);
            return this;
        }

        Writer int32(int field, int value) {
            if (value == 0) return this;
            tag(field, 0);
            varint(value & 0xFFFFFFFFL);
            return this;
        }

        Writer doubleField(int field, double value) {
            if (value == 0) return this;
            tag(field, 1);
            long bits = Double.doubleToLongBits(value);
            for (int i = 0; i < 8; i++) {
                out.write((int) (bits >>> (8 * i)) & 0xFF);
            }
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    // ---- reader ----

    /** Поля: номер -> значение (Long для varint, byte[] для length-delimited). */
    static Map<Integer, Object> parse(byte[] data) throws Exception {
        Map<Integer, Object> fields = new HashMap<>();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        while (in.available() > 0) {
            long key = readVarint(in);
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            switch (wire) {
                case 0:
                    fields.put(field, readVarint(in));
                    break;
                case 1: {
                    byte[] raw = new byte[8];
                    in.read(raw);
                    long bits = 0;
                    for (int i = 0; i < 8; i++) bits |= ((long) (raw[i] & 0xFF)) << (8 * i);
                    fields.put(field, Double.longBitsToDouble(bits));
                    break;
                }
                case 2: {
                    int len = (int) readVarint(in);
                    byte[] raw = new byte[len];
                    int read = in.read(raw);
                    if (read != len) throw new IllegalStateException("protobuf: короткое поле");
                    fields.put(field, raw);
                    break;
                }
                case 5: {
                    byte[] raw = new byte[4];
                    in.read(raw);
                    fields.put(field, 0L);
                    break;
                }
                default:
                    throw new IllegalStateException("protobuf: wire " + wire);
            }
        }
        return fields;
    }

    private static long readVarint(ByteArrayInputStream in) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = in.read();
            if (b < 0) throw new IllegalStateException("protobuf: конец данных");
            result |= ((long) (b & 0x7F)) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    static String asString(Map<Integer, Object> fields, int field) {
        Object value = fields.get(field);
        return value instanceof byte[] ? new String((byte[]) value, StandardCharsets.UTF_8) : null;
    }

    static long asLong(Map<Integer, Object> fields, int field) {
        Object value = fields.get(field);
        return value instanceof Long ? (Long) value : -1;
    }
}
