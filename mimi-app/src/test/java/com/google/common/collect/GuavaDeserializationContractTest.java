package com.google.common.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.Test;

/** Runs only on the host JVM; no payload is sent to Android or external services. */
public class GuavaDeserializationContractTest {
    // Deliberately bounded: the old library must fail assertions, not exhaust the test JVM.
    private static final int DECLARED_CAPACITY = 4096;

    @Test
    public void compactMapDoesNotTrustTheDeclaredSizeBeforeReadingEntries() throws Exception {
        RecordingMap map = new RecordingMap();
        map.put("key", "value");
        byte[] data = serialize(map);
        RecordingMap.requestedCapacity = -1;
        try (ObjectInputStream input = inflatedSizeInput(data)) {
            assertThrows(IOException.class, input::readObject);
        }
        assertTrue(RecordingMap.requestedCapacity >= 0);
        assertTrue(RecordingMap.requestedCapacity <= 256);
    }

    @Test
    public void compactSetDoesNotTrustTheDeclaredSizeBeforeReadingEntries() throws Exception {
        RecordingSet set = new RecordingSet();
        set.add("value");
        byte[] data = serialize(set);
        RecordingSet.requestedCapacity = -1;
        try (ObjectInputStream input = inflatedSizeInput(data)) {
            assertThrows(IOException.class, input::readObject);
        }
        assertTrue(RecordingSet.requestedCapacity >= 0);
        assertTrue(RecordingSet.requestedCapacity <= 256);
    }

    @Test
    public void mapMakerCapsInitialSegmentAllocation() throws Exception {
        Map<Object, Object> map = new MapMaker().weakKeys().makeMap();
        try (ObjectInputStream input = inflatedSizeInput(serialize(map))) {
            MapMakerInternalMap<?, ?, ?, ?> result =
                    (MapMakerInternalMap<?, ?, ?, ?>) input.readObject();
            assertTrue(result.isEmpty());
            assertTrue(result.segments[0].table.length() <= 64);
        }
    }

    @Test
    public void legitimateCollectionsCanStillGrowAndRoundTrip() throws Exception {
        CompactHashMap<Integer, String> map = CompactHashMap.create();
        CompactHashSet<Integer> set = CompactHashSet.create();
        for (int i = 0; i < 1024; i++) {
            map.put(i, "value-" + i);
            set.add(i);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialize(map)))) {
            assertEquals(map, input.readObject());
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialize(set)))) {
            assertEquals(set, input.readObject());
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static ObjectInputStream inflatedSizeInput(byte[] bytes) throws IOException {
        return new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            @Override public int readInt() throws IOException {
                super.readInt();
                return DECLARED_CAPACITY;
            }
        };
    }

    // Package-local Guava hooks are observed from test-only subclasses, not copied or patched.
    private static class RecordingMap extends CompactHashMap<String, String> {
        private static final long serialVersionUID = 1L;
        static int requestedCapacity;

        @Override void init(int expectedSize) {
            requestedCapacity = expectedSize;
            super.init(expectedSize);
        }
    }

    private static class RecordingSet extends CompactHashSet<String> {
        private static final long serialVersionUID = 1L;
        static int requestedCapacity;

        @Override void init(int expectedSize) {
            requestedCapacity = expectedSize;
            super.init(expectedSize);
        }
    }
}
