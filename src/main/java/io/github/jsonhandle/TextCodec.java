package io.github.jsonhandle;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum TextCodec {
    UNICODE("Unicode"),
    BASE64("Base64"),
    URL_COMPONENT("URL component");

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private static final Pattern PERCENT_BYTES = Pattern.compile("(?:%[0-9a-fA-F]{2})+|%");
    private static final HexFormat PERCENT_HEX = HexFormat.ofDelimiter("%");

    private final String label;

    TextCodec(String label) {
        this.label = label;
    }

    String encode(String input) throws CharacterCodingException {
        return switch (this) {
            case UNICODE -> {
                var output = new StringBuilder();
                for (int index = 0; index < input.length(); index++) {
                    output.append("\\u").append(HexFormat.of().toHexDigits(input.charAt(index)));
                }
                yield output.toString();
            }
            case BASE64 -> {
                ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(input));
                byte[] utf8 = new byte[bytes.remaining()];
                bytes.get(utf8);
                yield Base64.getEncoder().encodeToString(utf8);
            }
            case URL_COMPONENT -> {
                StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(input));
                yield URLEncoder.encode(input, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("%7E", "~")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")");
            }
        };
    }

    String decode(String input) throws CharacterCodingException {
        return switch (this) {
            case UNICODE -> UNICODE_ESCAPE.matcher(input).replaceAll(match ->
                Matcher.quoteReplacement(Character.toString((char) Integer.parseInt(match.group(1), 16))));
            case BASE64 -> StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(Base64.getDecoder().decode(input.replaceAll("[\\r\\n\\t ]", ""))))
                .toString();
            case URL_COMPONENT -> {
                var matches = PERCENT_BYTES.matcher(input);
                var output = new StringBuilder();
                var decoder = StandardCharsets.UTF_8.newDecoder();
                while (matches.find()) {
                    String escapes = matches.group();
                    if (escapes.length() == 1) {
                        throw new IllegalArgumentException("Invalid percent escape at character " + (matches.start() + 1) + ".");
                    }
                    byte[] bytes = PERCENT_HEX.parseHex(escapes, 1, escapes.length());
                    matches.appendReplacement(output, Matcher.quoteReplacement(decoder.decode(ByteBuffer.wrap(bytes)).toString()));
                }
                yield matches.appendTail(output).toString();
            }
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
