package controllers

import java.util.UUID
import javax.inject.Inject

import dao.{Database, PersonRepo, PollRepo}
import exceptions.PollNotFoundException
import models.Person
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class PeopleController @Inject()(val personRepo: PersonRepo, val pollRepo: PollRepo, val db: Database)(implicit exec: ExecutionContext) extends Controller {
  import db.api._

  // Read a person name from a simple string value
  val nameReads = Reads[String](js => js.validate[String] (minLength[String](3) <~ maxLength[String](128)))

  // Write a person as JSON
  implicit val personWrites : Writes[Person] = (
    (__ \ "id").write[Long] and
    (__ \ "name").write[String]
  )(p => (p.id.getOrElse(-1), p.name))

  case class NameAlreadyExistsException() extends Exception

  // Show people for a specific poll
  def get(pollId: UUID) = Action async {
    db run (for {
      pollExists <- pollRepo.exists(pollId)
      people <- if (pollExists) personRepo.findByPollId(pollId) else DBIO.failed(PollNotFoundException())
    } yield people).transactionally.asTry map {
      case Success(people) => Ok(Json.toJson(people))
      case Failure(PollNotFoundException()) => NotFound
      case Failure(ex) =>
        Logger.error("Failed to get lsit of people", ex)
        InternalServerError
    }
  }

  // Add a new person to a specific poll
  def post(pollId: UUID) = Action.async(BodyParsers.parse.json) {
    _.body.validate[String](nameReads).fold(
      errors => Future successful BadRequest(Json.obj("error" -> JsError.toJson(errors))),
      name => db run (for {
        pollExists <- pollRepo.exists(pollId)
        nameExists <- if (pollExists) personRepo.exists(pollId, name) else DBIO.failed(PollNotFoundException())
        person     <- if (!nameExists) personRepo.insert(Person(pollId = pollId, name = name)) else DBIO.failed(NameAlreadyExistsException())
      } yield person).transactionally.asTry map {
        case Success(person) => Ok(Json.toJson(person))
        case Failure(PollNotFoundException()) => NotFound
        case Failure(NameAlreadyExistsException()) => BadRequest(Json.obj("error" -> "Name already exists"))
        case Failure(ex) =>
          Logger.error("Failed to create a new person", ex)
          InternalServerError
      }
    )
  }

  // Delete a specific person from a specific poll
  def delete(pollId: UUID, id: Long) = Action.async {
    db run personRepo.deleteById(pollId, id) map {
      case x if x > 0 => NoContent
      case _ => NotFound
    }
  }
}
