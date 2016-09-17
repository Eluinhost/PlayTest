package controllers

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

import dao.{Database, PollRepo, SlotRepo}
import exceptions.{IncorrectSecretException, NotAssignedToPollException, PollNotFoundException}
import models.Slot
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SlotsController @Inject() (val slotRepo: SlotRepo, val pollRepo: PollRepo, val db: Database) (implicit exec: ExecutionContext) extends Controller {
  import db.api._

  // Single slot read
  implicit val slotReads : Reads[Slot] = (
    (__ \ "day").read[LocalDate] and
    (__ \ "hour").read[Int] (min(0) <~ max(23)) and
    (__ \ "chooseable").readNullable[Boolean]
  )((day, hour, chooseable) => Slot(
    day = day,
    hour = hour,
    choosable = chooseable.getOrElse(true),
    pollId = null
  ))

  // JSON for writing a slot
  implicit val slotWrites : Writes[Slot] = (
    (__ \ "id").write[Long] and
    (__ \ "day").write[LocalDate] and
    (__ \ "hour").write[Int] and
    (__ \ "chooseable").write[Boolean]
  )(s => (s.id.getOrElse(-1), s.day, s.hour, s.choosable))

  // Get all slots for specific poll
  def get(pollId: UUID) = Action.async {
    db run (for {
      pollExists <- pollRepo.exists(pollId)
      slots <- if (pollExists) slotRepo.findByPollId(pollId) else DBIO.failed(PollNotFoundException())
    } yield slots).transactionally.asTry map {
      case Success(slots) => Ok(Json.toJson(slots))
      case Failure(PollNotFoundException()) => NotFound
      case Failure(ex) =>
        Logger.error("Failed to fetch slots for poll", ex)
        InternalServerError
    }
  }

  case class SlotAlreadyExistsException() extends Exception

  // Add a new slot to a specific poll
  def post(pollId: UUID, secret: UUID) = Action.async(BodyParsers.parse.json) {
    _.body.validate[Slot].fold(
      errors => Future successful BadRequest(Json.obj("error" -> JsError.toJson(errors))),
      jsonSlot => {
        // Parsed without a slot id so add the known one to it
        val slot = jsonSlot.copy(pollId = pollId)

        db run (for {
          correct    <- pollRepo.isCorrectSecret(pollId, secret)
          slotExists <- if (correct) slotRepo.exists(slot)     else DBIO.failed(IncorrectSecretException())
          slot       <- if (!slotExists) slotRepo.insert(slot) else DBIO.failed(SlotAlreadyExistsException())
        } yield slot).transactionally.asTry map {
          case Success(s) => Ok(Json.toJson(s))
          case Failure(IncorrectSecretException()) => Unauthorized
          case Failure(SlotAlreadyExistsException()) => BadRequest(Json.obj("error" -> "Slot already exists"))
          case Failure(ex) =>
            Logger.error("Failed to create a new slot", ex)
            InternalServerError
        }
      }
    )
  }

  def delete(pollId: UUID, secret: UUID, slotId: Long) = Action.async {
    db run (for {
      correct  <- pollRepo.isCorrectSecret(pollId, secret)
      assigned <- if (correct) slotRepo.isAssignedToPoll(slotId, pollId)  else DBIO.failed(IncorrectSecretException())
      deleted  <- if (assigned) slotRepo.deleteById(slotId)               else DBIO.failed(NotAssignedToPollException())
    } yield deleted).transactionally.asTry map {
      case Success(x) if x == 0 => NotFound
      case Success(_) => NoContent
      case Failure(NotAssignedToPollException()) => NotFound
      case Failure(IncorrectSecretException()) => Unauthorized
      case Failure(ex) =>
        Logger.error("Failed to delete a slot", ex)
        InternalServerError
    }
  }
}
