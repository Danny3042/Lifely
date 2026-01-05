package platform

// JVM implementation used for JVM targets (tests or desktop runners). Provide a no-op
// implementation that previously logged values for debugging.
actual object ChartPublisher {
    actual fun publishTotals(totals: List<Float>) {
        // logging removed
    }
}
