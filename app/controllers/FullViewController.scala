package controllers

import java.util.UUID
import javax.inject.Inject

import dao.{Database, PersonRepo, PollRepo, SlotRepo}
import exceptions.PollNotFoundException
import models.{Person, Poll, Slot}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class FullViewController @Inject()
  (val personRepo: PersonRepo, val pollRepo: PollRepo, val slotRepo: SlotRepo, val db: Database,
   val pollsController: PollsController, val peopleController: PeopleController, val slotsController: SlotsController)
  (implicit exec: ExecutionContext)
  extends Controller {
  import db.api._

  case class FullContext(poll: Poll, people: Seq[Person], slots: Seq[Slot])

  implicit val pollWrites = pollsController.pollWrites
  implicit val personWrites = peopleController.personWrites
  implicit val slotWrites = slotsController.slotWrites

  implicit val writes = Writes[FullContext](c => {
    val peopleById = c.people.map(p => p.id.get -> p).toMap
    val slotsById = c.slots.map(s => s.id.get -> s).toMap

    val peopleIds = JsArray(peopleById.keys.map(id => JsNumber(id)).toSeq)
    val slotIds = JsArray(slotsById.keys.map(id => JsNumber(id)).toSeq)

    val pollJson = Json.toJson(c.poll).as[JsObject] + ("people" -> peopleIds) + ("slots" -> slotIds)

    val peopleByIdJson = peopleById map { (id, person) =>  (JsString(id.toString), Json.toJson(person)) }

    Json.obj(
      "poll" -> pollJson,
      "peopleById" -> Json.toJson(peopleById),
      "slotsById" -> Json.toJson(slotsById)
    )
  })

  def get(id: UUID) = Action async {
    db run (for {
      pollOption <- pollRepo.findById(id)
      poll       <- if (pollOption.isDefined) DBIO.successful(pollOption.get) else DBIO.failed(PollNotFoundException())
      people     <- personRepo.findByPollId(id)
      slots      <- slotRepo.findByPollId(id)
    } yield FullContext(poll, people, slots)).transactionally.asTry map {
      case Failure(PollNotFoundException()) => NotFound
      case Failure(ex) =>
        Logger.error("Failed to fetch poll data", ex)
        InternalServerError
      case Success(c) => Ok(Json.toJson(c))
    }
  }
}
