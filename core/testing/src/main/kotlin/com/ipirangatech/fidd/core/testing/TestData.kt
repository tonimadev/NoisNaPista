package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole

/** São Paulo, Sé — the default coordinates for every test fixture. */
fun testLocation(
    latitude: Double = -23.5505,
    longitude: Double = -46.6333,
    accuracy: Float = 5f,
    timestamp: Long = 1_700_000_000_000L,
    speed: Float? = null,
) = LocationPoint(latitude, longitude, accuracy, timestamp, speed)

fun testPothole(
    id: String = "pothole-1",
    severity: Float = 18f,
    timestamp: Long = 1_700_000_000_000L,
    isFalseAlarm: Boolean = false,
    serverId: String? = null,
    status: String? = null,
    distinctReporterCount: Int = 1,
    sessionId: String? = null,
    location: LocationPoint = testLocation(timestamp = timestamp),
) = Pothole(
    id = id,
    location = location,
    severity = severity,
    timestamp = timestamp,
    isFalseAlarm = isFalseAlarm,
    serverId = serverId,
    status = status,
    distinctReporterCount = distinctReporterCount,
    sessionId = sessionId,
)

fun testCityRanking(
    ibgeCode: Int = 3550308,
    name: String = "São Paulo",
    state: String = "SP",
    totalPotholes: Long = 10,
    fixedPotholes: Long = 2,
    recurrenceCount: Long = 1,
    rank: Int? = 1,
) = CityRanking(ibgeCode, name, state, totalPotholes, fixedPotholes, recurrenceCount, rank)
