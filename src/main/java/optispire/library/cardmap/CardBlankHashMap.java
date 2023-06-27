package optispire.library.cardmap;

import com.megacrit.cardcrawl.cards.AbstractCard;
import optispire.library.AbstractCardBlank;
import optispire.library.MakeCopyChecking;

import java.util.*;
import java.util.function.Consumer;

public class CardBlankHashMap extends HashMap<String, AbstractCard> {
    private HashMap<String, AbstractCardBlank> blanks = new HashMap<>();

    @Override
    public AbstractCard put(String key, AbstractCard c) {
        if (!MakeCopyChecking.optimizableCards.contains(c.getClass().getName()))
        {
            return super.put(key, c);
        }

        blanks.put(key, new AbstractCardBlank(c));

        return c;
    }

    @Override
    public AbstractCard get(Object key) {
        AbstractCardBlank blank = blanks.get(key);
        if (blank != null)
            return blank.makeCopy();
        return super.get(key);
    }

    @Override
    public AbstractCard remove(Object key) {
        AbstractCard c;
        if ((c = super.remove(key)) != null)
            return c;
        AbstractCardBlank blank = blanks.remove(key);
        return blank == null ? null : blank.makeCopy();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && blanks.isEmpty();
    }

    @Override
    public int size() {
        return super.size() + blanks.size();
    }

    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(key) || blanks.containsKey(key);
    }

    @Override
    public void clear() {
        super.clear();
        blanks.clear();
    }

    transient Set<Entry<String, AbstractCard>> chainedSet = null;
    @Override
    public Set<Entry<String, AbstractCard>> entrySet() {
        Set<Entry<String, AbstractCard>> es;
        return (es = chainedSet) == null ? (chainedSet = new ChainedSet()) : es;
    }



    private class ChainedSet extends AbstractSet<Entry<String, AbstractCard>> {
        public final int size()                 { return CardBlankHashMap.this.size(); }
        public final void clear()               { CardBlankHashMap.this.clear(); }
        public final Iterator<Entry<String, AbstractCard>> iterator() {
            return new ChainedIterator<>(CardBlankHashMap.super.entrySet().iterator(),
                    blanks.entrySet().stream().map((entry)-> new TempNode(entry.getKey(), entry.getValue().makeCopy())).iterator());
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            if (!containsKey(key)) return false;
            AbstractCard c = get(key);
            return c.equals(e.getValue());
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                if (!containsKey(key)) return false;
                Object value = e.getValue();
                AbstractCard c = get(key);
                if (c.equals(value))
                    CardBlankHashMap.this.remove(key);
            }
            return false;
        }
        public final Spliterator<Map.Entry<String, AbstractCard>> spliterator() {
            System.out.println("WARNING: Spliterator is not supported by Optispire.");
            return null;
        }
        public final void forEach(Consumer<? super Entry<String, AbstractCard>> action) {
            TempNode temp = new TempNode();
            CardBlankHashMap.this.forEach((id, card)->{
                temp.setKey(id);
                temp.setValue(card);
                action.accept(temp);
            });

            blanks.forEach((id, blank)->{
                temp.setKey(id);
                temp.setValue(blank.makeCopy());
                action.accept(temp);
            });
        }
    }

    private class ChainedIterator<U> implements Iterator<U> {
        final Iterator<? extends U>[] iterators;
        int iteratorIndex;

        @SafeVarargs
        ChainedIterator(Iterator<? extends U>... iterators) {
            this.iterators = iterators;
            iteratorIndex = 0;

            while (iteratorIndex < iterators.length && !iterators[iteratorIndex].hasNext())
                ++iteratorIndex;
        }

        @Override
        public boolean hasNext() {
            return iteratorIndex < iterators.length;
        }

        @Override
        public U next() {
            if (iteratorIndex >= iterators.length)
                throw new NoSuchElementException();
            U next = iterators[iteratorIndex].next();

            while (iteratorIndex < iterators.length && !iterators[iteratorIndex].hasNext())
                ++iteratorIndex;

            return next;
        }

        @Override
        public void remove() {
            Iterator.super.remove();
        }
    }

    private final Map<String, AbstractCard> tempCards = new HashMap<>();
    private class BlankIterator implements Iterator<Map.Entry<String, AbstractCard>> {
        private final Iterator<Map.Entry<String, AbstractCardBlank>> innerIterator;

        public BlankIterator() {
            innerIterator = blanks.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            return innerIterator.hasNext();
        }

        @Override
        public Entry<String, AbstractCard> next() {
            Map.Entry<String, AbstractCardBlank> blank = innerIterator.next();
            return new TempNode(blank.getKey(), blank.getValue().makeCopy());
        }

        @Override
        public void remove() {
            innerIterator.remove();
        }
    }

    private static class TempNode implements Map.Entry<String, AbstractCard> {
        String key;
        AbstractCard value;

        TempNode() {

        }

        public TempNode(String key, AbstractCard c) {
            this.key = key;
            this.value = c;
        }

        @Override
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Override
        public AbstractCard getValue() {
            return value;
        }

        @Override
        public AbstractCard setValue(AbstractCard value) {
            return this.value = value;
        }
    }
}
