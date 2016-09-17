package dao

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import javax.inject.Inject

import models.{Person, Poll, Response, Slot}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import util.CustomPostgresDriver

import scala.concurrent.Future

class Database @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[CustomPostgresDriver] {
  val api = driver.api
  import api._

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)

  lazy val polls = TableQuery[Polls]
  lazy val people = TableQuery[People]
  lazy val slots = TableQuery[Slots]
  lazy val responses = TableQuery[Responses]

  class Polls(tag: Tag) extends Table[Poll](tag, "polls") {
    def id = column[UUID]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.Length(128))
    def opened = column[OffsetDateTime]("opened")
    def closes = column[OffsetDateTime]("closes")
    def secret = column[UUID]("secret")

    def * = (name, opened, closes, secret, id.?) <> (Poll.tupled, Poll.unapply)
  }

  class People(tag: Tag) extends Table[Person](tag, "people") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.Length(128))
    def pollId = column[UUID]("poll_id")

    def * = (name, pollId, id.?) <> (Person.tupled, Person.unapply)

    def poll = foreignKey("poll_fk", pollId, polls)(_.id)
    def idx = index("person_idx", (pollId, name), unique = true)
  }

  class Slots(tag: Tag) extends Table[Slot](tag, "slots") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def day = column[LocalDate]("day")
    def hour = column[Int]("hour")
    def choosable = column[Boolean]("choosable")
    def pollId = column[UUID]("poll_id")

    def * = (day, hour, choosable, pollId, id.?) <> (Slot.tupled, Slot.unapply)

    def poll = foreignKey("poll_fk", pollId, polls)(_.id)
    def idx = index("slot_idx", (pollId, day, hour), unique = true)
  }

  class Responses(tag: Tag) extends Table[Response](tag, "responses") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def available = column[Option[Boolean]]("available")
    def slotId = column[Long]("slot_id")
    def personId = column[Long]("person_id")

    def * = (available, slotId, personId, id.?) <> (Response.tupled, Response.unapply)

    def slot = foreignKey("slot_fk", slotId, slots)(_.id)
    def person = foreignKey("person_fk", personId, people)(_.id)
    def idx = index("response_idx", (slotId, personId), unique = true)
  }
}
