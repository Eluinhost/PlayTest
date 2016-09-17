package models

import java.util.UUID

case class Person(name: String, pollId: UUID, id: Option[Long] = None)