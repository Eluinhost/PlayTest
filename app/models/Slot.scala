package models

import java.time.LocalDate
import java.util.UUID

case class Slot(day: LocalDate, hour: Int, choosable: Boolean, pollId: UUID, id: Option[Long] = None)