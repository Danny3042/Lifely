package platform

// Android-specific actual for ChartPublisher
// On Android the Compose UI updates charts from shared state directly, so this
// implementation is a lightweight bridge that previously logged totals for debugging.
actual object ChartPublisher {
    actual fun publishTotals(totals: List<Float>) {
        // logging removed
    }
}
