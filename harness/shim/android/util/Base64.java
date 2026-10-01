package android.util;

/** JVM 联调用的极简 shim：把 Android 的 flags 映射到 java.util.Base64。 */
public final class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() { }

    private static java.util.Base64.Encoder encoder(int flags) {
        java.util.Base64.Encoder e = ((flags & URL_SAFE) != 0)
                ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) e = e.withoutPadding();
        return e;
    }

    private static java.util.Base64.Decoder decoder(int flags) {
        return ((flags & URL_SAFE) != 0)
                ? java.util.Base64.getUrlDecoder()
                : java.util.Base64.getMimeDecoder();
    }

    public static String encodeToString(byte[] input, int flags) {
        return encoder(flags).encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        String s = str;
        if ((flags & NO_WRAP) != 0) s = s.replace("\n", "").replace("\r", "");
        return decoder(flags).decode(s);
    }
}
