package it.pagopa.helpdeskcommands.config

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Mixin for QueueEvent to include all fields in JSON serialization. This ensures tracingInfo is
 * always serialized, even when null, to satisfy consumer's strict deserialization requirements.
 */
@JsonInclude(JsonInclude.Include.ALWAYS) abstract class QueueEventMixin
