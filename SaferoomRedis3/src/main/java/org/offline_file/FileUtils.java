package org.offline_file;

public class FileUtils {

    public static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0, j = 0; i < len; i += 2, j++) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("non-hex char");
            out[j] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static String buildPathForHash(String hashHex) {
        String p1 = hashHex.substring(0, 2);
        String p2 = hashHex.substring(2, 4);
        return "/var/saferoom/files/" + p1 + "/" + p2 + "/" + hashHex + ".enc";
    }
}

