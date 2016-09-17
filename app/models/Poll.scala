package models

import java.time.OffsetDateTime
import java.util.UUID

case class Poll(name: String, opens: OffsetDateTime, closes: OffsetDateTime, secret: UUID, id: Option[UUID] = None)
