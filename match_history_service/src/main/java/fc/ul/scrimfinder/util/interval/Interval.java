package fc.ul.scrimfinder.util.interval;

public interface Interval<T> {
    T getMin();

    T getMax();

    void setMin(T min);

    void setMax(T max);
}
