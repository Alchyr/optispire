/*package optispire.util;

import javassist.CtMethod;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;

public class StupidDumbCtMethodHashMap<U> implements Map<CtMethod, U> {
    private HashMap<StinkyGrossWrapper, U> innerMap = new HashMap<>();
    private final StinkyGrossWrapper tempWrapper = new StinkyGrossWrapper(null);

    @Override
    public int size() {
        return innerMap.size();
    }

    @Override
    public boolean isEmpty() {
        return innerMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof CtMethod) {
            tempWrapper.method = (CtMethod) key;
            return innerMap.containsKey(tempWrapper);
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return innerMap.containsValue(value);
    }

    @Override
    public U get(Object key) {
        if (key instanceof CtMethod) {
            tempWrapper.method = (CtMethod) key;
            return innerMap.get(tempWrapper);
        }
        return null;
    }

    @Override
    public U put(CtMethod key, U value) {
        return innerMap.put(new StinkyGrossWrapper(key), value);
    }

    @Override
    public U remove(Object key) {
        if (key instanceof CtMethod) {
            tempWrapper.method = (CtMethod) key;
            return innerMap.remove(tempWrapper);
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends CtMethod, ? extends U> m) {
        for (Map.Entry<? extends CtMethod, ? extends U> e : m.entrySet()) {
            CtMethod key = e.getKey();
            U value = e.getValue();
            put(key, value);
        }
    }

    @Override
    public void clear() {
        innerMap.clear();
    }

    @Override
    public Set<CtMethod> keySet() {
        throw new NotImplementedException("no keySet");
    }

    @Override
    public Collection<U> values() {
        return innerMap.values();
    }

    @Override
    public Set<Entry<CtMethod, U>> entrySet() {
        throw new NotImplementedException("no entrySet");
    }

    private static class StinkyGrossWrapper {
        CtMethod method;
        public StinkyGrossWrapper(CtMethod method) {
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StupidDumbCtMethodHashMap.StinkyGrossWrapper)) return false;
            StinkyGrossWrapper that = (StinkyGrossWrapper) o;
            return method == that.method;
        }

        @Override
        public int hashCode() {
            return Objects.hash(method);
        }
    }
}
*/