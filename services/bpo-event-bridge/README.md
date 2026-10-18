# bpo-event-bridge

Planned (`com.bank.bpo.eventbridge`): consumes domain events (ADR-019: one topic per aggregate, keyed
by aggregate id, `eventType` header) through an inbox and correlates them as messages with
`messageId = eventId`, so a redelivered event correlates once. P1 waits for `Msg_KycCompleted`
from `evt.cus.customer.v1` (requested from the customer context). The bridge publishes no money or
personal data and carries only the ids and enums the message definition in
`models/dictionary/messages.yaml` lists.
