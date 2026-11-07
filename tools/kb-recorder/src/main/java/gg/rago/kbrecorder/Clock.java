package gg.rago.kbrecorder;

/** Time source of the recorder, replaced in tests. */
interface Clock {

    /** Monotonic time in nanoseconds, to measure intervals between lines. */
    long nanoTime();

    /** Wall-clock time in milliseconds, to line up recordings made on two clients. */
    long currentTimeMillis();

    Clock SYSTEM = new Clock() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };
}
