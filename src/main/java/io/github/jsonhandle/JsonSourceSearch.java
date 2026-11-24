package io.github.jsonhandle;

import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class JsonSourceSearch {
    private static final Pattern TOKEN = Pattern.compile(
        "\"(?:[^\"\\\\]++|\\\\.)*+\"|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null");

    record Range(int start, int end) {}

    // The source has already been validated. Searching complete tokens prevents matches across JSON punctuation.
    static List<Range> find(String source, String query) {
        var ranges = new ArrayList<Range>();
        var pattern = Pattern.compile(query, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        var tokens = TOKEN.matcher(source);
        var matches = pattern.matcher(source);
        while (tokens.find()) {
            int start = tokens.start();
            int end = tokens.end();
            if (source.charAt(start) == '"') {
                if (source.indexOf('\\', start + 1, end - 1) >= 0) {
                    findEscapedString(source, start, end, pattern, ranges);
                    continue;
                }
                start++;
                end--;
            }
            matches.region(start, end);
            while (matches.find()) {
                ranges.add(new Range(matches.start(), matches.end()));
            }
        }
        return ranges;
    }

    private static void findEscapedString(String source, int start, int end, Pattern pattern, List<Range> ranges) {
        String decoded = JsonParser.parseString(source.substring(start, end)).getAsString();
        var matches = pattern.matcher(decoded);
        if (!matches.find()) {
            return;
        }
        // Map UTF-16 positions in the decoded match back to complete escapes in the original document.
        int[] offsets = new int[decoded.length() + 1];
        int raw = start + 1;
        for (int index = 0; index < decoded.length(); index++) {
            offsets[index] = raw;
            raw += source.charAt(raw) == '\\' ? (source.charAt(raw + 1) == 'u' ? 6 : 2) : 1;
        }
        offsets[decoded.length()] = raw;
        do {
            ranges.add(new Range(offsets[matches.start()], offsets[matches.end()]));
        } while (matches.find());
    }
}
