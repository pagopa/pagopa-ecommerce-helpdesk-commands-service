package it.pagopa.helpdeskcommands.exceptions

/** Exception thrown when an error occurs communicating with Node Forwarder */
class NodeForwarderClientException
/**
 * Constructor
 *
 * @param reason the error reason
 * @param cause the throwable error cause, if any
 */
(reason: String?, cause: Throwable?) : RuntimeException(reason, cause)
