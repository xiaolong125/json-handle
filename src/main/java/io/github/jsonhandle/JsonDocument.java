package io.github.jsonhandle;

import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

final class JsonDocument {
    static final int MAX_INPUT_LENGTH = 512 * 1024;
    private static final int MAX_NESTING = 128;

    enum Kind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    // Ordered entries preserve repeated object names, and scalar tokens preserve numeric precision and spelling.
    static final class Entry {
        final String name;
        final Value value;

        final boolean arrayItem;

        Entry(String name, Value value, boolean arrayItem) {
            this.name = name;
            this.value = value;
            this.arrayItem = arrayItem;
        }

        boolean hasKey() {
            return name != null && !arrayItem;
        }
    }

    record Value(Kind kind, String token, List<Entry> children) {
        String summary() {
            return switch (kind) {
                case OBJECT -> "{" + children.size() + "}";
                case ARRAY -> "[" + children.size() + "]";
                case STRING, NUMBER, BOOLEAN, NULL -> token.length() <= 180 ? token : token.substring(0, 180) + "…";
            };
        }
    }

    static Value parse(String input) throws IOException {
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new IOException("JSON exceeds the 524,288-character limit.");
        }
        try (var reader = new JsonReader(new StringReader(input))) {
            reader.setStrictness(Strictness.STRICT);
            // The formatter and tree handling also traverse nested values, so bound recursive depth at the input.
            reader.setNestingLimit(MAX_NESTING);
            Value value = readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Expected a single JSON value at " + reader.getPath());
            }
            return value;
        }
    }

    private static Value readValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var members = new ArrayList<Entry>();
                while (reader.hasNext()) {
                    members.add(new Entry(reader.nextName(), readValue(reader), false));
                }
                reader.endObject();
                yield new Value(Kind.OBJECT, null, members);
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                var items = new ArrayList<Entry>();
                while (reader.hasNext()) {
                    items.add(new Entry(Integer.toString(items.size()), readValue(reader), true));
                }
                reader.endArray();
                yield new Value(Kind.ARRAY, null, items);
            }
            case STRING -> scalar(Kind.STRING, new JsonPrimitive(reader.nextString()).toString());
            case NUMBER -> scalar(Kind.NUMBER, reader.nextString());
            case BOOLEAN -> scalar(Kind.BOOLEAN, Boolean.toString(reader.nextBoolean()));
            case NULL -> {
                reader.nextNull();
                yield scalar(Kind.NULL, "null");
            }
            default -> throw new IOException("Expected a JSON value at " + reader.getPath());
        };
    }

    private static Value scalar(Kind kind, String token) {
        return new Value(kind, token, List.of());
    }

    static String render(Value value, boolean pretty) {
        var text = new StringWriter();
        try (var writer = new JsonWriter(text)) {
            writer.setStrictness(Strictness.STRICT);
            if (pretty) {
                writer.setIndent("  ");
            }
            writeValue(writer, value);
        } catch (IOException error) {
            throw new IllegalStateException("Could not serialize parsed JSON.", error);
        }
        return text.toString();
    }

    private static void writeValue(JsonWriter writer, Value value) throws IOException {
        switch (value.kind()) {
            case OBJECT -> {
                writer.beginObject();
                for (Entry child : value.children()) {
                    writer.name(child.name);
                    writeValue(writer, child.value);
                }
                writer.endObject();
            }
            case ARRAY -> {
                writer.beginArray();
                for (Entry child : value.children()) {
                    writeValue(writer, child.value);
                }
                writer.endArray();
            }
            case STRING, NUMBER, BOOLEAN, NULL -> writer.jsonValue(value.token());
        }
    }
}
