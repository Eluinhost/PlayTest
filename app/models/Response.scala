package models

case class Response(available: Option[Boolean], slotId: Long, personId: Long, id: Option[Long] = None)