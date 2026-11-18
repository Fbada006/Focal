package io.disruption.focal.core.ios

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.nullable
import kotlin.time.Instant

/** ISO 8601 UTC with whole seconds (`2026-01-05T09:00:00Z`), readable by Swift's ISO8601DateFormatter. */
object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("focal.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(Instant.fromEpochSeconds(value.epochSeconds).toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

object NullableIsoInstantSerializer : KSerializer<Instant?> by IsoInstantSerializer.nullable
