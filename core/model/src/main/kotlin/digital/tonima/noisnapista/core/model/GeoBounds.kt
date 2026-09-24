package digital.tonima.noisnapista.core.model

/** A lat/lon rectangle — the visible map viewport sent to the backend's bounding-box query. */
data class GeoBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double
)
