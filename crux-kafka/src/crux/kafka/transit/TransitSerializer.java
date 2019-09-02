package crux.kafka.transit;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import clojure.java.api.Clojure;
import clojure.lang.AFn;
import clojure.lang.IDeref;
import clojure.lang.IFn;
import org.apache.kafka.common.serialization.Serializer;
import com.fasterxml.jackson.core.JsonGenerator;

public class TransitSerializer implements Serializer<Object> {
    private static final IFn write;
    private static final IFn writer;
    private static final IFn writeHandler;
    private static final IFn ednIdToOriginalId;
    private static final Object jsonVerbose;
    private static final Map<?, ?> options;

    static {
        Clojure.var("clojure.core/require").invoke(Clojure.read("cognitect.transit"));
        write = Clojure.var("cognitect.transit/write");
        writer = Clojure.var("cognitect.transit/writer");
        writeHandler = Clojure.var("cognitect.transit/write-handler");
        Clojure.var("clojure.core/require").invoke(Clojure.read("crux.codec"));
        ednIdToOriginalId = (IFn) ((IDeref) Clojure.var("crux.codec/edn-id->original-id")).deref();
        jsonVerbose = Clojure.read(":json-verbose");
        options = new HashMap<Object, Object>() {
                {
                    put(Clojure.read(":handlers"),
                        new HashMap<Object, Object>() {
                            {
                                put(Clojure.var("clojure.core/resolve").invoke(Clojure.read("crux.codec.EDNId")),
                                    writeHandler.invoke("crux/id", ednIdToOriginalId));
                            }
                        });
                }};
    }

    public void close() {
    }

    public void configure(Map<String,?> configs, boolean isKey) {
    }

    public byte[] serialize(String topic, Object data) {
        if (data == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            write.invoke(writer.invoke(out, jsonVerbose, options), data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}