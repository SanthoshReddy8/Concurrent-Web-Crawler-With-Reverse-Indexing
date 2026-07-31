package com.crawler.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

final class PipelineJson {

    private PipelineJson() {
    }

    static Gson create(boolean prettyPrinting) {
        GsonBuilder builder = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter());
        if (prettyPrinting) {
            builder.setPrettyPrinting();
        }
        return builder.create();
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value == null ? null : value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            String value = in.nextString();
            return Instant.parse(value);
        }
    }
}